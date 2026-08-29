import { useEffect, useState } from 'react'
import MapGlobe from '../components/globe/MapGlobe'
import Navbar from '../components/layout/Navbar'
import EventDetailPanel from '../components/panel/EventDetailPanel'
import CreateEventModal from '../components/panel/CreateEventModal'
import { useEventStore } from '../store/eventStore'
import { useAuthStore } from '../store/authStore'
import { useDraftStars } from '../hooks/useDraftStars'
import { api } from '../lib/api'
import { detectApproximateLocation, type GeoPoint } from '../lib/geolocate'
import type { EarthUser } from '../types'
import './HomePage.css'

/** 남은 시간을 m:ss 형태로. */
function formatRemaining(ms: number | null): string {
  if (ms == null) return ''
  const totalSeconds = Math.ceil(ms / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

export default function HomePage() {
  const { events, selectedEventId, loadEvents, selectEvent, subscribeRealtime, addOrUpdate, pruneExpired } =
    useEventStore()
  const { accessToken, user, setUser } = useAuthStore()
  const { drafts, addDraft, removeDraft, soonestRemainingMs } = useDraftStars()

  const [selectedDraftId, setSelectedDraftId] = useState<string | null>(null)
  const [initialFocusLatLng, setInitialFocusLatLng] = useState<GeoPoint | null>(null)
  const [showLoginHint, setShowLoginHint] = useState(false)
  // 값이 바뀔 때마다 지구본이 첫 화면으로 되돌아간다. 같은 위치에서 여러 번 눌러도
  // 매번 동작해야 하므로 boolean이 아니라 증가하는 카운터를 쓴다.
  const [resetViewToken, setResetViewToken] = useState(0)

  // 만료 전파는 WebSocket으로 오지만, 연결이 끊겼다 붙는 사이의 메시지는 재생되지
  // 않는다(STOMP는 재연결만 하고 놓친 메시지를 다시 주지 않는다). 그 구간에 수명이
  // 다한 별이 화면에 영영 남지 않도록 클라이언트에서도 주기적으로 걷어낸다.
  useEffect(() => {
    const id = window.setInterval(pruneExpired, 1000)
    return () => window.clearInterval(id)
  }, [pruneExpired])

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
  // 수명이 다해 목록에서 빠지면 selectedDraft도 자연히 null이 되어 모달이 닫힌다.
  const selectedDraft = drafts.find((d) => d.id === selectedDraftId) ?? null

  const handleDraftCreate = (latitude: number, longitude: number) => {
    if (!user) {
      setShowLoginHint(true)
      window.setTimeout(() => setShowLoginHint(false), 3000)
      return
    }
    addDraft(latitude, longitude)
  }

  return (
    <div className={`home${selectedEvent ? ' home--panel-open' : ''}`}>
      <Navbar
        onBrandClick={() => {
          // 열려 있던 상세/입력창을 모두 닫고 지구본 전체 화면으로 되돌린다.
          selectEvent(null)
          setSelectedDraftId(null)
          setResetViewToken((token) => token + 1)
        }}
      />

      <MapGlobe
        events={events}
        selectedEventId={selectedEventId}
        onSelect={selectEvent}
        draftStars={drafts}
        onDraftCreate={handleDraftCreate}
        onDraftSelect={(id) => {
          selectEvent(null)
          setSelectedDraftId(id)
        }}
        onDraftRemove={(id) => {
          removeDraft(id)
          setSelectedDraftId((current) => (current === id ? null : current))
        }}
        initialFocusLatLng={initialFocusLatLng}
        resetViewToken={resetViewToken}
      />

      {showLoginHint && (
        <div className="home__hint home__hint--warn">별을 남기려면 로그인이 필요합니다</div>
      )}

      {!showLoginHint && user && (
        <div className="home__hint">
          {drafts.length === 0 ? (
            <>지구본을 <strong>우클릭</strong>해 별을 남겨보세요</>
          ) : (
            <>
              임시 별 {drafts.length}개 · 클릭해 내용 입력, <strong>우클릭</strong>하면 삭제
              <span className="home__hint-timer">{formatRemaining(soonestRemainingMs)} 후 사라짐</span>
            </>
          )}
        </div>
      )}

      {selectedEvent && <EventDetailPanel event={selectedEvent} onClose={() => selectEvent(null)} />}

      {selectedDraft && (
        <CreateEventModal
          latitude={selectedDraft.latitude}
          longitude={selectedDraft.longitude}
          onClose={() => setSelectedDraftId(null)}
          onDelete={() => {
            removeDraft(selectedDraft.id)
            setSelectedDraftId(null)
          }}
          onCreated={(event) => {
            // 서버에 등록됐으니 임시 별은 걷어내고 실제 별로 대체한다.
            removeDraft(selectedDraft.id)
            setSelectedDraftId(null)
            addOrUpdate(event)
            selectEvent(event.id)
          }}
        />
      )}
    </div>
  )
}
