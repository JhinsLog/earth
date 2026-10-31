import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import HomeGlobe, { type HandoffView } from '../components/globe/HomeGlobe'
import { GLOBE_TRANSITION_MS } from '../components/globe/constants'
import Navbar from '../components/layout/Navbar'
import EventDetailPanel from '../components/panel/EventDetailPanel'
import CreateEventModal from '../components/panel/CreateEventModal'
import { useEventStore } from '../store/eventStore'
import { useAuthStore } from '../store/authStore'
import { useDraftStars } from '../hooks/useDraftStars'
import { useMyLocation } from '../hooks/useMyLocation'
import { api } from '../lib/api'
import { approximateLongitudeFromTimezone, detectApproximateLocation, type GeoPoint } from '../lib/geolocate'
import type { EarthUser } from '../types'
import './HomePage.css'

// 첫 화면은 홈 지구본(three)이고 지도(MapLibre, 번들의 큰 몫)는 지구를 클릭해 들어갈 때만 필요하다.
// 따로 떼어 내 첫 로딩에서 빼고, 들어갈 때 내려받는다.
const MapGlobe = lazy(() => import('../components/globe/MapGlobe'))

/** 남은 시간을 m:ss 형태로. */
function formatRemaining(ms: number | null): string {
  if (ms == null) return ''
  const totalSeconds = Math.ceil(ms / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

export default function HomePage() {
  const {
    events,
    selectedEventId,
    selectionToken,
    loadEvents,
    selectEvent,
    subscribeRealtime,
    addOrUpdate,
    pruneExpired,
  } = useEventStore()
  const { accessToken, user, setUser } = useAuthStore()
  const { drafts, addDraft, removeDraft, soonestRemainingMs } = useDraftStars()
  // 내 위치는 여기서만 소유하고 필요한 두 곳에만 내려보낸다. 서버로는 나가지 않는다.
  const { myLocation, locating, locateError, locate } = useMyLocation()

  const [selectedDraftId, setSelectedDraftId] = useState<string | null>(null)
  const [initialFocusLatLng, setInitialFocusLatLng] = useState<GeoPoint | null>(null)
  const [loginHint, setLoginHint] = useState<string | null>(null)
  const loginHintTimerRef = useRef<number | undefined>(undefined)
  const flashLoginHint = (message: string) => {
    window.clearTimeout(loginHintTimerRef.current)
    setLoginHint(message)
    loginHintTimerRef.current = window.setTimeout(() => setLoginHint(null), 3000)
  }

  // 첫 화면은 감상용 3D 지구본(HomeGlobe), 클릭하면 탐색용 지도(MapGlobe)로 들어간다.
  // 로그인하고 돌아온 경우(OAuthRedirectPage가 state.enter를 붙인다)는 지구본을 건너뛴다.
  const location = useLocation()
  const [view, setView] = useState<'globe' | 'map'>(
    (location.state as { enter?: boolean } | null)?.enter ? 'map' : 'globe',
  )
  // 교차 페이드 동안 사라지는 쪽도 잠깐 남겨 둔다. 그 뒤 언마운트해 WebGL 컨텍스트를 하나만 유지한다.
  const [leaving, setLeaving] = useState<'globe' | 'map' | null>(null)
  const leavingTimerRef = useRef<number | undefined>(undefined)
  /** 지구본에서 클릭한 지점. 지도는 그곳으로 날아가며 시작한다. */
  const [enterPoint, setEnterPoint] = useState<GeoPoint | null>(null)
  /** 지구본을 확대해 들어왔을 때 지도가 이어받을 화면. 있으면 지도는 어디로도 날아가지 않고 그 자리에서 시작한다. */
  const [enterView, setEnterView] = useState<HandoffView | null>(null)
  const [globeCenter] = useState<GeoPoint>(() => ({ latitude: 20, longitude: approximateLongitudeFromTimezone() }))

  // 지도로 들어갈 때는 지도가 준비될 때까지(타일 도착) 지구본을 그대로 두고 지도는 숨겨 둔다.
  // 바로 겹쳐 바꾸면 그 사이 빈 우주만 보이는 순간이 생긴다(지도는 준비 전엔 투명하다).
  const [waitingForMap, setWaitingForMap] = useState(false)
  // 지도의 준비 알림은 등록 시점의 콜백으로 오므로 최신 대기 여부는 ref로 읽는다.
  const waitingForMapRef = useRef(false)
  const setWaiting = (waiting: boolean) => {
    waitingForMapRef.current = waiting
    setWaitingForMap(waiting)
  }
  const handleMapReady = () => {
    if (!waitingForMapRef.current) return
    setWaiting(false)
    window.clearTimeout(leavingTimerRef.current)
    leavingTimerRef.current = window.setTimeout(() => setLeaving(null), GLOBE_TRANSITION_MS)
  }

  const switchView = (next: 'globe' | 'map') => {
    if (next === view) return
    window.clearTimeout(leavingTimerRef.current)
    setLeaving(view)
    if (next === 'map') {
      setWaiting(true)
      setView(next)
      return
    }
    setWaiting(false)
    setView(next)
    leavingTimerRef.current = window.setTimeout(() => setLeaving(null), GLOBE_TRANSITION_MS)
  }
  useEffect(() => () => window.clearTimeout(leavingTimerRef.current), [])

  // 비로그인도 그대로 들어간다. 다른 나라 별은 서버가 위치만 내려 주고 상세 요청은 거부하므로(restricted, 403),
  // 입구에서 로그인을 물을 이유가 없다. 로그인은 오른쪽 위 버튼으로 한다(D-8: 제한은 상세만).
  const handleGlobeClick = (point: GeoPoint) => {
    setEnterView(null)
    setEnterPoint(point)
    switchView('map')
  }

  const handleGlobeZoomEnter = (view: HandoffView) => {
    setEnterView(view)
    setEnterPoint(null)
    switchView('map')
  }

  const exitToGlobe = () => {
    selectEvent(null)
    setSelectedDraftId(null)
    switchView('globe')
  }

  // 만료 전파는 WebSocket으로 오지만, 연결이 끊겼다 붙는 사이의 메시지는 재생되지
  // 않는다(STOMP는 재연결만 하고 놓친 메시지를 다시 주지 않는다). 그 구간에 수명이
  // 다한 별이 화면에 영영 남지 않도록 클라이언트에서도 주기적으로 걷어낸다.
  useEffect(() => {
    const id = window.setInterval(pruneExpired, 1000)
    return () => window.clearInterval(id)
  }, [pruneExpired])

  useEffect(() => {
    detectApproximateLocation().then(setInitialFocusLatLng)
  }, [])

  // 로그인 여부에 따라 받을 수 있는 범위가 다르다(비로그인은 접속 국가의 별만 상세).
  // 로그인·로그아웃하면 목록을 다시 받고 실시간 구독도 그 범위에 맞게 다시 맺는다.
  useEffect(() => {
    let cancelled = false
    let unsubscribe: (() => void) | null = null
    loadEvents()
    const viewerCountry = accessToken
      ? Promise.resolve(null)
      : api
          .get<{ countryCode: string | null }>('/api/geo/me')
          .then(({ data }) => data.countryCode)
          .catch(() => null)
    viewerCountry
      .then((country) => subscribeRealtime({ loggedIn: !!accessToken, country }))
      .then((fn) => {
        if (cancelled) fn()
        else unsubscribe = fn
      })
    return () => {
      cancelled = true
      unsubscribe?.()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken])

  useEffect(() => {
    if (!accessToken) {
      setUser(null)
      return
    }
    api.get<EarthUser>('/api/users/me').then(({ data }) => setUser(data))
  }, [accessToken, setUser])

  const found = events.find((e) => e.id === selectedEventId)
  const selectedEvent = found && !found.restricted ? found : null

  const handleSelect = (id: number | null) => {
    const target = id == null ? null : events.find((e) => e.id === id)
    // 위치만 아는 별은 서버가 상세를 주지 않는다(403). 열지 않고 이유를 알려 준다.
    if (target?.restricted) {
      flashLoginHint('로그인하면 다른 나라의 별도 자세히 볼 수 있습니다')
      return
    }
    selectEvent(id)
  }
  // 수명이 다해 목록에서 빠지면 selectedDraft도 자연히 null이 되어 모달이 닫힌다.
  const selectedDraft = drafts.find((d) => d.id === selectedDraftId) ?? null

  const handleDraftCreate = (latitude: number, longitude: number) => {
    if (!user) {
      flashLoginHint('별을 남기려면 로그인이 필요합니다')
      return
    }
    addDraft(latitude, longitude)
  }

  return (
    <div className={`home${selectedEvent ? ' home--panel-open' : ''}`}>
      <Navbar
        onBrandClick={exitToGlobe}
      />

      {(view === 'globe' || leaving === 'globe') && (
        <div
          className={`home__layer${view === 'globe' ? ' home__layer--in' : waitingForMap ? ' home__layer--hold' : ' home__layer--out'}`}
        >
          <HomeGlobe events={events} center={globeCenter} onEnter={handleGlobeClick} onZoomEnter={handleGlobeZoomEnter} />
        </div>
      )}

      {view === 'globe' && (
        <div className="home__hint">
          지구를 <strong>확대</strong>하거나 <strong>클릭</strong>해 들어가 보세요
        </div>
      )}

      {(view === 'map' || leaving === 'map') && (
        <div
          className={`home__layer${view === 'map' ? (waitingForMap ? ' home__layer--waiting' : ' home__layer--in') : ' home__layer--out'}`}
        >
          <Suspense fallback={null}>
            <MapGlobe
              onViewportChange={loadEvents}
              events={events}
              selectedEventId={selectedEventId}
              selectionToken={selectionToken}
              onSelect={handleSelect}
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
              initialFocusLatLng={enterView ? null : (enterPoint ?? initialFocusLatLng)}
              initialView={enterView}
              onZoomOutBeyondMin={exitToGlobe}
              onReady={handleMapReady}
              myLocation={myLocation}
              locating={locating}
              locateError={locateError}
              onLocateRequest={locate}
            />
          </Suspense>
        </div>
      )}

      {loginHint && (
        <div className="home__hint home__hint--warn">{loginHint}</div>
      )}

      {view === 'map' && !loginHint && user && (
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

      {selectedEvent && (
        <EventDetailPanel
          key={selectedEvent.id}
          event={selectedEvent}
          onClose={() => selectEvent(null)}
          onConfirmed={addOrUpdate}
        />
      )}

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
