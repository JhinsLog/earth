export interface GeoPoint {
  latitude: number
  longitude: number
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
  return firstResolved([tryBrowserGeolocation(), tryIpGeolocation()])
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

function tryBrowserGeolocation(): Promise<GeoPoint | null> {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      resolve(null)
      return
    }
    let settled = false
    const finish = (result: GeoPoint | null) => {
      if (settled) return
      settled = true
      resolve(result)
    }
    const timeoutId = setTimeout(() => finish(null), 5000)

    navigator.geolocation.getCurrentPosition(
      (position) => {
        clearTimeout(timeoutId)
        finish({ latitude: position.coords.latitude, longitude: position.coords.longitude })
      },
      () => {
        clearTimeout(timeoutId)
        finish(null)
      },
      { timeout: 4500, maximumAge: 5 * 60 * 1000 },
    )
  })
}

const IP_GEOLOCATION_ENDPOINTS = ['https://ipwho.is/', 'https://get.geojs.io/v1/ip/geo.json']

async function tryIpGeolocation(): Promise<GeoPoint | null> {
  for (const endpoint of IP_GEOLOCATION_ENDPOINTS) {
    try {
      const response = await fetch(endpoint)
      if (!response.ok) continue
      const data = await response.json()
      const latitude = Number(data.latitude)
      const longitude = Number(data.longitude)
      if (Number.isFinite(latitude) && Number.isFinite(longitude)) {
        return { latitude, longitude }
      }
    } catch {
      // 다음 엔드포인트로 폴백
    }
  }
  return null
}
