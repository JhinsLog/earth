import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import HomeGlobe, { type HandoffView } from '../components/globe/HomeGlobe'
import { DRAFT_MIN_ZOOM, GLOBE_TRANSITION_MS } from '../components/globe/constants'
import Navbar from '../components/layout/Navbar'
import FlareDetailPanel from '../components/panel/FlareDetailPanel'
import CreateFlareModal from '../components/panel/CreateFlareModal'
import { useFlareStore } from '../store/flareStore'
import { useAuthStore } from '../store/authStore'
import { useSettingsStore } from '../store/settingsStore'
import { useBlockStore } from '../store/blockStore'
import { useDraftFlares } from '../hooks/useDraftFlares'
import { useMyLocation } from '../hooks/useMyLocation'
import { api } from '../lib/api'
import { onReconnect } from '../lib/ws'
import { approximateLongitudeFromTimezone, detectApproximateLocation, type GeoPoint } from '../lib/geolocate'
import type { EarthUser } from '../types'
import './HomePage.css'

// 첫 화면은 홈 라스(three)고 지도(MapLibre, 번들의 큰 몫)는 지구를 클릭해 들어갈 때만 필요하다.
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

// 손전등 조작 안내. 마우스가 없는 기기(휴대폰·태블릿)는 우클릭 대신 길게 누른다.
const FLASHLIGHT_HINT = window.matchMedia('(pointer: coarse)').matches ? (
  <><strong>길게 눌러</strong> 비춰 보기</>
) : (
  <><strong>우클릭</strong>으로 비춰 보기</>
)

export default function HomePage() {
  const {
    flares,
    selectedFlareId,
    selectionToken,
    loadFlares,
    selectFlare,
    subscribeRealtime,
    reloadAfterReconnect,
    addOrUpdate,
    pruneExpired,
  } = useFlareStore()
  const { accessToken, user, setUser } = useAuthStore()
  const publicFlares = useSettingsStore((s) => s.publicFlares)
  // 화면에서만 거른다(docs/decisions.md). 제한 응답은 출처를 담지 않으므로 스위치와 무관하게 남는다.
  const blockedIds = useBlockStore((s) => s.blockedIds)
  // 차단한 사람의 플레어도 여기서 거른다. 실시간으로 온 것과 REST로 받은 것이 모두 이 목록을 거친다.
  // 제한 응답(비로그인)에는 작성자가 없지만, 차단은 로그인해야 쓸 수 있어 상관없다.
  const visibleFlares = useMemo(
    () =>
      flares.filter(
        (e) => (publicFlares || e.restricted || e.origin !== 'API') && (e.restricted || !blockedIds.has(e.authorId)),
      ),
    [flares, publicFlares, blockedIds],
  )
  const { drafts, addDraft, removeDraft, soonestRemainingMs } = useDraftFlares()
  // 내 위치는 여기서만 소유하고 필요한 두 곳에만 내려보낸다. 서버로는 나가지 않는다.
  const { myLocation, locating, locateError, locate } = useMyLocation()

  const [selectedDraftId, setSelectedDraftId] = useState<string | null>(null)
  const [initialFocusLatLng, setInitialFocusLatLng] = useState<GeoPoint | null>(null)
  const [loginHint, setLoginHint] = useState<string | null>(null)
  /** 지도가 손전등을 쓸 수 있는 배율인지. 안내 문구를 플레어 남기기와 비춰 보기 중에서 고른다. */
  const [flashlightZoom, setFlashlightZoom] = useState(false)
  const loginHintTimerRef = useRef<number | undefined>(undefined)
  const flashLoginHint = (message: string) => {
    window.clearTimeout(loginHintTimerRef.current)
    setLoginHint(message)
    loginHintTimerRef.current = window.setTimeout(() => setLoginHint(null), 3000)
  }

  // 첫 화면은 감상용 라스(HomeGlobe), 확대하면 탐색용 지도(MapGlobe)로 들어간다.
  // 로그인하고 돌아온 경우(OAuthRedirectPage가 state.enter를 붙인다)는 라스를 건너뛴다.
  const location = useLocation()
  const navigate = useNavigate()
  const [view, setView] = useState<'globe' | 'map'>(
    (location.state as { enter?: boolean } | null)?.enter ? 'map' : 'globe',
  )
  // state.enter는 한 번 읽고 지운다. 라우터 state는 브라우저 기록(history.state)에 남아 새로고침해도 그대로라,
  // 지우지 않으면 로그인한 뒤로는 새로고침할 때마다 첫 화면을 건너뛰고 지도로 들어갔다(2026-11-08).
  useEffect(() => {
    if ((location.state as { enter?: boolean } | null)?.enter) {
      navigate(location.pathname, { replace: true, state: null })
    }
  }, [location.state, location.pathname, navigate])
  // 교차 페이드 동안 사라지는 쪽도 잠깐 남겨 둔다. 그 뒤 언마운트해 WebGL 컨텍스트를 하나만 유지한다.
  const [leaving, setLeaving] = useState<'globe' | 'map' | null>(null)
  const leavingTimerRef = useRef<number | undefined>(undefined)
  /** 라스를 확대해 들어왔을 때 지도가 이어받을 화면. 있으면 지도는 어디로도 날아가지 않고 그 자리에서 시작한다. */
  const [enterView, setEnterView] = useState<HandoffView | null>(null)
  /** 지도에서 축소해 돌아왔을 때 홈 라스가 이어받을 화면. 없으면 처음 화면(globeCenter, 배율 1)으로 시작한다. */
  const [returnView, setReturnView] = useState<HandoffView | null>(null)
  const [globeCenter] = useState<GeoPoint>(() => ({ latitude: 20, longitude: approximateLongitudeFromTimezone() }))

  // 지도로 들어갈 때는 지도가 준비될 때까지(타일 도착) 라스를 그대로 두고 지도는 숨겨 둔다.
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

  // 전환할 때마다 들어가는 쪽을 새 인스턴스로 띄운다. 빠지는 쪽이 아직 사라지는 중(GLOBE_TRANSITION_MS)에 되돌아오면
  // React가 그 인스턴스를 그대로 다시 쓰는데, 지도는 '준비됨' 알림을 처음 한 번만 보내므로 다시 기다리던 화면이 영영
  // 풀리지 않았다(라스는 조작 막힘, 지도는 투명 — 휠로 확대·축소를 빠르게 오가면 재현, 2026-11-07). 라스도 이미
  // 넘겨준(handedOff) 상태와 처음 화면 값을 들고 있어 같은 문제가 있다. 키를 바꾸면 이전 것은 내려가고 새로 시작한다.
  const [globeKey, setGlobeKey] = useState(0)
  const [mapKey, setMapKey] = useState(0)

  const switchView = (next: 'globe' | 'map') => {
    if (next === view) return
    window.clearTimeout(leavingTimerRef.current)
    setLeaving(view)
    if (next === 'map') setMapKey((key) => key + 1)
    else setGlobeKey((key) => key + 1)
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

  // 비로그인도 그대로 들어간다. 다른 나라 플레어는 서버가 위치만 내려 주고 상세 요청은 거부하므로(restricted, 403),
  // 입구에서 로그인을 물을 이유가 없다. 로그인은 오른쪽 위 버튼으로 한다(D-8: 제한은 상세만).
  const handleGlobeZoomEnter = (view: HandoffView) => {
    setEnterView(view)
    switchView('map')
  }

  // 로고로 돌아갈 때는 view 없이 처음 화면으로 돌아간다.
  const exitToGlobe = (view: HandoffView | null) => {
    setReturnView(view)
    selectFlare(null)
    setSelectedDraftId(null)
    switchView('globe')
  }

  // 만료 전파는 WebSocket으로 오지만, 연결이 끊겼다 붙는 사이의 메시지는 재생되지
  // 않는다(STOMP는 놓친 메시지를 다시 주지 않는다). 재연결 때 목록을 다시 받지만, 그 전에도
  // 수명이 다한 플레어가 화면에 남지 않도록 클라이언트에서 주기적으로 걷어낸다.
  useEffect(() => {
    const id = window.setInterval(pruneExpired, 1000)
    return () => window.clearInterval(id)
  }, [pruneExpired])

  // 끊긴 동안 생기거나 사라진 플레어는 실시간으로 다시 오지 않으므로 재연결되면 목록을 다시 받는다.
  useEffect(() => onReconnect(() => void reloadAfterReconnect().catch(() => {})), [reloadAfterReconnect])

  useEffect(() => {
    detectApproximateLocation().then(setInitialFocusLatLng)
  }, [])

  // 로그인 여부에 따라 받을 수 있는 범위가 다르다(비로그인은 접속 국가의 플레어만 상세).
  // 로그인·로그아웃하면 목록을 다시 받고 실시간 구독도 그 범위에 맞게 다시 맺는다.
  useEffect(() => {
    let cancelled = false
    let unsubscribe: (() => void) | null = null
    loadFlares()
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
      useBlockStore.getState().clear()
      return
    }
    useBlockStore.getState().load().catch(() => {})
    api.get<EarthUser>('/api/users/me').then(({ data }) => setUser(data))
  }, [accessToken, setUser])

  const found = visibleFlares.find((e) => e.id === selectedFlareId)
  const selectedFlare = found && !found.restricted ? found : null

  const handleSelect = (id: number | null) => {
    const target = id == null ? null : visibleFlares.find((e) => e.id === id)
    // 위치만 아는 플레어는 서버가 상세를 주지 않는다(403). 열지 않고 이유를 알려 준다.
    if (target?.restricted) {
      flashLoginHint('로그인하면 다른 나라의 플레어도 자세히 볼 수 있습니다')
      return
    }
    selectFlare(id)
  }
  // 수명이 다해 목록에서 빠지면 selectedDraft도 자연히 null이 되어 모달이 닫힌다.
  const selectedDraft = drafts.find((d) => d.id === selectedDraftId) ?? null

  const handleDraftCreate = (latitude: number, longitude: number, zoom: number) => {
    if (!user) {
      flashLoginHint('플레어를 남기려면 로그인이 필요합니다')
      return
    }
    if (zoom < DRAFT_MIN_ZOOM) {
      flashLoginHint('더 확대해야 플레어를 남길 수 있습니다')
      return
    }
    addDraft(latitude, longitude)
  }

  // 이 화면의 우클릭은 플레어 남기기와 달빛 비춰 보기다. 지도·라스는 각자 브라우저 메뉴를 막지만, 이벤트가 그쪽에
  // 닿지 않으면(위에 겹친 요소에서 눌린 경우 등) 크롬 메뉴가 떴다(2026-11-08). 그래서 페이지 전체에서 막는다.
  // 글을 입력하거나 복사하려는 경우(입력창 안, 글자를 선택한 상태)는 브라우저 메뉴를 그대로 둔다.
  const blockBrowserMenu = (e: { target: EventTarget; preventDefault: () => void }) => {
    if (e.target instanceof Element && e.target.closest('input, textarea, [contenteditable="true"]')) return
    if (window.getSelection()?.toString()) return
    e.preventDefault()
  }

  return (
    <div className={`home${selectedFlare ? ' home--panel-open' : ''}`} onContextMenu={blockBrowserMenu}>
      <Navbar
        onBrandClick={() => exitToGlobe(null)}
      />

      {(view === 'globe' || leaving === 'globe') && (
        <div
          className={`home__layer${view === 'globe' ? ' home__layer--in' : waitingForMap ? ' home__layer--hold' : ' home__layer--out'}`}
        >
          <HomeGlobe
            key={globeKey}
            flares={visibleFlares}
            center={initialFocusLatLng ?? globeCenter}
            initialView={returnView}
            onZoomEnter={handleGlobeZoomEnter}
            onContextLost={() => setGlobeKey((key) => key + 1)}
          />
        </div>
      )}

      {view === 'globe' && (
        <div className="home__hint">
          지구를 <strong>확대</strong>해 들어가 보세요 · 밤 지역은 {FLASHLIGHT_HINT}
        </div>
      )}

      {(view === 'map' || leaving === 'map') && (
        <div
          className={`home__layer${view === 'map' ? (waitingForMap ? ' home__layer--waiting' : ' home__layer--in') : ' home__layer--out'}`}
        >
          <Suspense fallback={null}>
            <MapGlobe
              key={mapKey}
              onViewportChange={loadFlares}
              flares={visibleFlares}
              selectedFlareId={selectedFlareId}
              selectionToken={selectionToken}
              onSelect={handleSelect}
              draftFlares={drafts}
              onDraftCreate={handleDraftCreate}
              onDraftSelect={(id) => {
                selectFlare(null)
                setSelectedDraftId(id)
              }}
              onDraftRemove={(id) => {
                removeDraft(id)
                setSelectedDraftId((current) => (current === id ? null : current))
              }}
              initialFocusLatLng={enterView ? null : initialFocusLatLng}
              initialView={enterView}
              onZoomOutToHome={exitToGlobe}
              onReady={handleMapReady}
              onContextLost={exitToGlobe}
              onFlashlightZoomChange={setFlashlightZoom}
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

      {view === 'map' && !loginHint && (user || flashlightZoom) && (
        <div className="home__hint">
          {drafts.length === 0 && flashlightZoom ? (
            <>밤 지역은 {FLASHLIGHT_HINT}</>
          ) : drafts.length === 0 ? (
            <>확대한 뒤 <strong>우클릭</strong>해 플레어를 남겨보세요</>
          ) : (
            <>
              임시 플레어 {drafts.length}개 · 클릭해 내용 입력, <strong>우클릭</strong>하면 삭제
              <span className="home__hint-timer">{formatRemaining(soonestRemainingMs)} 후 사라짐</span>
            </>
          )}
        </div>
      )}

      {selectedFlare && (
        <FlareDetailPanel
          key={selectedFlare.id}
          flare={selectedFlare}
          onClose={() => selectFlare(null)}
          onConfirmed={addOrUpdate}
        />
      )}

      {selectedDraft && (
        <CreateFlareModal
          latitude={selectedDraft.latitude}
          longitude={selectedDraft.longitude}
          onClose={() => setSelectedDraftId(null)}
          onDelete={() => {
            removeDraft(selectedDraft.id)
            setSelectedDraftId(null)
          }}
          onCreated={(flare) => {
            // 서버에 등록됐으니 임시 플레어는 걷어내고 실제 플레어로 대체한다.
            removeDraft(selectedDraft.id)
            setSelectedDraftId(null)
            addOrUpdate(flare)
            selectFlare(flare.id)
          }}
        />
      )}
    </div>
  )
}
