import type { CustomLayerInterface, CustomRenderMethodInput, Map as MapLibreMap } from 'maplibre-gl'
import { API_BASE_URL } from '../../lib/api'
import { subsolarPoint } from '../../lib/sun'
import { useSettingsStore } from '../../store/settingsStore'
import { MAP_DAYLIGHT_FULL_ZOOM, MAP_DAYLIGHT_NONE_ZOOM } from './constants'

/**
 * 지도(MapLibre 지구본) 위에 홈 지구본(HomeGlobe)과 같은 실제 낮·밤, 실시간 구름, 도시 불빛을 얹는 커스텀 레이어.
 *
 * 지도는 위성사진 타일을 그대로 붙여 그려서 지구 전체가 대낮처럼 보였고, 홈 지구본에서 확대해 넘어오는 순간
 * 품질 차이가 컸다. 타일 위에 이 레이어를 한 장 덮어 같은 효과를 낸다. 지도는 확대해 읽는 화면이므로
 * 지구가 작을 때만 진하게 보이고, 확대할수록 옅어져 줌 MAP_DAYLIGHT_NONE_ZOOM부터는 그리지 않는다.
 *
 * 홈 지구본의 바다 햇빛 반사·가장자리 대기 산란은 옮기지 않았다. 둘 다 카메라 방향이 필요한데, MapLibre가
 * 넘겨주는 것은 투영 행렬뿐이라 별도 계산이 필요하고, 지구본 바깥 대기광은 이미 GlobeHalo가 그린다.
 *
 * 홈 지구본은 선형 공간에서 조명하지만 MapLibre는 감마 공간(sRGB 값 그대로)으로 합성한다. 그래서 밝기 배율을
 * 감마로 바꿔(pow 1/2.2) 같은 명암이 나오게 한다.
 */

/** 위·경도 격자 간격(도). 삼각형이 평평하게 그려지므로 너무 성기면 지구 가장자리에서 각이 보인다. */
const GRID_STEP_DEG = 2
// 극점에서는 메르카토르 y가 무한대라 바로 앞에서 멈춘다. 0.1°면 화면에서 구별되지 않는다.
const MAX_LATITUDE = 89.9
const LIVE_CLOUDS_REFRESH_MS = 10 * 60 * 1000
// 우클릭 손전등. 홈 지구본(HomeGlobe)의 FLASHLIGHT_* 와 같은 값이다.
const FLASHLIGHT_RADIUS_RAD = 0.22
const FLASHLIGHT_FADE_IN_SEC = 0.15
const FLASHLIGHT_FADE_OUT_SEC = 1.0

const VERTEX = (prelude: string, define: string) => `#version 300 es
${prelude}
${define}
in vec2 a_pos;
in vec2 a_lnglat;
out vec2 v_lnglat;
void main() {
  v_lnglat = a_lnglat;
  gl_Position = projectTile(a_pos);
}`

// HomeGlobe의 EARTH_FRAGMENT·CLOUD_FRAGMENT·CLOUD_DENSITY와 같은 규칙이다. 한쪽을 고치면 다른 쪽도 맞춘다.
// 결과는 미리 곱한 알파(premultiplied)로 낸다: 화면 = rgb + 아래 지도 × (1 - a).
// 즉 a는 '아래 지도를 얼마나 어둡게 덮을지', rgb는 '그 위에 더할 빛(구름·밤 푸른빛·도시 불빛)'이다.
const FRAGMENT = `#version 300 es
precision highp float;
uniform sampler2D u_static_clouds;
uniform sampler2D u_live_clouds;
uniform sampler2D u_night;
uniform float u_live;
uniform float u_city_lights;
uniform float u_strength;
uniform vec3 u_sun;
uniform vec3 u_flash;
uniform float u_flash_strength;
in vec2 v_lnglat;
out vec4 fragColor;

const float PI = 3.141592653589793;

float cloudDensity(vec2 uv) {
  float fallback = texture(u_static_clouds, uv).r;
  float live = texture(u_live_clouds, uv).r;
  float latitude = abs(uv.y - 0.5) * 180.0;
  float liveWeight = u_live * (1.0 - smoothstep(60.0, 70.0, latitude));
  return mix(smoothstep(0.12, 1.0, fallback), smoothstep(0.0, 1.0, live), liveWeight);
}

vec3 toGamma(vec3 linear) { return pow(clamp(linear, 0.0, 1.0), vec3(1.0 / 2.2)); }

void main() {
  float lng = radians(v_lnglat.x);
  float lat = radians(v_lnglat.y);
  vec3 n = vec3(cos(lat) * cos(lng), sin(lat), cos(lat) * sin(lng));
  vec2 uv = vec2((v_lnglat.x + 180.0) / 360.0, (90.0 - v_lnglat.y) / 180.0);

  float sunDot = dot(n, u_sun);
  float light = max(sunDot, 0.0);
  float dayMix = smoothstep(-0.1, 0.2, sunDot);
  float cloud = cloudDensity(uv);

  // 아래 지도에 곱할 밝기: 태양 고도에 따른 명암 × 구름 그림자 × 구름에 가려진 만큼.
  // 밤 쪽 바닥값은 화면으로 맞췄다. 홈 지구본은 톤 매핑(ACES)이 어두운 값을 더 눌러 밤이 거의 검은데,
  // 같은 식(0.01)을 감마로만 바꾸면 0.12가 되어 지도의 밤이 남색으로 떠 보였다.
  float shade = max(toGamma(vec3(light * 1.7)).r, 0.05) * (1.0 - cloud * 0.4 * light);
  float keep = shade * (1.0 - cloud);
  // 손전등: 비추는 자리만 밤의 어두움을 걷어 아래 위성사진을 드러낸다. 구름은 그대로 덮는다.
  float flashCos = cos(${FLASHLIGHT_RADIUS_RAD.toFixed(2)});
  float spot = smoothstep(flashCos, mix(flashCos, 1.0, 0.7), dot(n, u_flash));
  keep = mix(keep, 0.9 * (1.0 - cloud), spot * u_flash_strength * (1.0 - dayMix));

  // 밤 지역에서도 대륙 윤곽이 보이도록 남기는 옅은 푸른빛. 위성사진(낮)의 밝기에 비례하게 하려고
  // 아래 지도 색을 알 수 없으므로 일정한 값으로 둔다.
  vec3 add = vec3(0.35, 0.45, 0.8) * 0.06 * (1.0 - dayMix) * (1.0 - cloud);
  add += toGamma(texture(u_night, uv).rgb * vec3(1.0, 0.85, 0.6) * 1.4) * (1.0 - dayMix) * u_city_lights * (1.0 - cloud);
  add += toGamma(vec3(light * 1.5 + 0.015) * mix(0.55, 1.0, cloud)) * cloud;

  float a = 1.0 - keep;
  fragColor = vec4(add, a) * u_strength;
}`

interface GridMesh {
  vertices: Float32Array
  indices: Uint16Array
}

/** 위·경도 격자. a_pos는 MapLibre가 투영할 메르카토르 좌표(0~1, 극 쪽은 범위를 넘는다), a_lnglat은 셰이더 계산용. */
function buildGrid(): GridMesh {
  const columns = 360 / GRID_STEP_DEG
  const rows = Math.round((2 * MAX_LATITUDE) / GRID_STEP_DEG)
  const vertices = new Float32Array((columns + 1) * (rows + 1) * 4)
  let v = 0
  for (let r = 0; r <= rows; r++) {
    const lat = MAX_LATITUDE - (2 * MAX_LATITUDE * r) / rows
    const mercatorY = 0.5 - Math.log(Math.tan(Math.PI / 4 + (lat * Math.PI) / 360)) / (2 * Math.PI)
    for (let c = 0; c <= columns; c++) {
      const lng = -180 + c * GRID_STEP_DEG
      vertices[v++] = (lng + 180) / 360
      vertices[v++] = mercatorY
      vertices[v++] = lng
      vertices[v++] = lat
    }
  }
  const indices = new Uint16Array(columns * rows * 6)
  let i = 0
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < columns; c++) {
      const a = r * (columns + 1) + c
      const b = a + columns + 1
      indices.set([a, b, a + 1, a + 1, b, b + 1], i)
      i += 6
    }
  }
  return { vertices, indices }
}

function compile(gl: WebGL2RenderingContext, type: number, source: string): WebGLShader {
  const shader = gl.createShader(type)!
  gl.shaderSource(shader, source)
  gl.compileShader(shader)
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    throw new Error(`daylight shader: ${gl.getShaderInfoLog(shader)}`)
  }
  return shader
}

function createTexture(gl: WebGL2RenderingContext, image?: TexImageSource): WebGLTexture {
  const texture = gl.createTexture()!
  gl.bindTexture(gl.TEXTURE_2D, texture)
  if (image) gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image)
  else gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, 1, 1, 0, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array([0, 0, 0, 255]))
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
  // 경도 ±180°에서 이어지도록 가로는 반복, 극에서는 가장자리를 늘린다.
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.REPEAT)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
  return texture
}

async function fetchImage(url: string, signal: AbortSignal): Promise<ImageBitmap | null> {
  try {
    const response = await fetch(url, { signal })
    if (!response.ok) return null
    return await createImageBitmap(await response.blob())
  } catch {
    return null
  }
}

/** 지금 태양이 머리 위에 있는 방향. 셰이더의 위경도 → 단위벡터 규칙(n)과 같은 축을 쓴다. */
function sunVector(): [number, number, number] {
  const sun = subsolarPoint(new Date())
  const lat = (sun.latitude * Math.PI) / 180
  const lng = (sun.longitude * Math.PI) / 180
  return [Math.cos(lat) * Math.cos(lng), Math.sin(lat), Math.cos(lat) * Math.sin(lng)]
}

/** 줌에 따른 효과 세기. FULL 이하에서 1, NONE 이상에서 0. */
function toVector(longitude: number, latitude: number): [number, number, number] {
  const lat = (latitude * Math.PI) / 180
  const lng = (longitude * Math.PI) / 180
  return [Math.cos(lat) * Math.cos(lng), Math.sin(lat), Math.cos(lat) * Math.sin(lng)]
}

function strengthAt(zoom: number): number {
  const t = Math.min(1, Math.max(0, (zoom - MAP_DAYLIGHT_FULL_ZOOM) / (MAP_DAYLIGHT_NONE_ZOOM - MAP_DAYLIGHT_FULL_ZOOM)))
  return 1 - t * t * (3 - 2 * t)
}

export interface DaylightLayer extends CustomLayerInterface {
  /** 손전등을 그 위경도에 켠다. null이면 서서히 끈다. */
  setFlashlight(point: { lng: number; lat: number } | null): void
}

export function createDaylightLayer(id: string): DaylightLayer {
  let map: MapLibreMap | null = null
  let gl: WebGL2RenderingContext | null = null
  const programs = new Map<string, { program: WebGLProgram; uniforms: Record<string, WebGLUniformLocation | null> }>()
  let vertexBuffer: WebGLBuffer | null = null
  let indexBuffer: WebGLBuffer | null = null
  let indexCount = 0
  let staticClouds: WebGLTexture | null = null
  let liveClouds: WebGLTexture | null = null
  let nightLights: WebGLTexture | null = null
  let hasLive = false
  const abort = new AbortController()
  let refreshTimer: number | undefined
  let unsubscribeSettings: (() => void) | null = null
  let flashVector: [number, number, number] = [1, 0, 0]
  let flashOn = false
  let flashLevel = 0
  let flashTime = 0

  const programFor = (shaderData: CustomRenderMethodInput['shaderData']) => {
    const cached = programs.get(shaderData.variantName)
    if (cached) return cached
    const context = gl!
    const program = context.createProgram()!
    context.attachShader(program, compile(context, context.VERTEX_SHADER, VERTEX(shaderData.vertexShaderPrelude, shaderData.define)))
    context.attachShader(program, compile(context, context.FRAGMENT_SHADER, FRAGMENT))
    context.linkProgram(program)
    if (!context.getProgramParameter(program, context.LINK_STATUS)) {
      throw new Error(`daylight program: ${context.getProgramInfoLog(program)}`)
    }
    const names = [
      'u_projection_matrix',
      'u_projection_fallback_matrix',
      'u_projection_tile_mercator_coords',
      'u_projection_clipping_plane',
      'u_projection_transition',
      'u_static_clouds',
      'u_live_clouds',
      'u_night',
      'u_live',
      'u_city_lights',
      'u_strength',
      'u_sun',
      'u_flash',
      'u_flash_strength',
    ]
    const uniforms = Object.fromEntries(names.map((name) => [name, context.getUniformLocation(program, name)]))
    const entry = { program, uniforms }
    programs.set(shaderData.variantName, entry)
    return entry
  }

  const loadInto = async (url: string, assign: (texture: WebGLTexture) => void) => {
    const image = await fetchImage(url, abort.signal)
    if (!image || !gl || abort.signal.aborted) return
    assign(createTexture(gl, image))
    image.close()
    map?.triggerRepaint()
  }

  const refreshLiveClouds = () =>
    loadInto(`${API_BASE_URL}/api/globe/clouds`, (texture) => {
      if (liveClouds) gl!.deleteTexture(liveClouds)
      liveClouds = texture
      hasLive = true
    })

  return {
    id,
    type: 'custom',
    setFlashlight(point) {
      flashOn = point !== null
      if (point) flashVector = toVector(point.lng, point.lat)
      flashTime = performance.now()
      map?.triggerRepaint()
    },
    renderingMode: '2d',
    onAdd(m, context) {
      map = m
      gl = context as WebGL2RenderingContext
      const mesh = buildGrid()
      vertexBuffer = gl.createBuffer()
      gl.bindBuffer(gl.ARRAY_BUFFER, vertexBuffer)
      gl.bufferData(gl.ARRAY_BUFFER, mesh.vertices, gl.STATIC_DRAW)
      indexBuffer = gl.createBuffer()
      gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indexBuffer)
      gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, mesh.indices, gl.STATIC_DRAW)
      indexCount = mesh.indices.length
      staticClouds = createTexture(gl)
      liveClouds = createTexture(gl)
      nightLights = createTexture(gl)
      // 지도에서는 2K로 충분하다. 저줌에서만 보이고, 홈 지구본이 같은 파일을 이미 받아 두어 캐시에서 나온다.
      void loadInto('/textures/clouds_2048.jpg', (texture) => {
        gl!.deleteTexture(staticClouds)
        staticClouds = texture
      })
      void loadInto('/textures/night_2048.jpg', (texture) => {
        gl!.deleteTexture(nightLights)
        nightLights = texture
      })
      void refreshLiveClouds()
      refreshTimer = window.setInterval(() => void refreshLiveClouds(), LIVE_CLOUDS_REFRESH_MS)
      unsubscribeSettings = useSettingsStore.subscribe(() => map?.triggerRepaint())
    },
    onRemove(_m, context) {
      abort.abort()
      window.clearInterval(refreshTimer)
      unsubscribeSettings?.()
      const c = context as WebGL2RenderingContext
      for (const { program } of programs.values()) c.deleteProgram(program)
      programs.clear()
      c.deleteBuffer(vertexBuffer)
      c.deleteBuffer(indexBuffer)
      for (const texture of [staticClouds, liveClouds, nightLights]) c.deleteTexture(texture)
    },
    render(context, options) {
      if (!map) return
      const strength = strengthAt(map.getZoom())
      // 지구본에서 평면 지도로 바뀌는 높은 줌에서는 어차피 세기가 0이다.
      if (strength <= 0 || options.defaultProjectionData.projectionTransition < 0.999) return
      const c = context as WebGL2RenderingContext
      const { program, uniforms } = programFor(options.shaderData)
      const projection = options.defaultProjectionData
      c.useProgram(program)
      c.uniformMatrix4fv(uniforms.u_projection_matrix, false, projection.mainMatrix)
      c.uniformMatrix4fv(uniforms.u_projection_fallback_matrix, false, projection.fallbackMatrix)
      c.uniform4f(uniforms.u_projection_tile_mercator_coords, ...projection.tileMercatorCoords)
      c.uniform4f(uniforms.u_projection_clipping_plane, ...projection.clippingPlane)
      c.uniform1f(uniforms.u_projection_transition, projection.projectionTransition)
      c.uniform1f(uniforms.u_live, hasLive ? 1 : 0)
      c.uniform1f(uniforms.u_city_lights, useSettingsStore.getState().cityLights ? 1 : 0)
      c.uniform1f(uniforms.u_strength, strength)
      c.uniform3f(uniforms.u_sun, ...sunVector())
      const now = performance.now()
      const dt = (now - flashTime) / 1000
      flashTime = now
      flashLevel = flashOn
        ? Math.min(1, flashLevel + dt / FLASHLIGHT_FADE_IN_SEC)
        : Math.max(0, flashLevel - dt / FLASHLIGHT_FADE_OUT_SEC)
      // 켜지거나 꺼지는 중에는 지도가 멈춰 있어도 다음 프레임을 요청한다.
      if (flashLevel > 0 && flashLevel < 1) map.triggerRepaint()
      c.uniform3f(uniforms.u_flash, ...flashVector)
      c.uniform1f(uniforms.u_flash_strength, flashLevel)
      const textures: [WebGLTexture | null, string][] = [
        [staticClouds, 'u_static_clouds'],
        [liveClouds, 'u_live_clouds'],
        [nightLights, 'u_night'],
      ]
      textures.forEach(([texture, name], unit) => {
        c.activeTexture(c.TEXTURE0 + unit)
        c.bindTexture(c.TEXTURE_2D, texture)
        c.uniform1i(uniforms[name], unit)
      })

      c.bindBuffer(c.ARRAY_BUFFER, vertexBuffer)
      c.bindBuffer(c.ELEMENT_ARRAY_BUFFER, indexBuffer)
      const position = c.getAttribLocation(program, 'a_pos')
      const lngLat = c.getAttribLocation(program, 'a_lnglat')
      c.enableVertexAttribArray(position)
      c.vertexAttribPointer(position, 2, c.FLOAT, false, 16, 0)
      c.enableVertexAttribArray(lngLat)
      c.vertexAttribPointer(lngLat, 2, c.FLOAT, false, 16, 8)

      c.enable(c.BLEND)
      c.blendFunc(c.ONE, c.ONE_MINUS_SRC_ALPHA)
      c.disable(c.DEPTH_TEST)
      c.disable(c.CULL_FACE)
      // 래스터 타일은 스텐실로 잘라 그리므로 그 상태가 남아 있으면 이 레이어가 통째로 걸러진다.
      c.disable(c.STENCIL_TEST)
      c.colorMask(true, true, true, true)
      c.drawElements(c.TRIANGLES, indexCount, c.UNSIGNED_SHORT, 0)
    },
  }
}
