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
  const { events, selectedEventId, loadEvents, selectEvent, subscribeRealtime, addOrUpdate } = useEventStore()
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
        initialFocusLatLng={initialFocusLatLng}
      />

      {selectedEvent && <EventDetailPanel event={selectedEvent} onClose={() => selectEvent(null)} />}

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
