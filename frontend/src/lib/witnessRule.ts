import { haversineDistanceKm } from './geo'
import type { GeoPoint, LocatedPoint } from './geolocate'
import type { EventCategory } from '../types'

/**
 * "그 사건을 볼 수 있는 위치에 있었는가" — 별을 등록·공감할 수 있는 거리 규칙.
 *
 * <p>이 서비스의 전제는 사건을 직접 보거나 겪은 사람이 별을 올린다는 것이다. 서울에 있으면서
 * 부산 화재를 등록하는 것은 그 전제를 벗어난다. 다만 "목격 가능한 거리"는 사건 종류마다
 * 다르다 — 큰 화재의 연기는 수 km 밖에서도 보이지만 접촉 사고는 바로 앞에 있어야 안다.
 *
 * <p><b>이 검사는 브라우저에서만 이뤄진다.</b> 서버는 사용자 위치를 받지도 저장하지도 않기로
 * 한 정책이라 여기서 검증할 좌표 자체가 서버에 없다. 따라서 이것은 보안 통제가 아니라
 * 제품 규범이며, API를 직접 호출하면 우회된다. 대량 남용은 서버의 시간당 등록 횟수 제한이
 * 막는다.
 */
export const WITNESS_RADIUS_KM: Record<EventCategory, number> = {
  /** 호우·우박·돌풍은 지역 단위 현상이라 넓게 잡는다. */
  WEATHER: 10,
  /** 연기와 화염은 멀리서도 목격된다. */
  FIRE: 5,
  /** 분류하기 어려운 사건. 기본값. */
  ETC: 2,
  /** 규모를 알려면 현장 근처여야 한다. */
  CROWD: 1.5,
  /** 직접 보거나 정체를 겪어야 안다. */
  ACCIDENT: 1,
}

/**
 * IP 기반 위치의 오차 반경(km) 가정.
 *
 * <p>IP 위치는 도시나 ISP 관문 단위라 실제와 수 km~수십 km 어긋난다. 브라우저가 오차를
 * 알려주지 않으므로 보수적인 값을 가정한다. 이 값보다 좁은 카테고리 반경은 의미가 없다.
 */
export const IP_ASSUMED_ACCURACY_KM = 25

/** 가장 넓은 카테고리 반경. 아직 카테고리를 모르는 시점(우클릭)의 1차 판정에 쓴다. */
export const MAX_WITNESS_RADIUS_KM = Math.max(...Object.values(WITNESS_RADIUS_KM))

/**
 * 이 위치에서 허용되는 최대 거리(km).
 *
 * <p>카테고리 반경은 "이 사건은 얼마나 멀리서 목격되는가"를, 오차 반경은 "그 판단을 할 만큼
 * 내 위치가 정확한가"를 담당한다. 오차 25km짜리 위치에 1km 규칙을 적용할 수는 없으므로
 * 둘 중 큰 값을 쓴다.
 */
export function allowedDistanceKm(from: LocatedPoint, category: EventCategory | null): number {
  const categoryRadius = category == null ? MAX_WITNESS_RADIUS_KM : WITNESS_RADIUS_KM[category]
  const accuracyRadius =
    from.source === 'ip'
      ? IP_ASSUMED_ACCURACY_KM
      : (from.accuracyMeters ?? 0) / 1000
  return Math.max(categoryRadius, accuracyRadius)
}

export interface WitnessCheck {
  ok: boolean
  distanceKm: number
  allowedKm: number
}

/** 내 위치에서 그 지점의 사건을 목격했다고 볼 수 있는 거리인지. */
export function checkWitnessRange(
  from: LocatedPoint,
  target: GeoPoint,
  category: EventCategory | null,
): WitnessCheck {
  const distanceKm = haversineDistanceKm(
    from.latitude,
    from.longitude,
    target.latitude,
    target.longitude,
  )
  const allowedKm = allowedDistanceKm(from, category)
  return { ok: distanceKm <= allowedKm, distanceKm, allowedKm }
}

/** 거리를 사람이 읽는 단위로. 1km 미만은 m로 보여준다. */
export function formatDistance(km: number): string {
  if (km < 1) return `${Math.round(km * 1000)}m`
  if (km < 10) return `${km.toFixed(1)}km`
  return `${Math.round(km)}km`
}
