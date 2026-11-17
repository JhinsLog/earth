import { useEffect, useState } from 'react'
import { API_BASE_URL } from '../lib/api'
import type { Quake } from '../types'

// 서버가 1분마다 기상청에서 받는다(응답 캐시 30초). 같은 간격으로 다시 묻는다.
const REFRESH_MS = 60 * 1000

/**
 * 최근 지진(기상청 통보 + 한반도 밖 USGS, 같은 지진이면 기상청). 꺼져 있으면(옵션의 '공공 정보') 묻지 않고 빈 목록이다.
 *
 * <p>지진은 장식이 아니라 정보지만, 받지 못해도 지구본은 정상이어야 한다. 실패하면 마지막으로 받은 목록을 그대로 둔다.
 * 로그인 토큰이 필요 없는 공개 자료라 api 클라이언트(토큰 갱신)를 거치지 않고 fetch로 받는다.
 */
export function useQuakes(enabled: boolean): Quake[] {
  const [quakes, setQuakes] = useState<Quake[]>([])

  useEffect(() => {
    if (!enabled) return
    const abort = new AbortController()
    const load = async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/api/globe/quakes`, { signal: abort.signal })
        if (!response.ok) return
        const body = (await response.json()) as { quakes?: Quake[] }
        if (Array.isArray(body.quakes)) setQuakes(body.quakes)
      } catch {
        // 네트워크 오류·취소: 다음 주기에 다시 묻는다.
      }
    }
    void load()
    const timer = window.setInterval(() => void load(), REFRESH_MS)
    return () => {
      abort.abort()
      window.clearInterval(timer)
    }
  }, [enabled])

  return enabled ? quakes : []
}
