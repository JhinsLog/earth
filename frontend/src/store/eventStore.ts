import { create } from 'zustand'
import { api } from '../lib/api'
import type { IMessage } from '@stomp/stompjs'
import { subscribeTopic } from '../lib/ws'
import type { EarthEvent } from '../types'

/** 지도 화면 범위. 경도는 MapLibre가 주는 그대로(감지 않은 값) 보내고, 정규화는 서버가 한다. */
export interface ViewportBounds {
  southLat: number
  northLat: number
  westLng: number
  eastLng: number
}

interface EventState {
  events: EarthEvent[]
  selectedEventId: number | null
  /**
   * 별을 선택할 때마다 증가하는 값.
   *
   * 이미 선택된 별을 다시 클릭하면 selectedEventId가 그대로라 지도의 확대 effect가
   * 재실행되지 않는다. 줌 아웃한 뒤 같은 별을 눌러도 다시 확대되지 않던 원인이다.
   * 선택 "사건"을 나타내는 값이 따로 있어야 매번 트리거된다.
   */
  selectionToken: number
  /** 범위 없이 부르면 전 세계 최신 별로 교체하고, 범위를 주면 그 안의 별을 기존 목록에 합친다. */
  loadEvents: (bounds?: ViewportBounds) => Promise<void>
  selectEvent: (id: number | null) => void
  /**
   * 실시간 별 피드를 구독한다. 로그인하면 전체 피드, 아니면 위치 전용 공개 피드 + 자기 접속 국가 피드.
   * 비로그인 연결은 서버가 그 둘 외의 구독을 버린다(TopicAccessPolicy).
   */
  subscribeRealtime: (viewer: { loggedIn: boolean; country: string | null }) => Promise<() => void>
  addOrUpdate: (event: EarthEvent) => void
  removeEvent: (id: number) => void
  /** 만료 시각이 지난 별을 목록에서 걷어낸다. 타이머로 주기 호출된다. */
  pruneExpired: () => void
}

export const useEventStore = create<EventState>((set, get) => ({
  events: [],
  selectedEventId: null,
  selectionToken: 0,

  loadEvents: async (bounds) => {
    const { data } = await api.get<EarthEvent[]>('/api/events', { params: bounds })
    if (!bounds) {
      set({ events: data })
      return
    }
    // 교체하지 않고 합친다. 교체하면 화면 밖으로 나간 별이 지워져, 줌아웃했을 때 방금까지
    // 보던 지역이 비어 보인다. 목록이 무한히 커지지 않는 건 pruneExpired가 만료된 별을 걷어내기 때문이다.
    const fetched = new Map(data.map((e) => [e.id, e]))
    const merged = get().events.map((e) => fetched.get(e.id) ?? e)
    const known = new Set(merged.map((e) => e.id))
    set({ events: [...data.filter((e) => !known.has(e.id)), ...merged] })
  },

  selectEvent: (id) =>
    set((state) => ({
      selectedEventId: id,
      // 선택 해제(null)는 확대할 대상이 없으므로 토큰을 올리지 않는다.
      selectionToken: id == null ? state.selectionToken : state.selectionToken + 1,
    })),

  addOrUpdate: (event) => {
    // 서버는 삭제·만료도 같은 채널로 흘려보낸다. ACTIVE가 아니면 지구본에서 걷어낸다.
    if (event.status !== 'ACTIVE') {
      get().removeEvent(event.id)
      return
    }
    const exists = get().events.some((e) => e.id === event.id)
    set({
      events: exists
        ? get().events.map((e) => (e.id === event.id ? event : e))
        : [event, ...get().events],
    })
  },

  removeEvent: (id) =>
    set((state) => ({
      events: state.events.filter((e) => e.id !== id),
      selectedEventId: state.selectedEventId === id ? null : state.selectedEventId,
    })),

  pruneExpired: () => {
    const now = Date.now()
    const alive = get().events.filter((e) => new Date(e.expiresAt).getTime() > now)
    if (alive.length !== get().events.length) {
      const goneSelected =
        get().selectedEventId !== null && !alive.some((e) => e.id === get().selectedEventId)
      set({ events: alive, selectedEventId: goneSelected ? null : get().selectedEventId })
    }
  },

  subscribeRealtime: async ({ loggedIn, country }) => {
    const apply = (message: IMessage) => get().addOrUpdate(JSON.parse(message.body) as EarthEvent)
    if (loggedIn) return subscribeTopic('/topic/events', apply)

    const unsubscribes = [
      await subscribeTopic('/topic/events.public', (message) => {
        const event = JSON.parse(message.body) as EarthEvent
        // 자기 나라 별은 같은 소식이 국가 피드로 상세와 함께 온다. 위치만 담긴 쪽이 늦게 도착해
        // 상세를 덮어쓰지 않도록 공개 피드의 것은 버린다.
        if (event.restricted && country !== null && event.countryCode === country) return
        get().addOrUpdate(event)
      }),
    ]
    if (country) unsubscribes.push(await subscribeTopic(`/topic/events.country.${country}`, apply))
    return () => unsubscribes.forEach((unsubscribe) => unsubscribe())
  },
}))
