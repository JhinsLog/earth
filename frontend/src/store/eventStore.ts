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
    const exists = get().events.some((e) => e.id === event.id)
    set({
      events: exists
        ? get().events.map((e) => (e.id === event.id ? event : e))
        : [event, ...get().events],
    })
  },

  subscribeRealtime: async () => {
    return subscribeTopic('/topic/events', (message) => {
      const event = JSON.parse(message.body) as EarthEvent
      get().addOrUpdate(event)
    })
  },
}))
