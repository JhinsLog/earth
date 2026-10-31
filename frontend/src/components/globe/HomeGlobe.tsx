import { useEffect, useRef, useState } from 'react'
import * as THREE from 'three'
import Starfield from './Starfield'
import { HOME_GLOBE_CLICK_TOLERANCE_PX } from './constants'
import type { GeoPoint } from '../../lib/geolocate'
import { API_BASE_URL } from '../../lib/api'
import { subsolarPoint } from '../../lib/sun'
import { useSettingsStore } from '../../store/settingsStore'
import { EVENT_CATEGORY_COLOR, RESTRICTED_STAR_COLOR, type EarthEvent } from '../../types'
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
const CLOUD_RADIUS = 1.006
const ATMOSPHERE_RADIUS = 1.025
// 도시 불빛 토글이 한 번에 켜지고 꺼지지 않고 이 시간 동안 번지듯 바뀐다.
const CITY_LIGHTS_FADE_SEC = 0.6
// 정적 구름 사진일 때만 지구보다 조금 빨리 흘려 정지 사진이 아니라는 느낌을 준다.
// 실시간 구름은 실제 위치가 의미라 흘리지 않는다.
const CLOUD_DRIFT_RAD_PER_SEC = 0.004

// 백엔드가 10분마다 위성 영상을 다시 합성한다(응답 캐시는 5분). 같은 간격으로 다시 받는다.
const LIVE_CLOUDS_REFRESH_MS = 10 * 60 * 1000

interface LiveClouds {
  observedAt: string | null
  /** 이 이미지에 쓰인 자료의 제공 기관. 출처 표기에 그대로 쓴다. */
  sources: string[]
}

/**
 * 실시간 구름(정지궤도 위성 다섯 기 합성, 빈 곳은 NOAA GMGSI). 실패하면 null이고 지금 쓰는 구름을 그대로 둔다.
 * 구름은 장식이라 이 요청이 실패해도 화면은 정상이어야 한다.
 */
async function loadLiveClouds(signal: AbortSignal): Promise<({ texture: THREE.Texture } & LiveClouds) | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/globe/clouds`, { signal })
    if (!response.ok) return null
    const observedAt = response.headers.get('X-Clouds-Observed-At')
    const sources = (response.headers.get('X-Clouds-Sources') ?? '').split(',').filter(Boolean)
    const url = URL.createObjectURL(await response.blob())
    try {
      const texture = await new THREE.TextureLoader().loadAsync(url)
      return { texture, observedAt, sources }
    } finally {
      URL.revokeObjectURL(url)
    }
  } catch {
    return null
  }
}

// 지구 표면(그림자)과 구름층이 같은 규칙으로 구름 밀도를 읽는다.
// 실시간 구름은 원본이 위도 약 72°까지만 있어서 60~70°에서 정적 사진으로 넘겨 극지방을 채운다.
// 정적 구름 사진에는 촬영 구획 단위의 옅은 연무(밝기 5~10%)가 사각형으로 남아 있어 문턱 0.12를 둔다.
// 실시간 구름은 백엔드가 이미 지표를 걸러 0으로 만들어 오므로, 같은 문턱을 또 걸면 얇은 구름까지 지워진다.
const CLOUD_DENSITY = /* glsl */ `
  uniform sampler2D cloudMap;
  uniform sampler2D liveCloudMap;
  uniform float liveClouds;
  float cloudDensity(vec2 uv, vec2 staticUv) {
    float fallback = texture2D(cloudMap, staticUv).r;
    float live = texture2D(liveCloudMap, uv).r;
    float latitude = abs(uv.y - 0.5) * 180.0;
    float liveWeight = liveClouds * (1.0 - smoothstep(60.0, 70.0, latitude));
    return mix(smoothstep(0.12, 1.0, fallback), smoothstep(0.0, 0.8, live), liveWeight);
  }
`

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
  varying vec3 vWorldNormal;
  varying vec3 vWorldPos;
  void main() {
    vUv = uv;
    vWorldNormal = normalize(mat3(modelMatrix) * normal);
    vec4 world = modelMatrix * vec4(position, 1.0);
    vWorldPos = world.xyz;
    gl_Position = projectionMatrix * viewMatrix * world;
  }
`

// 태양은 실제 시각의 태양 직하점 방향이다(lib/sun.ts). 지구본의 자전은 '보는 위치가 도는 것'이라 태양도
// 지구와 함께 돈다. 그래서 어느 쪽을 보든 지금 실제로 낮인 곳이 밝고 밤인 곳이 어둡다.
//
// 밤 지역의 도시 불빛은 별(이벤트)과 겹쳐 구분이 어려워 기본은 끄고, 설정에서 켤 수 있다(cityLights 0~1).
// 꺼져 있어도 대륙 윤곽은 보이도록 밤 지역에 아주 옅은 푸른빛을 남긴다.
//
// 텍스처는 sRGB로 읽어 선형 공간에서 조명하고, 마지막에 톤 매핑과 sRGB 변환을 거친다. 감마 공간에서
// 그대로 곱하면 명암이 뭉개져 바다가 회보라색으로 뜬다.
const EARTH_FRAGMENT = /* glsl */ `
  uniform sampler2D dayMap;
  uniform sampler2D nightMap;
  ${CLOUD_DENSITY}
  uniform sampler2D waterMap;
  uniform vec3 sunWorld;
  uniform float cloudOffset;
  uniform float cityLights;
  varying vec2 vUv;
  varying vec3 vWorldNormal;
  varying vec3 vWorldPos;
  void main() {
    vec3 n = normalize(vWorldNormal);
    vec3 viewDir = normalize(cameraPosition - vWorldPos);
    vec3 day = texture2D(dayMap, vUv).rgb;
    vec3 night = texture2D(nightMap, vUv).rgb;
    float sunDot = dot(n, sunWorld);
    float light = max(sunDot, 0.0);
    float dayMix = smoothstep(-0.1, 0.2, sunDot);

    // 구름 그림자. 구름층이 지구보다 조금 빨리 돌기 때문에 그만큼 밀어서 읽는다.
    float cloudShadow = cloudDensity(vUv, vUv - vec2(cloudOffset, 0.0));
    vec3 color = day * (light * 1.7 + 0.01) * (1.0 - cloudShadow * 0.4 * light);
    color += day * vec3(0.35, 0.45, 0.8) * 0.12 * (1.0 - dayMix);
    color += night * vec3(1.0, 0.85, 0.6) * 1.4 * (1.0 - dayMix) * cityLights;

    // 바다에만 햇빛 반사. 처음엔 '어둡고 푸른 픽셀'을 바다로 봤는데, 위성 사진의 섬 주변에 남은 JPEG
    // 압축 블록에서 판정이 뒤집혀 반사광 속에 사각형 얼룩이 생겼다. 육지/바다 마스크를 따로 쓴다.
    float water = texture2D(waterMap, vUv).r;
    // 블루 마블의 바다는 거의 검은 남색이라 실사 렌더처럼 깊은 파랑이 나오도록 조금 끌어올린다.
    color = mix(color, color * vec3(0.8, 1.15, 1.6) + vec3(0.002, 0.008, 0.026) * (light + 0.05), water);
    vec3 halfDir = normalize(sunWorld + viewDir);
    float nh = max(dot(n, halfDir), 0.0);
    float spec = pow(nh, 60.0) * 0.12 + pow(nh, 500.0) * 1.3;
    color += vec3(1.0, 0.96, 0.88) * spec * water * light * (1.0 - cloudShadow);
    // 바다의 비스듬한 면은 하늘빛을 조금 비춘다(프레넬).
    float fresnel = pow(1.0 - max(dot(n, viewDir), 0.0), 5.0);
    color += vec3(0.06, 0.12, 0.25) * fresnel * water * dayMix;

    // 가장자리의 얇은 대기층. 빛이 비스듬히 대기를 길게 지나는 곳일수록 푸르게 번진다.
    float rim = pow(1.0 - max(dot(n, viewDir), 0.0), 3.5);
    color = mix(color, vec3(0.32, 0.55, 1.0) * (0.15 + light), rim * 0.65 * smoothstep(-0.25, 0.35, sunDot));

    gl_FragColor = vec4(color, 1.0);
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }
`

// 구름층. 밝기를 투명도로 쓰고, 해가 비치는 만큼 하얗게 빛난다.
const CLOUD_FRAGMENT = /* glsl */ `
  ${CLOUD_DENSITY}
  uniform vec3 sunWorld;
  varying vec2 vUv;
  varying vec3 vWorldNormal;
  varying vec3 vWorldPos;
  void main() {
    vec3 n = normalize(vWorldNormal);
    vec3 viewDir = normalize(cameraPosition - vWorldPos);
    float light = max(dot(n, sunWorld), 0.0);
    // 가장자리에서는 구름이 대기에 묻혀 흐려진다.
    float edgeFade = smoothstep(0.0, 0.35, dot(n, viewDir));
    float alpha = cloudDensity(vUv, vUv) * edgeFade;
    vec3 color = vec3(1.0) * (light * 1.5 + 0.015);
    gl_FragColor = vec4(color, alpha);
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }
`

const ATMOSPHERE_VERTEX = /* glsl */ `
  varying vec3 vViewNormal;
  varying vec3 vWorldNormal;
  void main() {
    vViewNormal = normalize(normalMatrix * normal);
    vWorldNormal = normalize(mat3(modelMatrix) * normal);
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`

// 지구보다 2.5% 큰 구의 안쪽 면. 지구 테두리 바로 바깥에서 가장 밝고 바깥으로 빠르게 사라지는 얇은 띠.
// 이 반지름에서 지구 테두리는 껍질 법선과 시선의 내적이 약 -0.22인 곳이다.
const ATMOSPHERE_FRAGMENT = /* glsl */ `
  uniform vec3 sunWorld;
  varying vec3 vViewNormal;
  varying vec3 vWorldNormal;
  void main() {
    float d = dot(vViewNormal, vec3(0.0, 0.0, 1.0));
    float intensity = pow(clamp(-d / 0.22, 0.0, 1.0), 2.0);
    float lit = smoothstep(-0.3, 0.5, dot(normalize(vWorldNormal), sunWorld));
    vec3 color = vec3(0.35, 0.6, 1.0) * intensity * lit * 0.9;
    gl_FragColor = vec4(color, 1.0);
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
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

/** 예: "14:30 NASA GIBS · EUMETSAT, contains modified EUMETSAT Meteosat data 2026". 시각은 보는 사람의 현지 시각. */
function cloudCredit(observedAt: string, sources: string[]): string {
  const date = new Date(observedAt)
  const time = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
  const names = sources.length > 0 ? sources : ['NOAA GMGSI']
  const meteosat = names.some((s) => s === 'EUMETSAT' || s === 'NOAA GMGSI')
  return `${time} ${names.join(' · ')}${meteosat ? `, contains modified EUMETSAT Meteosat data ${date.getUTCFullYear()}` : ''}`
}

export default function HomeGlobe({ events, center, onEnter }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [ready, setReady] = useState(false)
  const [hoveringEarth, setHoveringEarth] = useState(false)
  // 실시간 구름의 관측 시각과 출처. 없으면 정적 구름 사진을 쓰는 중이다.
  const [liveClouds, setLiveClouds] = useState<LiveClouds | null>(null)

  // 장면을 만드는 effect는 한 번만 돌기 때문에, 그 안의 클릭 처리기는 이 ref로 최신 콜백을 읽는다.
  const onEnterRef = useRef(onEnter)
  useEffect(() => {
    onEnterRef.current = onEnter
  }, [onEnter])
  // 장면은 한 번만 만들고, 별 데이터만 바뀔 때마다 갈아 끼운다.
  const starsRef = useRef<{ geometry: THREE.BufferGeometry; points: THREE.Points } | null>(null)
  const initialCenterRef = useRef(center)
  // 설정의 도시 불빛. 장면 effect는 한 번만 돌기 때문에 렌더 루프가 이 ref를 읽어 서서히 맞춘다.
  const cityLights = useSettingsStore((state) => state.cityLights)
  const cityLightsTargetRef = useRef(cityLights ? 1 : 0)
  useEffect(() => {
    cityLightsTargetRef.current = cityLights ? 1 : 0
  }, [cityLights])

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
    const pixelRatio = Math.min(window.devicePixelRatio, 2)
    renderer.setPixelRatio(pixelRatio)
    renderer.setClearColor(0x000000, 0)
    renderer.toneMapping = THREE.ACESFilmicToneMapping
    renderer.toneMappingExposure = 1.15
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
    // 자전은 실제 시각에서 바로 구한다: 태양 쪽 경도를 정면에서 늘 같은 각도에 둔다(아래 animate).
    // 이 값은 '처음 정면에 둘 경도'와 '지금 태양 직하점 경도'의 차이이고, 사용자가 끌면 바뀐다.
    let viewOffset = THREE.MathUtils.degToRad(subsolarPoint(new Date()).longitude - initialCenterRef.current.longitude)

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
    // 구름은 밝기 자체가 밀도라 색 공간 변환을 하지 않는다.
    const cloudMap = loader.load(`/textures/clouds_${size}.jpg`)
    // 해안선이 부드럽게 섞이기만 하면 되므로 마스크는 한 크기면 충분하다.
    const waterMap = loader.load('/textures/water_2048.jpg')
    dayMap.colorSpace = THREE.SRGBColorSpace
    // 구름층이 돌면서 경도 ±180°를 넘어 읽으므로 가로는 이어 붙인다.
    cloudMap.wrapS = THREE.RepeatWrapping
    nightMap.colorSpace = THREE.SRGBColorSpace
    for (const map of [dayMap, nightMap, cloudMap]) {
      map.anisotropy = Math.min(8, renderer.capabilities.getMaxAnisotropy())
    }

    // 실시간 구름이 오기 전까지 셰이더에 묶어 둘 빈 텍스처.
    const noLiveClouds = new THREE.DataTexture(new Uint8Array([0, 0, 0, 255]), 1, 1)
    noLiveClouds.needsUpdate = true
    const cloudUniforms = {
      cloudMap: { value: cloudMap },
      liveCloudMap: { value: noLiveClouds as THREE.Texture },
      liveClouds: { value: 0 },
    }

    // 세 재질이 같은 벡터를 본다. 매 프레임 이 벡터만 고치면 모두에 반영된다.
    const sunWorld = new THREE.Vector3()
    const earthMaterial = new THREE.ShaderMaterial({
      uniforms: {
        ...cloudUniforms,
        dayMap: { value: dayMap },
        nightMap: { value: nightMap },
        waterMap: { value: waterMap },
        sunWorld: { value: sunWorld },
        cloudOffset: { value: 0 },
        cityLights: { value: cityLightsTargetRef.current },
      },
      vertexShader: EARTH_VERTEX,
      fragmentShader: EARTH_FRAGMENT,
    })
    const earthGeometry = new THREE.SphereGeometry(EARTH_RADIUS, 128, 64)
    const earth = new THREE.Mesh(earthGeometry, earthMaterial)
    spin.add(earth)

    const cloudGeometry = new THREE.SphereGeometry(CLOUD_RADIUS, 128, 64)
    const cloudMaterial = new THREE.ShaderMaterial({
      // 같은 uniform 객체를 공유해 실시간 구름이 오면 두 재질에 한 번에 반영된다.
      uniforms: { ...cloudUniforms, sunWorld: { value: sunWorld } },
      vertexShader: EARTH_VERTEX,
      fragmentShader: CLOUD_FRAGMENT,
      transparent: true,
      depthWrite: false,
    })
    const clouds = new THREE.Mesh(cloudGeometry, cloudMaterial)
    spin.add(clouds)

    const atmosphereGeometry = new THREE.SphereGeometry(ATMOSPHERE_RADIUS, 96, 48)
    const atmosphereMaterial = new THREE.ShaderMaterial({
      uniforms: { sunWorld: { value: sunWorld } },
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
        viewOffset += (e.clientX - lastX) * 0.005
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

    const liveCloudsRequest = new AbortController()
    let liveCloudMap: THREE.Texture | null = null
    let liveObservedAt: string | null = null
    const refreshLiveClouds = () =>
      loadLiveClouds(liveCloudsRequest.signal).then((live) => {
        // 같은 이미지면 갈아 끼우지 않는다(GPU 업로드를 아낀다).
        if (!live || liveCloudsRequest.signal.aborted || (liveCloudMap && live.observedAt === liveObservedAt)) {
          live?.texture.dispose()
          return
        }
        const previous = liveCloudMap
        liveCloudMap = live.texture
        liveObservedAt = live.observedAt
        // 경도 ±180°에서 이어 읽는다.
        liveCloudMap.wrapS = THREE.RepeatWrapping
        liveCloudMap.anisotropy = Math.min(8, renderer.capabilities.getMaxAnisotropy())
        cloudUniforms.liveCloudMap.value = liveCloudMap
        cloudUniforms.liveClouds.value = 1
        previous?.dispose()
        // 실제 위치에 맞춰 구름층을 원위치로 되돌린다.
        clouds.rotation.y = 0
        setLiveClouds({ observedAt: live.observedAt, sources: live.sources })
      })
    void refreshLiveClouds()
    const liveCloudsTimer = window.setInterval(() => void refreshLiveClouds(), LIVE_CLOUDS_REFRESH_MS)

    // 지구 좌표계에서의 태양 방향. 렌더할 때 지구의 현재 회전을 입혀 sunWorld로 만든다.
    const sunLocal = new THREE.Vector3()
    const earthRotation = new THREE.Quaternion()

    const timer = new THREE.Timer()
    let rafId = 0
    const animate = (timestamp: number) => {
      timer.update(timestamp)
      // 탭이 백그라운드였다가 돌아오면 경과 시간이 한꺼번에 들어온다. 지구가 순간이동하지 않게 자른다.
      const dt = Math.min(timer.getDelta(), 0.1)
      // 실제 자전: 태양 직하점은 하루(24시간)에 한 바퀴 서쪽으로 돈다. 정면을 그만큼 따라 돌리면 화면에서 태양
      // 방향은 고정되고, 지구가 그 아래에서 하루 한 바퀴 돌며 낮·밤 경계가 지표 위를 시간당 15°씩 지나간다.
      // 경과 시간(dt)을 쌓지 않고 시각에서 바로 구하는 이유: 탭이 백그라운드였다 돌아와도 태양과 어긋나지 않는다.
      const sun = subsolarPoint(new Date())
      sunLocal.copy(toVector(sun.latitude, sun.longitude, 1))
      spin.rotation.y = spinFacing(sun.longitude) + viewOffset
      spin.updateMatrixWorld()
      sunWorld.copy(sunLocal).applyQuaternion(earth.getWorldQuaternion(earthRotation))
      const lights = earthMaterial.uniforms.cityLights
      const step = dt / CITY_LIGHTS_FADE_SEC
      lights.value += THREE.MathUtils.clamp(cityLightsTargetRef.current - lights.value, -step, step)
      if (cloudUniforms.liveClouds.value === 0) clouds.rotation.y += CLOUD_DRIFT_RAD_PER_SEC * dt
      // 구름 구를 a만큼 돌리면 텍스처의 u가 a/2π만큼 밀린다. 그림자도 같은 만큼 밀어 읽는다.
      earthMaterial.uniforms.cloudOffset.value = clouds.rotation.y / (2 * Math.PI)
      starMaterial.uniforms.time.value = timer.getElapsed()
      renderer.render(scene, camera)
      rafId = requestAnimationFrame(animate)
    }
    rafId = requestAnimationFrame(animate)

    return () => {
      cancelAnimationFrame(rafId)
      liveCloudsRequest.abort()
      window.clearInterval(liveCloudsTimer)
      liveCloudMap?.dispose()
      noLiveClouds.dispose()
      timer.dispose()
      resizeObserver.disconnect()
      canvas.removeEventListener('pointerdown', onPointerDown)
      canvas.removeEventListener('pointermove', onPointerMove)
      canvas.removeEventListener('pointerup', onPointerUp)
      canvas.removeEventListener('pointercancel', onPointerUp)
      starsRef.current = null
      for (const disposable of [
        earthGeometry, earthMaterial, cloudGeometry, cloudMaterial, atmosphereGeometry, atmosphereMaterial,
        starGeometry, starMaterial, dayMap, nightMap, cloudMap, waterMap,
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
      // 위치만 아는 별(비로그인, 다른 나라)은 분류를 모르므로 중립색.
      color.set(event.restricted ? RESTRICTED_STAR_COLOR : EVENT_CATEGORY_COLOR[event.category]).toArray(colors, i * 3)
      // id에서 위상을 뽑아 다시 그려도 같은 별이 같은 박자로 깜빡이게 한다.
      phases[i] = ((event.id * 0.618034) % 1 + 1) % 1
      // 공감이 많을수록 조금 더 크게. 상한을 둬 한 별이 화면을 덮지 않게 한다.
      scales[i] = event.restricted ? 1 : 1 + Math.min(event.confirmCount, 10) * 0.08
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
      {/* NASA 자료는 퍼블릭 도메인이지만 출처 표기를 요청한다. 지도 화면은 MapLibre 출처 표시가 맡는다.
          EUMETSAT 영상과 GMGSI(Meteosat 포함)를 쓰면 EUMETSAT 데이터 정책 6.3조의 표기를 붙인다. */}
      <span className="home-globe__credit">
        Imagery: NASA GIBS · Clouds:{' '}
        {liveClouds?.observedAt ? cloudCredit(liveClouds.observedAt, liveClouds.sources) : 'NASA Visible Earth'}{' '}
        · Water mask © OpenStreetMap contributors
      </span>
    </div>
  )
}
