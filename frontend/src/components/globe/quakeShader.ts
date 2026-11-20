import type { Quake } from '../../types'

/**
 * 지진 표현: 진앙에 점을 찍고 그 둘레로 원이 퍼져 나간다. 홈 라스(HomeGlobe)와 지도(mapQuakeLayer)가 같은 규칙을 쓴다.
 *
 * <b>원은 화면 픽셀 크기다.</b> 처음에는 지표면 위의 실제 거리(규모 1당 2배, 규모 5.1이면 약 690km)로 그렸는데 두 가지가
 * 문제였다(2026-11-16).
 * - 실제 거리의 원은 '여기까지 흔들렸다'는 영향 범위로 읽힌다. 그 범위는 기상청이 발표한 것이 아니라 우리가 정한 값이라,
 *   자연현상은 공식 기관의 발표만 전달하고 판단을 붙이지 않는다는 원칙(docs/data-sources.md, D-18)에 어긋난다.
 * - 조각마다 진앙까지 거리를 계산하는 방식은 크게 확대하면 GPU 소수 정밀도가 모자라 줄무늬로 깨졌다.
 * 그래서 원은 '어디서 몇 규모가 났는지 알리는 표식'으로만 그린다. 규모가 크면 원이 더 크게 퍼지고, 오래된 지진일수록 어둡다.
 */

/**
 * 서버가 지진을 보여 주는 기간(application.yml earth.quakes.kma.display-hours)과 맞춘다. 밝기가 이 기간에 걸쳐 줄어
 * 사라질 무렵 가장 어둡다.
 */
const DISPLAY_HOURS = 24

/** 규모를 모르는 통보(드물다)는 이 규모로 그린다. */
const UNKNOWN_MAGNITUDE = 3

/** 지진 하나를 그리는 데 필요한 값. 크기는 화면 CSS 픽셀. */
export interface QuakeLook {
  /** 원이 가장 멀리 퍼지는 반지름(px). 규모 2 → 14px, 5 → 41px, 7 이상 → 59px. */
  reachPx: number
  /** 진앙 점의 반지름(px). 3~6px. */
  dotPx: number
  /** 밝기. 방금 난 지진 1, 표시 기간(24시간)이 끝날 때 0.35. */
  strength: number
  /** 원 하나가 퍼지는 데 걸리는 초. 1시간 안에 난 지진은 더 자주 퍼진다. */
  period: number
  /** 지진마다 다른 박자(0~1). 여러 지진의 원이 동시에 퍼지지 않게 한다. */
  phase: number
}

function smoothstep(edge0: number, edge1: number, x: number): number {
  const t = Math.min(1, Math.max(0, (x - edge0) / (edge1 - edge0)))
  return t * t * (3 - 2 * t)
}

/** id에서 0~1 박자를 뽑는다. 다시 받아도 같은 지진은 같은 박자로 퍼진다. */
function phaseOf(id: string): number {
  let hash = 0
  for (let i = 0; i < id.length; i++) hash = (hash * 31 + id.charCodeAt(i)) | 0
  return ((hash >>> 0) % 1000) / 1000
}

export function quakeLook(quake: Quake, now: number): QuakeLook {
  const m = quake.magnitude ?? UNKNOWN_MAGNITUDE
  const ageHours = Math.max(0, (now - Date.parse(quake.occurredAt)) / 3_600_000)
  return {
    reachPx: Math.min(59, Math.max(14, 14 + 9 * (m - 2))),
    dotPx: Math.min(6, Math.max(3, 3 + 0.6 * (m - 2))),
    strength: 1 - 0.65 * smoothstep(0, DISPLAY_HOURS, ageHours),
    period: 2.2 + 1.4 * smoothstep(0, 1, ageHours),
    phase: phaseOf(quake.id),
  }
}

/** 원 세 개가 퍼지는 시각 차이. 셰이더(홈)와 지도 레이어가 같이 쓴다. */
export const RING_COUNT = 3

/**
 * 퍼지는 원과 점의 색. 자홍(마젠타). 예전 주황(#ff6b29)은 화산(용암색 #ff3d00)·화재와 나란히 놓이면 구별되지 않았다(2026-11-20).
 * 플레어 분류 색(빨강·주황·하늘·보라·민트·노랑)과 겹치지 않는 남은 색상환 자리다.
 */
export const QUAKE_WARM = '#ff3fb4'
export const QUAKE_CORE = '#fff0f8'
