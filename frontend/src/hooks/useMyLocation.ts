import { useCallback, useState } from 'react'
import { detectPreciseLocation, type LocatedPoint } from '../lib/geolocate'

/**
 * 내 위치를 구해 들고 있는다.
 *
 * <p><b>이 좌표는 서버로 전송되지 않는다.</b> 쓰임새는 두 가지뿐이다 — 지구본 카메라를
 * 내 위치로 옮기는 것, 그리고 "직접 보거나 겪은 사건만 등록한다"는 규칙을 브라우저 안에서
 * 판정하는 것. 별을 등록할 때 서버로 가는 것은 <i>사건의 좌표</i>이지 내 위치가 아니다.
 *
 * <p>스토어(zustand)가 아니라 훅으로 둔 이유도 같다. 전역 스토어에 올려두면 다른 코드가
 * 무심코 API 요청에 실어 보낼 여지가 생긴다. 필요한 화면에만 props로 내려보낸다.
 */
export function useMyLocation() {
  const [myLocation, setMyLocation] = useState<LocatedPoint | null>(null)
  const [locating, setLocating] = useState(false)
  const [locateError, setLocateError] = useState<string | null>(null)

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
