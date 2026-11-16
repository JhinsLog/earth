import type { Quake } from '../../types'

/**
 * 지진 표현: 진앙에 점을 찍고, 그 둘레로 원이 퍼져 나가 에너지가 전달되는 모습을 그린다.
 * 홈 라스(HomeGlobe, three.js)와 지도(mapQuakeLayer, MapLibre 커스텀 레이어)가 같은 셰이더 함수를 쓴다.
 *
 * 원은 화면 원이 아니라 <b>지표면 위의 원</b>이다. 조각마다 진앙까지의 거리를 계산해 그리므로, 라스 가장자리에서는
 * 원이 지구 곡면을 따라 찌그러져 보이고 확대하면 실제 거리만큼 커진다.
 *
 * 크기·밝기는 표현일 뿐 판단이 아니다. 규모가 크면 원이 멀리 퍼지고, 오래된 지진일수록 어두워진다. 글로 된 정보는
 * 지도에서 눌렀을 때 기상청 통보문 원문만 보여 준다(자연현상은 기상청 발표만 전달, docs/data-sources.md).
 */

/** 지구 반지름(km). 원의 크기를 지구 반지름 1 기준으로 바꿀 때 쓴다. */
const EARTH_RADIUS_KM = 6371
/** 규모를 모르는 통보(드물다)는 이 규모로 그린다. */
const UNKNOWN_MAGNITUDE = 3
/** 너무 많이 오면 앞(최신)부터 이만큼만 그린다. 메시 색인이 16비트라 상한이 필요하다(지진 하나에 289개 꼭짓점). */
export const MAX_QUAKES = 200
/** 지진 하나를 덮는 격자의 칸 수(가로·세로). 원이 곡면을 따라 휘므로 너무 성기면 가장자리에서 각이 보인다. */
const PATCH_CELLS = 16
/** 격자의 최소 반지름(rad, 약 190km). 작은 지진도 점이 화면 몇 픽셀은 되어야 해서 그 몫을 남긴다. */
const MIN_PATCH_RAD = 0.03

/**
 * 원이 가장 멀리 퍼지는 거리(km). 규모가 1 오를 때마다 2배: 규모 3 ≈ 160km, 5 ≈ 640km, 7 ≈ 2,560km.
 * 실제 진동이 전달되는 범위를 계산한 값이 아니다. 규모 차이가 눈에 보이게 하려는 표현 규칙이다.
 */
export function ringReachKm(magnitude: number | null): number {
  const m = magnitude ?? UNKNOWN_MAGNITUDE
  return Math.min(3000, Math.max(60, 40 * 2 ** (m - 1)))
}

/**
 * 셰이더 함수. GLSL ES 3.0(MapLibre)과 three.js ShaderMaterial(WebGL2) 양쪽에서 컴파일된다.
 *
 * d     진앙까지의 직선(현) 거리, 지구 반지름 1 기준. 작은 각에서는 각도(rad)와 거의 같다.
 *       acos로 각도를 구하지 않는 이유: 진앙 근처에서 미분이 무한대로 튀어 fwidth로 픽셀 크기를 잴 수 없다.
 * px    화면 1픽셀이 차지하는 같은 단위의 거리(fwidth(d)). 멀리서 볼 때도 점과 선이 최소 몇 픽셀은 되게 한다.
 * reach 원이 가장 멀리 퍼지는 거리(같은 단위).
 * age   진앙시로부터 지난 시간(시간 단위).
 * phase 지진마다 다른 박자. 여러 지진의 원이 동시에 퍼지지 않게 한다.
 * time  초.
 *
 * 결과는 더할 빛(rgb). 아래 화면에 더해 그린다.
 */
export const QUAKE_GLSL = /* glsl */ `
vec3 quakeLight(float d, float px, float reach, float age, float phase, float time) {
  // 오래될수록 어둡게: 방금 난 지진은 1, 3일(72시간) 지나면 0.35.
  float strength = mix(1.0, 0.35, smoothstep(0.0, 72.0, age));
  // 1시간 안에 난 지진은 원이 더 자주 퍼진다.
  float period = mix(2.2, 3.6, smoothstep(0.0, 1.0, age));
  // 진앙 점: 실제 크기(퍼지는 거리의 4%)를 쓰되 화면에서 4~8px 안으로 묶는다. 묶지 않으면 크게 확대했을 때
  // 점이 수백 px로 커져 지명을 덮었다(규모 5.1이면 반지름 약 27km, 2026-11-16).
  float dotR = clamp(reach * 0.04, 4.0 * px, 8.0 * px);
  float core = 1.0 - smoothstep(dotR * 0.3, dotR * 0.6, d);
  float halo = exp(-(d * d) / (dotR * dotR) * 1.6);
  // 세 개의 원이 번갈아 진앙에서 퍼져 나가며 옅어진다.
  // 원의 선 두께도 화면에서 1.3~3px 안으로 묶는다(확대하면 선이 띠처럼 두꺼워지지 않게).
  float width = clamp(reach * 0.022, 1.3 * px, 3.0 * px);
  float rings = 0.0;
  for (int k = 0; k < 3; k++) {
    float t = fract(time / period + phase + float(k) / 3.0);
    float offset = (d - t * reach) / width;
    rings += exp(-offset * offset) * pow(1.0 - t, 1.5) * smoothstep(0.0, 0.06, t);
  }
  vec3 warm = vec3(1.0, 0.42, 0.16);
  return (warm * (halo * 0.9 + rings * 0.9) + vec3(1.0, 0.93, 0.82) * core) * strength;
}
`

/** 격자 꼭짓점 하나. 각 라스가 자기 좌표계로 바꿔 쓴다. */
export interface QuakeVertex {
  lng: number
  lat: number
  /** 진앙 위경도. 셰이더가 조각마다 진앙까지 거리를 잰다. */
  centerLng: number
  centerLat: number
  /** 원이 퍼지는 거리(지구 반지름 1 기준). */
  reach: number
  ageHours: number
  phase: number
}

export interface QuakePatches {
  vertices: QuakeVertex[]
  indices: Uint16Array
}

/** id에서 0~1 박자를 뽑는다. 다시 받아도 같은 지진은 같은 박자로 퍼진다. */
function phaseOf(id: string): number {
  let hash = 0
  for (let i = 0; i < id.length; i++) hash = (hash * 31 + id.charCodeAt(i)) | 0
  return ((hash >>> 0) % 1000) / 1000
}

/**
 * 지진마다 원이 닿는 범위만 덮는 작은 위경도 격자를 만든다. 지구 전체를 덮어 그리면 지진이 몇 개 없어도 화면 모든 픽셀에서
 * 셰이더가 돌아(휴대폰 3배율 전체 화면이면 수백만 픽셀 × 지진 수) 배터리를 쓴다. 원 근처 픽셀만 그리게 한다.
 */
export function buildQuakePatches(quakes: Quake[], now: number): QuakePatches {
  const list = quakes.slice(0, MAX_QUAKES)
  const side = PATCH_CELLS + 1
  const vertices: QuakeVertex[] = []
  const indices = new Uint16Array(list.length * PATCH_CELLS * PATCH_CELLS * 6)
  let ii = 0
  list.forEach((quake, q) => {
    const reach = ringReachKm(quake.magnitude) / EARTH_RADIUS_KM
    const ageHours = Math.max(0, (now - Date.parse(quake.occurredAt)) / 3_600_000)
    const phase = phaseOf(quake.id)
    // 원 바깥 가장자리의 흐림까지 담도록 조금 넉넉하게.
    const radiusDeg = (Math.max(reach * 1.12, MIN_PATCH_RAD) * 180) / Math.PI
    const latMin = Math.max(-85, quake.latitude - radiusDeg)
    const latMax = Math.min(85, quake.latitude + radiusDeg)
    // 고위도일수록 같은 거리가 경도로는 넓다. 극 근처에서 무한히 커지지 않게 묶는다.
    const lngHalf = Math.min(180, radiusDeg / Math.max(Math.cos((quake.latitude * Math.PI) / 180), 0.1))
    for (let r = 0; r < side; r++) {
      const lat = latMin + ((latMax - latMin) * r) / PATCH_CELLS
      for (let c = 0; c < side; c++) {
        vertices.push({
          lng: quake.longitude - lngHalf + (2 * lngHalf * c) / PATCH_CELLS,
          lat,
          centerLng: quake.longitude,
          centerLat: quake.latitude,
          reach,
          ageHours,
          phase,
        })
      }
    }
    const base = q * side * side
    for (let r = 0; r < PATCH_CELLS; r++) {
      for (let c = 0; c < PATCH_CELLS; c++) {
        const a = base + r * side + c
        const b = a + side
        indices.set([a, b, a + 1, a + 1, b, b + 1], ii)
        ii += 6
      }
    }
  })
  return { vertices, indices }
}
