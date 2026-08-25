export interface GeoPoint {
  latitude: number
  longitude: number
}

/** 브라우저 GPS(권한 필요) → IP 기반 위치 순으로 시도해 대략적인 접속 위치를 구한다. */
export async function detectApproximateLocation(): Promise<GeoPoint | null> {
  return (await tryBrowserGeolocation()) ?? (await tryIpGeolocation())
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
