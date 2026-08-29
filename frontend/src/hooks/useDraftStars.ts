import { useCallback, useEffect, useState } from 'react'
import { DRAFT_STAR_LIFETIME_MS } from '../components/globe/constants'
import type { DraftStar } from '../types'

/**
 * 우클릭으로 찍은 임시 별을 관리한다. 서버로 전송되지 않는 순수 클라이언트 상태라
 * 생성자 본인 화면에만 보이며, 내용을 입력해 등록하지 않으면 수명이 다해 사라진다.
 *
 * `now`를 함께 돌려주는 이유는 남은 시간을 화면에 표시하기 위함이다 — 만료 검사와
 * 같은 타이머를 쓰므로 별도 타이머를 하나 더 둘 필요가 없다.
 */
export function useDraftStars() {
  const [drafts, setDrafts] = useState<DraftStar[]>([])
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      const current = Date.now()
      setNow(current)
      setDrafts((prev) => {
        const alive = prev.filter((draft) => current - draft.createdAt < DRAFT_STAR_LIFETIME_MS)
        // 만료된 게 없으면 같은 배열을 그대로 돌려줘 불필요한 리렌더를 막는다.
        return alive.length === prev.length ? prev : alive
      })
    }, 1000)
    return () => window.clearInterval(intervalId)
  }, [])

  const addDraft = useCallback((latitude: number, longitude: number): DraftStar => {
    // 화면에 쓰는 now는 1초 타이머로만 갱신돼 이 시점엔 최대 1초 뒤처져 있다.
    // 그대로 두면 남은 시간이 수명보다 커져 5:00이어야 할 표시가 5:01로 시작한다.
    const now = Date.now()
    setNow(now)
    const draft: DraftStar = {
      id: `draft-${now.toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
      latitude,
      longitude,
      createdAt: now,
    }
    setDrafts((prev) => [...prev, draft])
    return draft
  }, [])

  const removeDraft = useCallback((id: string) => {
    setDrafts((prev) => prev.filter((draft) => draft.id !== id))
  }, [])

  /** 가장 먼저 사라질 임시 별의 남은 시간(ms). 임시 별이 없으면 null. */
  const soonestRemainingMs =
    drafts.length === 0
      ? null
      : Math.max(
          0,
          // 남은 시간은 수명을 넘을 수 없다. now가 뒤처진 순간에도 표시가 튀지 않도록 묶어둔다.
          Math.min(
            DRAFT_STAR_LIFETIME_MS,
            ...drafts.map((draft) => draft.createdAt + DRAFT_STAR_LIFETIME_MS - now),
          ),
        )

  return { drafts, addDraft, removeDraft, soonestRemainingMs }
}
