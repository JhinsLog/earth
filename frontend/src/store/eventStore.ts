import { create } from 'zustand'
import { api } from '../lib/api'
import { subscribeTopic } from '../lib/ws'
import type { EarthEvent } from '../types'

interface EventState {
  events: EarthEvent[]
  selectedEventId: number | null
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

  loadEvents: async () => {
    const { data } = await api.get<EarthEvent[]>('/api/events')
    set({ events: data })
  },

  selectEvent: (id) => set({ selectedEventId: id }),

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
