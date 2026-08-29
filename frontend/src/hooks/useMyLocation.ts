import { useCallback, useEffect, useState } from 'react'
import { detectIpLocation, detectPreciseLocation, type LocatedPoint } from '../lib/geolocate'

/**
 * 내 위치를 두 단계로 들고 있는다.
 *
 * <p><b>1단계 — IP 기준선.</b> 페이지가 열리면 권한 창 없이 조용히 받아둔다. GPS는 사용자가
 * 권한에 응답할 때까지 아무것도 알 수 없는데, 그 사이에도 "직접 보거나 겪은 사건만 등록한다"는
 * 규칙은 판정할 기준이 있어야 한다. 특히 GPS가 없는 데스크톱 브라우저에서는 이것이 유일한
 * 위치 정보다. 오차가 크므로 규칙도 그에 맞춰 느슨하게(반경 25km) 적용된다.
 *
 * <p><b>2단계 — GPS 승격.</b> "내 위치" 버튼을 누르면 GPS를 요청해 더 정확한 값으로 올린다.
 * 한 번 GPS를 얻은 뒤에는 IP로 되돌리지 않는다 — 정밀도를 스스로 떨어뜨리는 셈이 된다.
 *
 * <p><b>이 좌표는 서버로 전송되지 않는다.</b> 쓰임새는 지구본 카메라를 옮기는 것과, 거리
 * 규칙을 브라우저 안에서 판정하는 것뿐이다. 별을 등록할 때 서버로 가는 것은 <i>사건의
 * 좌표</i>이지 내 위치가 아니다. 전역 스토어가 아니라 훅으로 둔 이유도 같다 — 스토어에
 * 올려두면 다른 코드가 무심코 API 요청에 실어 보낼 여지가 생긴다.
 */
export function useMyLocation() {
  const [myLocation, setMyLocation] = useState<LocatedPoint | null>(null)
  const [locating, setLocating] = useState(false)
  const [locateError, setLocateError] = useState<string | null>(null)

  // 1단계: IP 기준선. 권한이 필요 없으므로 사용자를 기다리지 않는다.
  useEffect(() => {
    let cancelled = false
    detectIpLocation().then((ip) => {
      if (cancelled || !ip) return
      setMyLocation((current) =>
        // 이미 GPS를 얻었다면 덮어쓰지 않는다.
        current?.source === 'gps' ? current : { ...ip, source: 'ip', accuracyMeters: null },
      )
    })
    return () => {
      cancelled = true
    }
  }, [])

  // 2단계: GPS 승격. 사용자가 명시적으로 요청했을 때만.
  const locate = useCallback(async (): Promise<LocatedPoint | null> => {
    setLocating(true)
    setLocateError(null)
    try {
      const point = await detectPreciseLocation()
      if (!point) {
        setLocateError('위치를 확인할 수 없습니다. 브라우저 위치 권한을 확인해 주세요.')
        return null
      }
      setMyLocation(point)
      return point
    } finally {
      setLocating(false)
    }
  }, [])

  return { myLocation, locating, locateError, locate }
}
