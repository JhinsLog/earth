export interface GeoPoint {
  latitude: number
  longitude: number
}

/** 위치를 어떻게 얻었는지. 정확도가 자릿수 단위로 다르므로 화면에서 구분해 다뤄야 한다. */
export type LocationSource = 'gps' | 'ip'

export interface LocatedPoint extends GeoPoint {
  source: LocationSource
  /** GPS가 보고한 오차 반경(m). IP 기반이면 알 수 없어 null. */
  accuracyMeters: number | null
}

/**
 * 표준시 오프셋만으로 접속 지역의 대략적인 경도를 즉시 구한다.
 *
 * <p>네트워크도 권한도 필요 없어 첫 프레임부터 쓸 수 있다는 것이 핵심이다.
 * IP/GPS 조회는 아무리 빨라도 수백 ms가 걸리는데, 그동안 지구본이 엉뚱한 대륙을
 * 보고 있으면 "기본 화면이 항상 아프리카"처럼 느껴진다.
 * 지구는 1시간에 15도씩 돌므로 UTC+9(한국)이면 135도E — 실제 127도E와 가깝고,
 * 줌 1.5의 지구본 화면에서는 대륙을 맞히는 데 충분한 정확도다.
 */
export function approximateLongitudeFromTimezone(): number {
  const offsetMinutes = -new Date().getTimezoneOffset()
  const longitude = (offsetMinutes / 60) * 15
  if (!Number.isFinite(longitude)) return 0
  return Math.max(-180, Math.min(180, longitude))
}

/**
 * 브라우저 GPS(권한 필요)와 IP 기반 위치를 <b>동시에</b> 던져 먼저 오는 답을 쓴다.
 *
 * <p>순차로 시도하면 사용자가 위치 권한 창을 그냥 두었을 때 타임아웃 4.5초를 통째로
 * 기다린 뒤에야 IP 조회가 시작된다. 실제로 IP 조회는 0.5초면 끝나므로, 그 동안
 * 초기 화면이 움직이지 않는 것이 접속 위치 반영이 안 되는 것처럼 보이는 원인이었다.
 * 줌 4(국가 단위)에서는 IP 수준의 정확도로 충분하다.
 */
export async function detectApproximateLocation(): Promise<GeoPoint | null> {
  return firstResolved([tryBrowserGeolocation(), detectIpLocation()])
}

/**
 * "내 위치로 이동" 버튼이 쓸 위치를 구한다. GPS를 먼저 기다리고, 실패하면 IP로 물러선다.
 *
 * <p>detectApproximateLocation()과 달리 <b>경쟁시키지 않는다</b>. 초기 화면(줌 4)은 대륙만
 * 맞으면 되지만 이 버튼은 거리 수준까지 확대하므로, 먼저 도착했다는 이유로 IP 값을 쓰면
 * 도시/ISP 단위 오차를 street level로 확대해 엉뚱한 골목을 "내 위치"라고 단언하게 된다.
 * 정확도가 화면에 그대로 드러나는 자리이므로 GPS 응답을 끝까지 기다린다.
 *
 * <p>여기서 얻은 좌표는 카메라를 움직이는 데에만 쓰이며 서버로 전송되지 않는다.
 */
export async function detectPreciseLocation(): Promise<LocatedPoint | null> {
  const gps = await tryBrowserGeolocation({ highAccuracy: true, timeoutMs: 10000 })
  if (gps) return gps

  const ip = await detectIpLocation()
  return ip ? { ...ip, source: 'ip', accuracyMeters: null } : null
}

/** 여러 후보 중 가장 먼저 성공한 값을 반환한다. 전부 실패하면 null. */
function firstResolved(candidates: Array<Promise<GeoPoint | null>>): Promise<GeoPoint | null> {
  return new Promise((resolve) => {
    let remaining = candidates.length
    let settled = false

    const fail = () => {
      if (!settled && --remaining === 0) resolve(null)
    }

    for (const candidate of candidates) {
      candidate
        .then((result) => {
          if (settled) return
          if (result) {
            settled = true
            resolve(result)
          } else {
            fail()
          }
        })
        .catch(fail)
    }
  })
}


interface BrowserGeolocationOptions {
  /** 기지국·와이파이 대신 GPS 하드웨어를 쓰게 한다. 느리지만 오차가 수십 m로 줄어든다. */
  highAccuracy: boolean
  timeoutMs: number
}

function tryBrowserGeolocation(
  options: BrowserGeolocationOptions = { highAccuracy: false, timeoutMs: 4500 },
): Promise<LocatedPoint | null> {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      resolve(null)
      return
    }
    let settled = false
    const finish = (result: LocatedPoint | null) => {
      if (settled) return
      settled = true
      resolve(result)
    }
    // 콜백이 끝내 오지 않는 브라우저가 있어 바깥에서도 한 번 더 끊는다.
    const timeoutId = setTimeout(() => finish(null), options.timeoutMs + 500)

    navigator.geolocation.getCurrentPosition(
      (position) => {
        clearTimeout(timeoutId)
        finish({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          source: 'gps',
          accuracyMeters: Number.isFinite(position.coords.accuracy)
            ? position.coords.accuracy
            : null,
        })
      },
      () => {
        clearTimeout(timeoutId)
        finish(null)
      },
      {
        enableHighAccuracy: options.highAccuracy,
        timeout: options.timeoutMs,
        maximumAge: 5 * 60 * 1000,
      },
    )
  })
}

let sharedIpLookup: Promise<GeoPoint | null> | null = null

/**
 * IP 기반 대략 위치. <b>권한도 사용자 동작도 필요 없다</b>는 것이 핵심이다.
 *
 * <p>GPS는 권한 창을 띄워야 하고 사용자가 응답할 때까지 아무것도 알 수 없다. 반면 IP는
 * 페이지가 열리자마자 조용히 받아둘 수 있어, 거리 규칙이 판정할 기준선을 항상 확보해 준다.
 * GPS를 얻으면 그때 더 정확한 값으로 올린다.
 *
 * <p>초기 화면과 거리 규칙이 동시에 부르므로 요청을 공유한다. 실패는 캐시하지 않는다 —
 * 일시적인 네트워크 문제일 수 있어 다음 호출에서 다시 시도해야 한다.
 */
export function detectIpLocation(): Promise<GeoPoint | null> {
  if (!sharedIpLookup) {
    sharedIpLookup = tryIpGeolocation().then((result) => {
      if (!result) sharedIpLookup = null
      return result
    })
  }
  return sharedIpLookup
}

const IP_GEOLOCATION_ENDPOINTS = ['https://ipwho.is/', 'https://get.geojs.io/v1/ip/geo.json']

/** 응답이 지역을 얼마나 좁혀 말했는지. 클수록 구체적이다. */
const PRECISION_DISTRICT = 2
const PRECISION_CITY = 1
const PRECISION_UNKNOWN = 0

/** 한 제공자가 응답을 늦게 주더라도 나머지로 진행할 수 있게 끊는다. */
const IP_LOOKUP_TIMEOUT_MS = 4000

interface ScoredLocation extends GeoPoint {
  precision: number
}

/**
 * 응답의 지역 이름이 얼마나 구체적인지 점수화한다.
 *
 * <p>핵심은 <b>city가 region(시/도)과 같은 값이면 그 제공자는 구 단위를 모른다</b>는 것이다.
 * 그때 돌려주는 좌표는 실제 위치가 아니라 도시 중심값이라 크게 어긋난다. 실측에서 같은 IP에
 * 대해 city를 "Seoul"로 준 쪽은 10.8km, "Gangseo-gu"로 준 쪽은 0.9km 오차였다.
 *
 * <p>그래서 응답 순서가 아니라 이 점수로 고른다. 어느 제공자가 더 낫다고 고정하면 다른
 * 지역·통신사에서 반대가 될 수 있지만, "지역명을 구체적으로 말한 쪽이 실제로 더 정확하다"는
 * 관계는 제공자와 무관하게 성립한다.
 */
function scorePrecision(data: { city?: unknown; region?: unknown }): number {
  const city = typeof data.city === 'string' ? data.city.trim() : ''
  const region = typeof data.region === 'string' ? data.region.trim() : ''
  if (!city) return PRECISION_UNKNOWN
  // 대소문자만 다른 경우도 같은 값으로 본다.
  if (region && city.toLowerCase() === region.toLowerCase()) return PRECISION_CITY
  return PRECISION_DISTRICT
}

async function fetchIpLocation(endpoint: string): Promise<ScoredLocation | null> {
  try {
    const response = await fetch(endpoint, { signal: AbortSignal.timeout(IP_LOOKUP_TIMEOUT_MS) })
    if (!response.ok) return null
    const data = await response.json()
    const latitude = Number(data.latitude)
    const longitude = Number(data.longitude)
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null
    return { latitude, longitude, precision: scorePrecision(data) }
  } catch {
    return null
  }
}

/**
 * 여러 제공자에게 동시에 물어보고 <b>지역을 가장 구체적으로 특정한</b> 응답을 쓴다.
 *
 * <p>순차로 돌며 첫 성공을 쓰면 목록 앞에 있는 제공자가 항상 이긴다. 그 제공자가 도시
 * 중심값만 주는 경우 실제 위치와 10km 넘게 어긋나고, 목격 거리 규칙에서 현장에 있는
 * 사용자가 차단된다.
 */
async function tryIpGeolocation(): Promise<GeoPoint | null> {
  const results = await Promise.all(IP_GEOLOCATION_ENDPOINTS.map(fetchIpLocation))
  const usable = results.filter((r): r is ScoredLocation => r != null)
  if (usable.length === 0) return null

  // 점수가 같으면 먼저 선언된 제공자를 쓴다(reduce가 기존 값을 유지하므로 자연히 그렇게 된다).
  const best = usable.reduce((a, b) => (b.precision > a.precision ? b : a))
  return { latitude: best.latitude, longitude: best.longitude }
}
