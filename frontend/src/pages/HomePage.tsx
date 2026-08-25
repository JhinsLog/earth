import { useEffect, useState } from 'react'
import MapGlobe from '../components/globe/MapGlobe'
import Navbar from '../components/layout/Navbar'
import EventDetailPanel from '../components/panel/EventDetailPanel'
import CreateEventModal from '../components/panel/CreateEventModal'
import { useEventStore } from '../store/eventStore'
import { useAuthStore } from '../store/authStore'
import { api } from '../lib/api'
import { detectApproximateLocation, type GeoPoint } from '../lib/geolocate'
import type { EarthUser } from '../types'
import './HomePage.css'

export default function HomePage() {
  const {
    events,
    selectedEventId,
    loadEvents,
    selectEvent,
    subscribeRealtime,
    addOrUpdate,
    removeEvent,
    pruneExpired,
  } = useEventStore()
  const { accessToken, setUser } = useAuthStore()

  const [placing, setPlacing] = useState(false)
  const [pendingLocation, setPendingLocation] = useState<{ lat: number; lng: number } | null>(null)
  const [initialFocusLatLng, setInitialFocusLatLng] = useState<GeoPoint | null>(null)

  useEffect(() => {
    loadEvents()
    const unsubscribe = subscribeRealtime()
    detectApproximateLocation().then(setInitialFocusLatLng)
    return () => {
      unsubscribe.then((fn) => fn())
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // 별은 TTL이 지나면 사라진다. 서버 스케줄러의 실시간 알림과 별개로, 클라이언트에서도
  // 만료 시각이 지난 별을 걷어낸다(브로드캐스트를 놓쳐도 화면에 남지 않도록).
  useEffect(() => {
    const id = window.setInterval(pruneExpired, 10_000)
    return () => window.clearInterval(id)
  }, [pruneExpired])

  useEffect(() => {
    if (!accessToken) {
      setUser(null)
      return
    }
    api.get<EarthUser>('/api/users/me').then(({ data }) => setUser(data))
  }, [accessToken, setUser])

  const selectedEvent = events.find((e) => e.id === selectedEventId) ?? null

  return (
    <div className="home">
      <Navbar
        placing={placing}
        onTogglePlacing={() => {
          setPlacing((v) => !v)
          selectEvent(null)
        }}
      />

      <MapGlobe
        events={events}
        selectedEventId={selectedEventId}
        onSelect={selectEvent}
        placing={placing}
        onPlaceLocation={(latitude, longitude) => {
          setPendingLocation({ lat: latitude, lng: longitude })
          setPlacing(false)
        }}
        pendingLocation={pendingLocation}
        initialFocusLatLng={initialFocusLatLng}
      />

      {selectedEvent && (
        <EventDetailPanel
          key={selectedEvent.id}
          event={selectedEvent}
          onClose={() => selectEvent(null)}
          onUpdated={addOrUpdate}
          onDeleted={removeEvent}
        />
      )}

      {pendingLocation && (
        <CreateEventModal
          latitude={pendingLocation.lat}
          longitude={pendingLocation.lng}
          onClose={() => setPendingLocation(null)}
          onCreated={(event) => {
            addOrUpdate(event)
            selectEvent(event.id)
          }}
        />
      )}
    </div>
  )
}
