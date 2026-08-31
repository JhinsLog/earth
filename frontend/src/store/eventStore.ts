import { create } from 'zustand'
import { api } from '../lib/api'
import { subscribeTopic } from '../lib/ws'
import type { EarthEvent } from '../types'

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
  loadEvents: () => Promise<void>
  selectEvent: (id: number | null) => void
  subscribeRealtime: () => Promise<() => void>
  addOrUpdate: (event: EarthEvent) => void
  removeEvent: (id: number) => void
  /** 만료 시각이 지난 별을 목록에서 걷어낸다. 타이머로 주기 호출된다. */
  pruneExpired: () => void
}

export const useEventStore = create<EventState>((set, get) => ({
  events: [],
  selectedEventId: null,
  selectionToken: 0,

  loadEvents: async () => {
    const { data } = await api.get<EarthEvent[]>('/api/events')
    set({ events: data })
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

  subscribeRealtime: async () => {
    return subscribeTopic('/topic/events', (message) => {
      const event = JSON.parse(message.body) as EarthEvent
      get().addOrUpdate(event)
    })
  },
}))
