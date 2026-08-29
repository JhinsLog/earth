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
 * IP 기반 위치에 적용할 하한 반경(km).
 *
 * <p>IP 위치의 <b>실제</b> 오차는 도시·ISP 관문 단위라 수 km에서 수십 km에 이른다. 그 값을
 * 그대로 하한으로 쓰면(25km) 서울 전역에서 아무 사건이나 등록할 수 있게 되어 "직접 보거나
 * 겪은 사람이 올린다"는 전제가 무너진다.
 *
 * <p>그래서 실제 오차보다 <b>의도적으로 좁게</b> 잡았다. 대가로 현장에 있는데도 IP 중심점이
 * 멀리 잡혀 막히는 사용자가 생긴다. 그 경우를 막다른 길로 두지 않기 위해, 차단 화면에서
 * GPS로 다시 확인할 수 있는 길을 함께 제공한다(정확한 위치를 얻으면 오차가 수십 m로 줄어
 * 카테고리 반경이 그대로 적용된다).
 */
export const IP_ASSUMED_ACCURACY_KM = 5

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
