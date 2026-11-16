import type { CustomLayerInterface, CustomRenderMethodInput, Map as MapLibreMap } from 'maplibre-gl'
import type { Quake } from '../../types'
import { QUAKE_GLSL, buildQuakePatches } from './quakeShader'

/**
 * 지도(MapLibre 라스) 위의 지진: 진앙의 점과 지표면을 따라 퍼지는 원(quakeShader.ts).
 *
 * 홈 라스와 같은 셰이더 함수를 쓰려고 MapLibre 원 레이어(circle)가 아니라 커스텀 레이어로 그린다. 원 레이어는 반지름이
 * 화면 픽셀이라 지구 곡면을 따라 휘지 않고, 확대해도 실제 거리만큼 커지지 않는다.
 * 누르면 기상청 통보문을 보여 주는 일은 MapGlobe가 보이지 않는 원 레이어로 따로 맡는다(클릭 판정은 그쪽이 쉽다).
 */

// 원이 퍼지는 동안은 계속 다시 그린다. 초당 30번이면 충분히 부드럽고, 60번의 절반만 GPU를 쓴다.
const FRAME_MS = 1000 / 30

const VERTEX = (prelude: string, define: string) => `#version 300 es
${prelude}
${define}
in vec2 a_pos;
in vec2 a_lnglat;
in vec2 a_center;
in vec3 a_params;
out vec2 v_lnglat;
out vec2 v_center;
out vec3 v_params;
void main() {
  v_lnglat = a_lnglat;
  v_center = a_center;
  v_params = a_params;
  gl_Position = projectTile(a_pos);
}`

// mapDaylightLayer와 같은 위경도 → 단위벡터 규칙. 진앙과 조각을 같은 규칙으로 바꿔야 거리가 맞다.
const FRAGMENT = `#version 300 es
precision highp float;
uniform float u_time;
in vec2 v_lnglat;
in vec2 v_center;
in vec3 v_params;
out vec4 fragColor;

vec3 unit(vec2 lnglat) {
  float lng = radians(lnglat.x);
  float lat = radians(lnglat.y);
  return vec3(cos(lat) * cos(lng), sin(lat), cos(lat) * sin(lng));
}
${QUAKE_GLSL}
void main() {
  float d = length(unit(v_lnglat) - unit(v_center));
  vec3 light = quakeLight(d, fwidth(d), v_params.x, v_params.y, v_params.z, u_time);
  if (max(light.r, max(light.g, light.b)) < 0.003) discard;
  // 미리 곱한 알파로 a = 0: 아래 지도를 덮지 않고 빛만 더한다.
  fragColor = vec4(min(light, vec3(1.0)), 0.0);
}`

const FLOATS_PER_VERTEX = 9

function compile(gl: WebGL2RenderingContext, type: number, source: string): WebGLShader {
  const shader = gl.createShader(type)!
  gl.shaderSource(shader, source)
  gl.compileShader(shader)
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    throw new Error(`quake shader: ${gl.getShaderInfoLog(shader)}`)
  }
  return shader
}

function mercatorY(lat: number): number {
  return 0.5 - Math.log(Math.tan(Math.PI / 4 + (lat * Math.PI) / 360)) / (2 * Math.PI)
}

export interface QuakeLayer extends CustomLayerInterface {
  setQuakes(quakes: Quake[]): void
}

export function createQuakeLayer(id: string): QuakeLayer {
  let map: MapLibreMap | null = null
  let gl: WebGL2RenderingContext | null = null
  const programs = new Map<string, { program: WebGLProgram; uniforms: Record<string, WebGLUniformLocation | null> }>()
  let vertexBuffer: WebGLBuffer | null = null
  let indexBuffer: WebGLBuffer | null = null
  let indexCount = 0
  let quakes: Quake[] = []
  let uploadedAt = -1
  let repaintTimer: number | undefined
  const startedAt = performance.now()

  const programFor = (shaderData: CustomRenderMethodInput['shaderData']) => {
    const cached = programs.get(shaderData.variantName)
    if (cached) return cached
    const context = gl!
    const program = context.createProgram()!
    context.attachShader(program, compile(context, context.VERTEX_SHADER, VERTEX(shaderData.vertexShaderPrelude, shaderData.define)))
    context.attachShader(program, compile(context, context.FRAGMENT_SHADER, FRAGMENT))
    context.linkProgram(program)
    if (!context.getProgramParameter(program, context.LINK_STATUS)) {
      throw new Error(`quake program: ${context.getProgramInfoLog(program)}`)
    }
    const names = [
      'u_projection_matrix',
      'u_projection_fallback_matrix',
      'u_projection_tile_mercator_coords',
      'u_projection_clipping_plane',
      'u_projection_transition',
      'u_time',
    ]
    const uniforms = Object.fromEntries(names.map((name) => [name, context.getUniformLocation(program, name)]))
    const entry = { program, uniforms }
    programs.set(shaderData.variantName, entry)
    return entry
  }

  /** 지진 목록이 바뀌었거나, 지난 시간(밝기에 쓰는 경과 시간)을 1분마다 새로 반영해야 할 때 격자를 다시 올린다. */
  const upload = (now: number) => {
    if (!gl) return
    const patches = buildQuakePatches(quakes, now)
    const data = new Float32Array(patches.vertices.length * FLOATS_PER_VERTEX)
    patches.vertices.forEach((v, i) => {
      data.set(
        [(v.lng + 180) / 360, mercatorY(v.lat), v.lng, v.lat, v.centerLng, v.centerLat, v.reach, v.ageHours, v.phase],
        i * FLOATS_PER_VERTEX,
      )
    })
    gl.bindBuffer(gl.ARRAY_BUFFER, vertexBuffer)
    gl.bufferData(gl.ARRAY_BUFFER, data, gl.DYNAMIC_DRAW)
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indexBuffer)
    gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, patches.indices, gl.DYNAMIC_DRAW)
    indexCount = patches.indices.length
    uploadedAt = now
  }

  return {
    id,
    type: 'custom',
    renderingMode: '2d',
    setQuakes(next) {
      quakes = next
      uploadedAt = -1
      map?.triggerRepaint()
    },
    onAdd(m, context) {
      map = m
      gl = context as WebGL2RenderingContext
      vertexBuffer = gl.createBuffer()
      indexBuffer = gl.createBuffer()
    },
    onRemove(_m, context) {
      window.clearTimeout(repaintTimer)
      const c = context as WebGL2RenderingContext
      for (const { program } of programs.values()) c.deleteProgram(program)
      programs.clear()
      c.deleteBuffer(vertexBuffer)
      c.deleteBuffer(indexBuffer)
      map = null
      gl = null
    },
    render(context, options) {
      if (!map || quakes.length === 0) return
      const now = Date.now()
      if (uploadedAt < 0 || now - uploadedAt > 60_000) upload(now)
      if (indexCount === 0) return
      const c = context as WebGL2RenderingContext
      const { program, uniforms } = programFor(options.shaderData)
      const projection = options.defaultProjectionData
      c.useProgram(program)
      c.uniformMatrix4fv(uniforms.u_projection_matrix, false, projection.mainMatrix)
      c.uniformMatrix4fv(uniforms.u_projection_fallback_matrix, false, projection.fallbackMatrix)
      c.uniform4f(uniforms.u_projection_tile_mercator_coords, ...projection.tileMercatorCoords)
      c.uniform4f(uniforms.u_projection_clipping_plane, ...projection.clippingPlane)
      c.uniform1f(uniforms.u_projection_transition, projection.projectionTransition)
      // 셰이더의 float 정밀도를 지키려고 페이지를 연 뒤 지난 초를 쓴다(1970년부터의 초는 너무 커서 원이 끊긴다).
      c.uniform1f(uniforms.u_time, (performance.now() - startedAt) / 1000)

      c.bindBuffer(c.ARRAY_BUFFER, vertexBuffer)
      c.bindBuffer(c.ELEMENT_ARRAY_BUFFER, indexBuffer)
      const stride = FLOATS_PER_VERTEX * 4
      const attributes: [string, number, number][] = [
        ['a_pos', 2, 0],
        ['a_lnglat', 2, 8],
        ['a_center', 2, 16],
        ['a_params', 3, 24],
      ]
      for (const [name, size, offset] of attributes) {
        const location = c.getAttribLocation(program, name)
        if (location < 0) continue
        c.enableVertexAttribArray(location)
        c.vertexAttribPointer(location, size, c.FLOAT, false, stride, offset)
      }

      c.enable(c.BLEND)
      c.blendFunc(c.ONE, c.ONE_MINUS_SRC_ALPHA)
      c.disable(c.DEPTH_TEST)
      c.disable(c.CULL_FACE)
      // 래스터 타일의 스텐실 상태가 남아 있으면 이 레이어가 통째로 걸러진다(mapDaylightLayer와 같은 이유).
      c.disable(c.STENCIL_TEST)
      c.colorMask(true, true, true, true)
      c.drawElements(c.TRIANGLES, indexCount, c.UNSIGNED_SHORT, 0)

      // 지도가 멈춰 있어도 원은 계속 퍼져야 한다. 다음 그리기를 예약한다.
      window.clearTimeout(repaintTimer)
      repaintTimer = window.setTimeout(() => map?.triggerRepaint(), FRAME_MS)
    },
  }
}
