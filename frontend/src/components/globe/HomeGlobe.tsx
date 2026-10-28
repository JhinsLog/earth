import { useEffect, useRef, useState } from 'react'
import * as THREE from 'three'
import Starfield from './Starfield'
import { HOME_GLOBE_CLICK_TOLERANCE_PX, HOME_GLOBE_SPIN_RAD_PER_SEC } from './constants'
import { subsolarPoint } from '../../lib/sun'
import type { GeoPoint } from '../../lib/geolocate'
import { EVENT_CATEGORY_COLOR, type EarthEvent } from '../../types'
import './HomeGlobe.css'

interface Props {
  events: EarthEvent[]
  /** 처음 화면 정면에 둘 지점. 접속 지역의 대륙이 보이도록 HomePage가 정한다. */
  center: GeoPoint
  /** 지구 표면을 클릭했을 때. 클릭한 지점의 위경도를 넘긴다. */
  onEnter: (point: GeoPoint) => void
}

const EARTH_RADIUS = 1
// 별을 지표면에 딱 붙이면 지구와 깊이가 같아 깜빡이며 가려진다(z-fighting). 살짝 띄운다.
const STAR_RADIUS = 1.012
const MAX_TILT_RAD = (60 * Math.PI) / 180
const HOME_TILT_LIMIT_RAD = (30 * Math.PI) / 180
// 화면 짧은 변 대비 지구 지름 비율.
const GLOBE_SCREEN_FRACTION = 0.72
const FOV_DEG = 30

/**
 * 위경도 → 지구 로컬 좌표.
 *
 * three의 SphereGeometry는 u=0을 -X축에 두고 Y축을 따라 돌며 감는다. 등장방형 텍스처는 u=0이 경도 -180°이므로
 * 경도 0°는 +X, 경도 90°는 -Z가 된다. 이 대응이 어긋나면 별이 엉뚱한 대륙에 찍힌다.
 */
function toVector(latitude: number, longitude: number, radius: number): THREE.Vector3 {
  const lat = THREE.MathUtils.degToRad(latitude)
  const lng = THREE.MathUtils.degToRad(longitude)
  return new THREE.Vector3(
    radius * Math.cos(lat) * Math.cos(lng),
    radius * Math.sin(lat),
    -radius * Math.cos(lat) * Math.sin(lng),
  )
}

function toLatLng(local: THREE.Vector3): GeoPoint {
  const v = local.clone().normalize()
  return {
    latitude: THREE.MathUtils.radToDeg(Math.asin(v.y)),
    longitude: THREE.MathUtils.radToDeg(Math.atan2(-v.z, v.x)),
  }
}

/** 정면(+Z, 카메라 쪽)에 이 경도가 오게 하는 Y축 회전각. toVector의 축 대응에서 나온다. */
function spinFacing(longitude: number): number {
  return -Math.PI / 2 - THREE.MathUtils.degToRad(longitude)
}

const EARTH_VERTEX = /* glsl */ `
  varying vec2 vUv;
  varying vec3 vLocalNormal;
  varying vec3 vWorldNormal;
  varying vec3 vWorldPos;
  void main() {
    vUv = uv;
    vLocalNormal = normal;
    vWorldNormal = normalize(mat3(modelMatrix) * normal);
    vec4 world = modelMatrix * vec4(position, 1.0);
    vWorldPos = world.xyz;
    gl_Position = projectionMatrix * viewMatrix * world;
  }
`

// 조명은 지구에 붙은(로컬) 좌표에서 계산한다. 태양 방향을 실제 태양 직하점으로 두면 지구가 돌아도
// 낮·밤 경계가 지리에 고정돼, 지금 실제로 밤인 지역에 도시 불빛이 켜진다.
const EARTH_FRAGMENT = /* glsl */ `
  uniform sampler2D dayMap;
  uniform sampler2D nightMap;
  uniform vec3 sunLocal;
  uniform vec3 sunWorld;
  varying vec2 vUv;
  varying vec3 vLocalNormal;
  varying vec3 vWorldNormal;
  varying vec3 vWorldPos;
  void main() {
    vec3 day = texture2D(dayMap, vUv).rgb;
    vec3 night = texture2D(nightMap, vUv).rgb;
    float sunDot = dot(normalize(vLocalNormal), sunLocal);
    // 경계를 칼같이 자르지 않고 박명(황혼) 폭만큼 섞는다.
    float dayMix = smoothstep(-0.12, 0.2, sunDot);
    vec3 dayLit = day * (0.3 + 0.95 * max(sunDot, 0.0));
    vec3 nightLit = night * 1.5;
    vec3 color = mix(nightLit, dayLit, dayMix);

    // 바다에만 햇빛 반사. 별도 마스크 텍스처 없이 '어둡고 푸른 픽셀'을 바다로 본다.
    vec3 n = normalize(vWorldNormal);
    vec3 viewDir = normalize(cameraPosition - vWorldPos);
    float luminance = dot(day, vec3(0.299, 0.587, 0.114));
    float water = clamp((day.b - day.r) * 5.0, 0.0, 1.0) * (1.0 - smoothstep(0.12, 0.3, luminance));
    float spec = pow(max(dot(reflect(-sunWorld, n), viewDir), 0.0), 40.0);
    color += vec3(1.0, 0.95, 0.85) * spec * water * 0.6 * dayMix;

    // 가장자리의 얇은 대기층. 낮 쪽이 더 밝다.
    float rim = 1.0 - max(dot(viewDir, n), 0.0);
    color += vec3(0.35, 0.6, 1.0) * pow(rim, 3.0) * (0.25 + 0.6 * dayMix);
    gl_FragColor = vec4(color, 1.0);
  }
`

const ATMOSPHERE_VERTEX = /* glsl */ `
  varying vec3 vViewNormal;
  void main() {
    vViewNormal = normalize(normalMatrix * normal);
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`

// 지구보다 조금 큰 구의 안쪽 면만 그려 테두리 바깥으로 번지는 빛을 만든다.
const ATMOSPHERE_FRAGMENT = /* glsl */ `
  varying vec3 vViewNormal;
  void main() {
    float intensity = pow(clamp(0.72 - dot(vViewNormal, vec3(0.0, 0.0, 1.0)), 0.0, 1.0), 2.4);
    gl_FragColor = vec4(0.35, 0.62, 1.0, 1.0) * intensity * 1.3;
  }
`

const STAR_VERTEX = /* glsl */ `
  attribute vec3 color;
  attribute float phase;
  attribute float scale;
  uniform float time;
  uniform float pixelRatio;
  uniform float frontDistance;
  varying vec3 vColor;
  varying float vTwinkle;
  void main() {
    vColor = color;
    // 별마다 위상과 속도를 달리해 동시에 깜빡이지 않게 한다.
    vTwinkle = 0.55 + 0.45 * sin(time * (1.6 + phase * 0.7) + phase * 6.2831);
    vec4 mv = modelViewMatrix * vec4(position, 1.0);
    // 정면(카메라에서 가장 가까운 지표) 기준으로 20~30px. 가장자리로 갈수록 멀어져 조금 작아진다.
    // 거리로만 나누면 카메라가 멀어 별이 5px 남짓이 되어 밝은 낮 지역에서 보이지 않았다.
    gl_PointSize = scale * (20.0 + 10.0 * vTwinkle) * pixelRatio * frontDistance / -mv.z;
    gl_Position = projectionMatrix * mv;
  }
`

// 둥근 빛 + 십자 광선. MapLibre 쪽 별(starFlareTexture)과 비슷한 인상을 준다.
const STAR_FRAGMENT = /* glsl */ `
  varying vec3 vColor;
  varying float vTwinkle;
  void main() {
    vec2 p = gl_PointCoord - 0.5;
    float r = length(p);
    float core = exp(-r * r * 90.0);
    float glow = exp(-r * r * 14.0) * 0.55;
    float spikes = (exp(-abs(p.x) * 60.0) + exp(-abs(p.y) * 60.0)) * exp(-r * 6.0) * 0.5;
    float a = (core + glow + spikes) * (0.45 + 0.55 * vTwinkle);
    if (a < 0.01) discard;
    gl_FragColor = vec4(mix(vColor, vec3(1.0), core * 0.7) * a, a);
  }
`

export default function HomeGlobe({ events, center, onEnter }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [ready, setReady] = useState(false)
  const [hoveringEarth, setHoveringEarth] = useState(false)

  // 장면을 만드는 effect는 한 번만 돌기 때문에, 그 안의 클릭 처리기는 이 ref로 최신 콜백을 읽는다.
  const onEnterRef = useRef(onEnter)
  useEffect(() => {
    onEnterRef.current = onEnter
  }, [onEnter])
  // 장면은 한 번만 만들고, 별 데이터만 바뀔 때마다 갈아 끼운다.
  const starsRef = useRef<{ geometry: THREE.BufferGeometry; points: THREE.Points } | null>(null)
  const initialCenterRef = useRef(center)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
    const pixelRatio = Math.min(window.devicePixelRatio, 2)
    renderer.setPixelRatio(pixelRatio)
    renderer.setClearColor(0x000000, 0)
    container.appendChild(renderer.domElement)

    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(FOV_DEG, 1, 0.1, 100)

    // 바깥 그룹은 기울기(정면 위도), 안쪽 그룹은 자전. 순서를 바꾸면 기울어진 축으로 돌게 된다.
    const tilt = new THREE.Group()
    const spin = new THREE.Group()
    tilt.add(spin)
    scene.add(tilt)
    tilt.rotation.x = THREE.MathUtils.clamp(
      THREE.MathUtils.degToRad(initialCenterRef.current.latitude),
      -HOME_TILT_LIMIT_RAD,
      HOME_TILT_LIMIT_RAD,
    )
    spin.rotation.y = spinFacing(initialCenterRef.current.longitude)

    // 화면이 크면 4K 텍스처. 모바일이나 저사양 GPU에는 2K로 충분하고 로딩도 짧다.
    const useLarge =
      Math.max(window.innerWidth, window.innerHeight) * pixelRatio >= 1400 &&
      renderer.capabilities.maxTextureSize >= 4096
    const size = useLarge ? 4096 : 2048
    // 텍스처가 오기 전에는 검은 구만 보이므로, 둘 다 도착한 뒤에 페이드인한다.
    // 전역 DefaultLoadingManager를 쓰면 다른 로더와 신호가 섞이므로 전용 관리자를 둔다.
    const loader = new THREE.TextureLoader(new THREE.LoadingManager(() => setReady(true)))
    const dayMap = loader.load(`/textures/day_${size}.jpg`)
    const nightMap = loader.load(`/textures/night_${size}.jpg`)
    for (const map of [dayMap, nightMap]) {
      map.anisotropy = Math.min(8, renderer.capabilities.getMaxAnisotropy())
    }

    const sun = subsolarPoint(new Date())
    const sunLocal = toVector(sun.latitude, sun.longitude, 1)
    const earthMaterial = new THREE.ShaderMaterial({
      uniforms: {
        dayMap: { value: dayMap },
        nightMap: { value: nightMap },
        sunLocal: { value: sunLocal },
        sunWorld: { value: new THREE.Vector3() },
      },
      vertexShader: EARTH_VERTEX,
      fragmentShader: EARTH_FRAGMENT,
    })
    const earthGeometry = new THREE.SphereGeometry(EARTH_RADIUS, 128, 64)
    const earth = new THREE.Mesh(earthGeometry, earthMaterial)
    spin.add(earth)

    const atmosphereGeometry = new THREE.SphereGeometry(EARTH_RADIUS * 1.1, 64, 32)
    const atmosphereMaterial = new THREE.ShaderMaterial({
      vertexShader: ATMOSPHERE_VERTEX,
      fragmentShader: ATMOSPHERE_FRAGMENT,
      side: THREE.BackSide,
      blending: THREE.AdditiveBlending,
      transparent: true,
      depthWrite: false,
    })
    scene.add(new THREE.Mesh(atmosphereGeometry, atmosphereMaterial))

    const starMaterial = new THREE.ShaderMaterial({
      uniforms: { time: { value: 0 }, pixelRatio: { value: pixelRatio }, frontDistance: { value: 1 } },
      vertexShader: STAR_VERTEX,
      fragmentShader: STAR_FRAGMENT,
      transparent: true,
      blending: THREE.AdditiveBlending,
      // 깊이는 검사하되 쓰지 않는다. 지구 뒤편 별은 가려지고, 별끼리는 서로 가리지 않는다.
      depthTest: true,
      depthWrite: false,
    })
    const starGeometry = new THREE.BufferGeometry()
    const stars = new THREE.Points(starGeometry, starMaterial)
    stars.renderOrder = 1
    spin.add(stars)
    starsRef.current = { geometry: starGeometry, points: stars }

    const resize = () => {
      const width = container.clientWidth
      const height = container.clientHeight
      if (width === 0 || height === 0) return
      renderer.setSize(width, height, false)
      camera.aspect = width / height
      // 짧은 변 기준으로 지구 크기를 맞춘다. 세로 화면(모바일)에서 좌우가 잘리지 않도록.
      const halfFov = THREE.MathUtils.degToRad(FOV_DEG / 2)
      const t = Math.tan(halfFov) * Math.min(1, camera.aspect) * GLOBE_SCREEN_FRACTION
      camera.position.set(0, 0, Math.sqrt(1 + 1 / (t * t)))
      starMaterial.uniforms.frontDistance.value = camera.position.z - STAR_RADIUS
      camera.lookAt(0, 0, 0)
      camera.updateProjectionMatrix()
    }
    resize()
    const resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(container)

    const raycaster = new THREE.Raycaster()
    const pointer = new THREE.Vector2()
    const hitEarth = (clientX: number, clientY: number): THREE.Intersection | null => {
      const rect = renderer.domElement.getBoundingClientRect()
      pointer.set(((clientX - rect.left) / rect.width) * 2 - 1, -((clientY - rect.top) / rect.height) * 2 + 1)
      raycaster.setFromCamera(pointer, camera)
      return raycaster.intersectObject(earth, false)[0] ?? null
    }

    let dragging = false
    let downX = 0
    let downY = 0
    let lastX = 0
    let lastY = 0
    let movedFar = false
    const canvas = renderer.domElement
    const onPointerDown = (e: PointerEvent) => {
      dragging = true
      movedFar = false
      downX = lastX = e.clientX
      downY = lastY = e.clientY
      canvas.setPointerCapture(e.pointerId)
    }
    const onPointerMove = (e: PointerEvent) => {
      if (dragging) {
        if (Math.hypot(e.clientX - downX, e.clientY - downY) > HOME_GLOBE_CLICK_TOLERANCE_PX) movedFar = true
        spin.rotation.y += (e.clientX - lastX) * 0.005
        tilt.rotation.x = THREE.MathUtils.clamp(tilt.rotation.x + (e.clientY - lastY) * 0.005, -MAX_TILT_RAD, MAX_TILT_RAD)
        lastX = e.clientX
        lastY = e.clientY
      } else if (e.pointerType === 'mouse') {
        setHoveringEarth(hitEarth(e.clientX, e.clientY) !== null)
      }
    }
    const onPointerUp = (e: PointerEvent) => {
      if (!dragging) return
      dragging = false
      canvas.releasePointerCapture(e.pointerId)
      if (movedFar) return
      const hit = hitEarth(e.clientX, e.clientY)
      if (!hit) return
      onEnterRef.current(toLatLng(earth.worldToLocal(hit.point.clone())))
    }
    canvas.addEventListener('pointerdown', onPointerDown)
    canvas.addEventListener('pointermove', onPointerMove)
    canvas.addEventListener('pointerup', onPointerUp)
    canvas.addEventListener('pointercancel', onPointerUp)

    const timer = new THREE.Timer()
    const sunWorld = new THREE.Vector3()
    let rafId = 0
    const animate = (timestamp: number) => {
      timer.update(timestamp)
      // 탭이 백그라운드였다가 돌아오면 경과 시간이 한꺼번에 들어온다. 지구가 순간이동하지 않게 자른다.
      const dt = Math.min(timer.getDelta(), 0.1)
      if (!dragging) spin.rotation.y += HOME_GLOBE_SPIN_RAD_PER_SEC * dt
      starMaterial.uniforms.time.value = timer.getElapsed()
      earth.updateMatrixWorld()
      sunWorld.copy(sunLocal).transformDirection(earth.matrixWorld)
      earthMaterial.uniforms.sunWorld.value = sunWorld
      renderer.render(scene, camera)
      rafId = requestAnimationFrame(animate)
    }
    rafId = requestAnimationFrame(animate)

    return () => {
      cancelAnimationFrame(rafId)
      timer.dispose()
      resizeObserver.disconnect()
      canvas.removeEventListener('pointerdown', onPointerDown)
      canvas.removeEventListener('pointermove', onPointerMove)
      canvas.removeEventListener('pointerup', onPointerUp)
      canvas.removeEventListener('pointercancel', onPointerUp)
      starsRef.current = null
      for (const disposable of [
        earthGeometry, earthMaterial, atmosphereGeometry, atmosphereMaterial,
        starGeometry, starMaterial, dayMap, nightMap,
      ]) {
        disposable.dispose()
      }
      renderer.dispose()
      container.removeChild(canvas)
    }
  }, [])

  // 실시간으로 별이 생기고 사라지면 위치 버퍼만 다시 만든다.
  useEffect(() => {
    const stars = starsRef.current
    if (!stars) return
    const positions = new Float32Array(events.length * 3)
    const colors = new Float32Array(events.length * 3)
    const phases = new Float32Array(events.length)
    const scales = new Float32Array(events.length)
    const color = new THREE.Color()
    events.forEach((event, i) => {
      toVector(event.latitude, event.longitude, STAR_RADIUS).toArray(positions, i * 3)
      color.set(EVENT_CATEGORY_COLOR[event.category]).toArray(colors, i * 3)
      // id에서 위상을 뽑아 다시 그려도 같은 별이 같은 박자로 깜빡이게 한다.
      phases[i] = ((event.id * 0.618034) % 1 + 1) % 1
      // 공감이 많을수록 조금 더 크게. 상한을 둬 한 별이 화면을 덮지 않게 한다.
      scales[i] = 1 + Math.min(event.confirmCount, 10) * 0.08
    })
    const geometry = stars.geometry
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))
    geometry.setAttribute('phase', new THREE.BufferAttribute(phases, 1))
    geometry.setAttribute('scale', new THREE.BufferAttribute(scales, 1))
    geometry.computeBoundingSphere()
  }, [events])

  return (
    <div className={`home-globe${ready ? ' home-globe--ready' : ''}`}>
      <Starfield />
      <div
        ref={containerRef}
        className={`home-globe__canvas${hoveringEarth ? ' home-globe__canvas--hover' : ''}`}
      />
      {/* NASA 자료는 퍼블릭 도메인이지만 출처 표기를 요청한다. 지도 화면은 MapLibre 출처 표시가 맡는다. */}
      <span className="home-globe__credit">Imagery: NASA GIBS (Blue Marble, Black Marble)</span>
    </div>
  )
}
