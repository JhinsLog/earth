import { useEffect, useRef } from 'react'
import * as maplibregl from 'maplibre-gl'
import type { GeoJSONSource, Map as MapLibreMap, MapLayerMouseEvent, MapMouseEvent } from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import {
  DARK_BASE_OPACITY,
  DARK_GATE_MAX_ZOOM,
  ESRI_GATE_MAX_ZOOM,
  ESRI_OPACITY,
  createEarthMapStyle,
} from './mapStyle'
import { createStarFlareImageData } from './starFlareTexture'
import Starfield from './Starfield'
import { detectLabelLanguage } from '../../lib/language'
import { destinationPoint, haversineDistanceKm } from '../../lib/geo'
import './GlobeHalo.css'
import {
  AUTO_ROTATE_DEGREES_PER_FRAME,
  AUTO_ROTATE_MAX_ZOOM,
  COUNTRY_FOCUS_ZOOM,
  COUNTRY_LABEL_MAX_DISTANCE_KM,
  DRAFT_STAR_COLOR,
  EVENT_FOCUS_ZOOM,
  EVENT_PANEL_WIDTH_PX,
  INITIAL_ZOOM,
  HOME_VIEW_MAX_ABS_LATITUDE,
  MAX_ZOOM,
  MIN_ZOOM,
} from './constants'
import { approximateLongitudeFromTimezone, type GeoPoint } from '../../lib/geolocate'
import { EVENT_CATEGORY_COLOR, type DraftStar, type EarthEvent } from '../../types'
import './MapGlobe.css'

const EVENTS_SOURCE_ID = 'earth-events-source'
const DRAFTS_SOURCE_ID = 'earth-drafts-source'
const STAR_FLARE_IMAGE_ID = 'earth-star-flare-sdf'
/** 클릭 판정을 받는 레이어 — 넓은 오라와 중앙 코어만으로 충분하다. */
const EVENT_CLICK_LAYER_IDS = ['events-aura-layer', 'events-white-core'] as const
const DRAFT_CLICK_LAYER_IDS = ['drafts-aura-layer', 'drafts-white-core'] as const

interface Props {
  events: EarthEvent[]
  selectedEventId: number | null
  onSelect: (id: number | null) => void
  draftStars: DraftStar[]
  onDraftCreate: (latitude: number, longitude: number) => void
  onDraftSelect: (id: string) => void
  onDraftRemove: (id: string) => void
  initialFocusLatLng?: GeoPoint | null
  /** 값이 바뀔 때마다 첫 화면(지구본 전체)으로 되돌아간다. 로고 클릭용. */
  resetViewToken?: number
}

/**
 * 소스가 준비된 뒤 콜백을 실행하고, 정리 함수를 돌려준다.
 *
 * isStyleLoaded()로 판단하면 안 된다. 이 값은 "모든 소스의 타일까지 다 왔는가"를
 * 보기 때문에 지도가 조금이라도 움직이는 중이면 false가 된다. 그때 once('load')로
 * 미루면 'load'는 이미 지나가 다시 오지 않으므로 콜백이 영영 실행되지 않는다.
 * 새로 등록한 별이 지도에 올라오지 않아 클릭조차 되지 않던 원인이다.
 *
 * setData는 소스만 있으면 안전하므로 소스 존재 여부만 본다. 소스는 map 'load'
 * 안에서 추가되니, 아직 없다면 load가 아직 오지 않은 것이라 기다려도 안전하다.
 */
function whenSourceReady(map: MapLibreMap, sourceId: string, run: () => void): () => void {
  if (map.getSource(sourceId)) {
    run()
    return () => {}
  }
  const handler = () => {
    if (!map.getSource(sourceId)) return
    map.off('load', handler)
    run()
  }
  map.on('load', handler)
  return () => map.off('load', handler)
}

function toEventGeoJson(events: EarthEvent[]) {
  return {
    type: 'FeatureCollection' as const,
    features: events.map((event) => ({
      type: 'Feature' as const,
      geometry: { type: 'Point' as const, coordinates: [event.longitude, event.latitude] },
      properties: {
        id: event.id,
        color: EVENT_CATEGORY_COLOR[event.category],
      },
    })),
  }
}

function toDraftGeoJson(drafts: DraftStar[]) {
  return {
    type: 'FeatureCollection' as const,
    features: drafts.map((draft) => ({
      type: 'Feature' as const,
      geometry: { type: 'Point' as const, coordinates: [draft.longitude, draft.latitude] },
      properties: { id: draft.id },
    })),
  }
}

/**
 * 별 하나를 이루는 4개 레이어(오라 → 헤일로 → 색 성광 → 백색 코어)를 한 번에 얹는다.
 * 등록된 이벤트와 임시 별이 같은 생김새를 공유하되 색과 밝기만 달라지도록 공통화했다.
 */
function addStarLayers(
  map: MapLibreMap,
  sourceId: string,
  prefix: string,
  color: string | unknown[],
  opacityScale: number,
) {
  map.addLayer({
    id: `${prefix}-aura-layer`,
    type: 'circle',
    source: sourceId,
    paint: {
      'circle-radius': ['interpolate', ['linear'], ['zoom'], 1, 7, 10, 16],
      'circle-color': color as string,
      'circle-opacity': 0.4 * opacityScale,
      'circle-blur': 0.85,
    },
  })

  map.addLayer({
    id: `${prefix}-halo-layer`,
    type: 'circle',
    source: sourceId,
    paint: {
      'circle-radius': ['interpolate', ['linear'], ['zoom'], 1, 3.5, 10, 8],
      'circle-color': color as string,
      'circle-opacity': 0.8 * opacityScale,
      'circle-blur': 0.25,
    },
  })

  map.addLayer({
    id: `${prefix}-color-flare`,
    type: 'symbol',
    source: sourceId,
    layout: {
      'icon-image': STAR_FLARE_IMAGE_ID,
      'icon-size': ['interpolate', ['linear'], ['zoom'], 1, 0.18, 10, 0.38],
      'icon-allow-overlap': true,
      'icon-ignore-placement': true,
    },
    paint: {
      'icon-color': color as string,
      'icon-opacity': 0.95 * opacityScale,
    },
  })

  map.addLayer({
    id: `${prefix}-white-core`,
    type: 'symbol',
    source: sourceId,
    layout: {
      'icon-image': STAR_FLARE_IMAGE_ID,
      'icon-size': ['interpolate', ['linear'], ['zoom'], 1, 0.09, 10, 0.2],
      'icon-allow-overlap': true,
      'icon-ignore-placement': true,
    },
    paint: {
      'icon-color': '#ffffff',
      'icon-opacity': 1.0 * opacityScale,
    },
  })
}

export default function MapGlobe({
  events,
  selectedEventId,
  onSelect,
  draftStars,
  onDraftCreate,
  onDraftSelect,
  onDraftRemove,
  initialFocusLatLng,
  resetViewToken = 0,
}: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const haloRef = useRef<HTMLDivElement>(null)
  const rootRef = useRef<HTMLDivElement>(null)
  const readyRef = useRef(false)
  const mapRef = useRef<MapLibreMap | null>(null)
  const isInteractingRef = useRef(false)
  const suppressNextMapClickRef = useRef(false)
  /** 임시 별 위에서 우클릭한 경우, 전역 우클릭(=새 별 생성)까지 실행되지 않게 막는다. */
  const suppressNextContextMenuRef = useRef(false)
  const appliedInitialFocusRef = useRef(false)
  /**
   * 첫 화면(지구본 전체)의 중심. 지도를 만드는 시점에는 네트워크 조회 결과를 기다릴 수
   * 없으므로 표준시로 추정한 경도를 먼저 쓰고, IP/GPS 위치가 도착하면 실제 값으로 교체한다.
   * 로고를 눌러 돌아올 때도 이 값을 쓰므로 항상 접속자의 대륙이 보인다.
   */
  const homeCenterRef = useRef<[number, number]>([approximateLongitudeFromTimezone(), 20])

  // 최신 콜백/props를 ref로 보관 — map 인스턴스 이벤트 리스너는 마운트 시 1회만 등록되므로
  // 클로저 안에서 항상 최신 값을 읽기 위함.
  const onSelectRef = useRef(onSelect)
  onSelectRef.current = onSelect
  const onDraftCreateRef = useRef(onDraftCreate)
  onDraftCreateRef.current = onDraftCreate
  const onDraftSelectRef = useRef(onDraftSelect)
  onDraftSelectRef.current = onDraftSelect
  const onDraftRemoveRef = useRef(onDraftRemove)
  onDraftRemoveRef.current = onDraftRemove
  const eventsRef = useRef(events)
  eventsRef.current = events
  const draftStarsRef = useRef(draftStars)
  draftStarsRef.current = draftStars
  /** 임시 별이 있을 때만 명멸 애니메이션을 돌리기 위한 플래그. */
  const hasDraftsRef = useRef(draftStars.length > 0)
  hasDraftsRef.current = draftStars.length > 0

  useEffect(() => {
    if (!containerRef.current) return

    const map = new maplibregl.Map({
      container: containerRef.current,
      style: createEarthMapStyle(detectLabelLanguage()),
      center: homeCenterRef.current,
      zoom: INITIAL_ZOOM,
      minZoom: MIN_ZOOM,
      maxZoom: MAX_ZOOM,
      doubleClickZoom: false,
      // pitch/bearing 조작을 막아 카메라가 항상 지구본을 수직으로 내려다보게 고정한다.
      // 대기광(halo)이 화면 중심 대칭 원이라는 전제로 계산되므로, 기울어지면 어긋난다.
      dragRotate: false,
      pitchWithRotate: false,
      touchPitch: false,
      // ⓘ 버튼 하나로 접히는 형태. 지구본 화면을 해치지 않으면서 출처 표기 의무를
      // 충족한다 — 특히 OpenStreetMap 파생 데이터는 ODbL상 표기가 의무다.
      attributionControl: { compact: true },
      // 줌을 오갈 때 상위 줌 타일이 캐시에서 밀려나면 그 영역이 다시 빈 채로 그려진다.
      // 캐시를 넉넉히 잡아 되돌아왔을 때 재요청 없이 즉시 표시되게 한다.
      maxTileCacheSize: 500,
    })
    mapRef.current = map

    // 로딩 중에는 부분적으로만 채워진 타일과 대기광이 그대로 노출된다. 스타일과 타일이
    // 모두 준비된 뒤 한 번에 페이드인시킨다. 자동 자전이 매 프레임 setCenter를 호출해
    // 'idle'이 발생하지 않으므로, 매 프레임 발생하는 'render'에서 직접 확인한다.
    let revealTimeoutId: number | undefined
    function reveal() {
      if (readyRef.current) return
      readyRef.current = true
      map.off('render', tryReveal)
      window.clearTimeout(revealTimeoutId)
      // compact 모드라도 MapLibre는 처음엔 펼친 상태(maplibregl-compact-show)로 시작한다.
      // 지구본 화면을 가리므로 접어두고, 사용자가 ⓘ를 누르면 펼쳐지게 한다.
      // 한 번의 클릭으로 전문을 볼 수 있으므로 ODbL 등의 표기 요건도 충족한다.
      map
        .getContainer()
        .querySelector('.maplibregl-ctrl-attrib')
        ?.classList.remove('maplibregl-compact-show')
      rootRef.current?.classList.add('map-globe--ready')
    }
    function tryReveal() {
      if (map.isStyleLoaded() && map.areTilesLoaded()) reveal()
    }
    map.on('render', tryReveal)

    // 래스터 레이어가 부분적으로만 로딩된 채 그려지면, 도착한 타일 영역만 다르게 보여
    // 사각형 경계가 드러난다. 소스가 준비되기 전에는 불투명도를 0으로 눌러 아래
    // 레이어(NASA)만 보이게 하고, 준비되면 원래 램프를 되돌린다. 전환 400ms는
    // 스타일에 정의돼 있어 화면 전체가 한 번에 자연스럽게 바뀐다.
    const syncRasterGates = () => {
      if (map.getLayer('satellite-base')) {
        const gated =
          map.getZoom() < ESRI_GATE_MAX_ZOOM && !map.isSourceLoaded('esri-satellite')
        map.setPaintProperty('satellite-base', 'raster-opacity', gated ? 0 : ESRI_OPACITY)
      }
      if (map.getLayer('dark-base')) {
        const gated =
          map.getZoom() < DARK_GATE_MAX_ZOOM && !map.isSourceLoaded('esri-dark-gray')
        map.setPaintProperty('dark-base', 'raster-opacity', gated ? 0 : DARK_BASE_OPACITY)
      }
    }
    map.on('sourcedata', syncRasterGates)
    map.on('moveend', syncRasterGates)
    map.on('zoom', syncRasterGates)
    // 타일 하나가 끝내 도착하지 않아도 화면이 영원히 비어 있지 않도록 하는 안전장치.
    revealTimeoutId = window.setTimeout(reveal, 5000)
    let nearbyCountryIntervalId: number | undefined

    const setInteractingTrue = () => {
      isInteractingRef.current = true
    }
    const setInteractingFalse = () => {
      isInteractingRef.current = false
    }
    map.on('mousedown', setInteractingTrue)
    map.on('mouseup', setInteractingFalse)
    map.on('dragstart', setInteractingTrue)
    map.on('dragend', setInteractingFalse)
    map.on('zoomstart', setInteractingTrue)
    map.on('zoomend', setInteractingFalse)

    map.on('load', () => {
      const imageData = createStarFlareImageData()
      if (!map.hasImage(STAR_FLARE_IMAGE_ID)) {
        map.addImage(STAR_FLARE_IMAGE_ID, imageData, { sdf: true })
      }

      map.addSource(EVENTS_SOURCE_ID, {
        type: 'geojson',
        data: toEventGeoJson(eventsRef.current),
      })
      addStarLayers(map, EVENTS_SOURCE_ID, 'events', ['get', 'color'], 1)

      // 임시 별은 나중에 얹어 등록된 별 위에 오게 한다 — 같은 자리에 겹쳤을 때
      // 아직 처리해야 할 임시 별이 클릭에 먼저 잡히도록.
      map.addSource(DRAFTS_SOURCE_ID, {
        type: 'geojson',
        data: toDraftGeoJson(draftStarsRef.current),
      })
      addStarLayers(map, DRAFTS_SOURCE_ID, 'drafts', DRAFT_STAR_COLOR, 0.85)

      EVENT_CLICK_LAYER_IDS.forEach((layerId) => {
        map.on('click', layerId, (e: MapLayerMouseEvent) => {
          suppressNextMapClickRef.current = true
          // MapLibre는 GeoJSON 소스도 내부적으로 벡터 타일로 변환하므로 properties 값의
          // 원래 타입이 그대로 유지된다는 보장이 없다. 숫자로 변환해 받는다.
          const id = Number(e.features?.[0]?.properties?.id)
          if (Number.isFinite(id)) onSelectRef.current(id)
        })
        map.on('mouseenter', layerId, () => {
          map.getCanvas().style.cursor = 'pointer'
        })
        map.on('mouseleave', layerId, () => {
          map.getCanvas().style.cursor = ''
        })
      })

      DRAFT_CLICK_LAYER_IDS.forEach((layerId) => {
        map.on('click', layerId, (e: MapLayerMouseEvent) => {
          suppressNextMapClickRef.current = true
          const rawId = e.features?.[0]?.properties?.id
          if (rawId != null) onDraftSelectRef.current(String(rawId))
        })
        // 우클릭으로 찍었으니 우클릭으로 지운다.
        map.on('contextmenu', layerId, (e: MapLayerMouseEvent) => {
          suppressNextContextMenuRef.current = true
          const rawId = e.features?.[0]?.properties?.id
          if (rawId != null) onDraftRemoveRef.current(String(rawId))
        })
        map.on('mouseenter', layerId, () => {
          map.getCanvas().style.cursor = 'pointer'
        })
        map.on('mouseleave', layerId, () => {
          map.getCanvas().style.cursor = ''
        })
      })

      // 현재 보고 있는 중심 근처의 국가만 이름표를 남기고 나머지는 숨긴다 — 저줌에서
      // 시야에 들어오는 모든 나라 이름이 한꺼번에 뜨면 시선이 분산되기 때문.
      // 스타일 filter의 `distance` 식은 이 라이브러리 버전에서 filter 컨텍스트를 지원하지
      // 않아 항상 false로 평가되므로, querySourceFeatures로 직접 거리순 필터를 만든다.
      const updateNearbyCountryFilter = () => {
        if (!map.getLayer('place-country-labels')) return
        const center = map.getCenter()
        const features = map.querySourceFeatures('openmaptiles', {
          sourceLayer: 'place',
          filter: ['==', ['get', 'class'], 'country'],
        })
        const nearbyNames = new Set<string>()
        for (const feature of features) {
          if (feature.geometry.type !== 'Point') continue
          const [lng, lat] = feature.geometry.coordinates
          const distanceKm = haversineDistanceKm(center.lat, center.lng, lat, lng)
          if (distanceKm <= COUNTRY_LABEL_MAX_DISTANCE_KM) {
            const name = feature.properties?.name
            if (typeof name === 'string') nearbyNames.add(name)
          }
        }
        map.setFilter('place-country-labels', [
          'all',
          ['==', ['get', 'class'], 'country'],
          ['in', ['get', 'name'], ['literal', Array.from(nearbyNames)]],
        ])
      }

      updateNearbyCountryFilter()
      map.on('moveend', updateNearbyCountryFilter)
      nearbyCountryIntervalId = window.setInterval(updateNearbyCountryFilter, 400)
    })

    // 빈 곳을 좌클릭하면 선택 해제. 별 레이어를 클릭한 경우에는 위쪽 레이어 핸들러가
    // 먼저 처리하면서 suppress 플래그를 세워두므로 여기서 선택이 풀리지 않는다.
    map.on('click', () => {
      if (suppressNextMapClickRef.current) {
        suppressNextMapClickRef.current = false
        return
      }
      onSelectRef.current(null)
    })

    // 우클릭한 지점에 임시 별을 찍는다. 브라우저 기본 컨텍스트 메뉴가 뜨면
    // 별이 가려지므로 확실히 막는다.
    map.on('contextmenu', (e: MapMouseEvent) => {
      e.preventDefault()
      e.originalEvent?.preventDefault()
      // 임시 별 위에서 우클릭한 경우는 삭제로 처리됐으니 새로 만들지 않는다.
      if (suppressNextContextMenuRef.current) {
        suppressNextContextMenuRef.current = false
        return
      }
      const { lat, lng } = e.lngLat
      // globe 투영에서 지구본 바깥(우주)을 클릭하면 좌표가 유효 범위를 벗어날 수 있다.
      // 그대로 두면 백엔드 검증(-90~90 / -180~180)에서 걸리므로 여기서 걸러낸다.
      if (!Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90) return
      onDraftCreateRef.current(lat, lng)
    })

    // 지구본 가장자리 대기광 — MapLibre의 네이티브 Sky/atmosphere는 pitch가 있는 3D 지형용이라
    // 우리처럼 수직으로 내려다보는 globe 뷰에서는 전혀 렌더링되지 않는다(v5.24 기준 확인됨).
    // 지구본의 실제 가장자리(limb)가 보이는 각도는 카메라가 멀수록(저줌) 90°에 가깝고
    // 가까워질수록(고줌) 크게 줄어든다(줌4≈45°, 줌6≈25°) — 원근 투영이라 당연한 현상인데,
    // 처음엔 이 사실을 놓치고 항상 90°로 계산해 고줌에서 대기광이 실제 표면보다 훨씬
    // 커지는 버그가 있었다. 이제 project→unproject 왕복 검증으로 실제 가장자리 각도를
    // 이진탐색해 구하고, 그 지점을 화면에 투영해 정확한 반지름을 얻는다.
    const findLimbAngleDeg = (lat: number, lng: number, bearingDeg: number): number => {
      let lo = 1
      let hi = 89.9
      for (let i = 0; i < 16; i++) {
        const mid = (lo + hi) / 2
        const dest = destinationPoint(lat, lng, bearingDeg, mid)
        const screen = map.project([dest.lng, dest.lat])
        const roundTrip = map.unproject([screen.x, screen.y])
        let dLng = Math.abs(roundTrip.lng - dest.lng)
        if (dLng > 180) dLng = 360 - dLng
        const isVisible = Math.abs(roundTrip.lat - dest.lat) < 0.05 && dLng < 0.05
        if (isVisible) lo = mid
        else hi = mid
      }
      return lo
    }

    const updateHalo = () => {
      const haloEl = haloRef.current
      if (!haloEl) return
      // 준비 전에는 대기광 원이 아직 구형이 아닌 지도 위에 겹쳐 보인다.
      if (!readyRef.current) {
        haloEl.style.opacity = '0'
        return
      }
      const zoom = map.getZoom()
      const fadeOutZoom = 6
      // pitch/bearing이 0이 아니면(지도가 기울어지면) 화면 중심 대칭 원이라는 계산 전제가
      // 깨지므로 안전하게 숨긴다 — dragRotate 등을 꺼뒀지만 이중 방어 차원.
      if (zoom >= fadeOutZoom || map.getPitch() !== 0 || map.getBearing() !== 0) {
        haloEl.style.opacity = '0'
        return
      }
      const center = map.getCenter()
      const centerScreen = map.project([center.lng, center.lat])
      const limbAngle = findLimbAngleDeg(center.lat, center.lng, 90)
      const limb = destinationPoint(center.lat, center.lng, 90, limbAngle)
      const limbScreen = map.project([limb.lng, limb.lat])
      const radius = Math.hypot(limbScreen.x - centerScreen.x, limbScreen.y - centerScreen.y)
      const opacity = 1 - Math.min(1, Math.max(0, (zoom - (fadeOutZoom - 2)) / 2))

      haloEl.style.width = `${radius * 2}px`
      haloEl.style.height = `${radius * 2}px`
      haloEl.style.left = `${centerScreen.x}px`
      haloEl.style.top = `${centerScreen.y}px`
      haloEl.style.opacity = String(opacity)
    }

    // 임시 별은 부드럽게 명멸시켜 "아직 등록되지 않은, 곧 사라질 상태"임을 드러낸다.
    const updateDraftPulse = () => {
      if (!hasDraftsRef.current || !map.getLayer('drafts-white-core')) return
      const pulse = 0.55 + 0.45 * Math.sin(performance.now() / 400)
      map.setPaintProperty('drafts-white-core', 'icon-opacity', pulse)
      map.setPaintProperty('drafts-color-flare', 'icon-opacity', pulse * 0.8)
    }

    let rafId: number
    const rotate = () => {
      // isEasing() 확인이 반드시 필요하다. flyTo는 곡선 궤적이라 초반에 줌이 오히려
      // 낮아지는데, 그때 자전이 setCenter를 호출하면 진행 중이던 이동이 통째로 취소된다.
      // (별을 클릭해도 확대되지 않던 원인이 이것이었다. 타이머로 자전을 멈추는 것만으로는
      //  애니메이션이 타이머보다 오래 걸릴 때 막지 못한다.)
      if (!isInteractingRef.current && !map.isEasing() && map.getZoom() < AUTO_ROTATE_MAX_ZOOM) {
        const center = map.getCenter()
        center.lng -= AUTO_ROTATE_DEGREES_PER_FRAME
        map.setCenter(center)
      }
      updateHalo()
      updateDraftPulse()
      rafId = requestAnimationFrame(rotate)
    }
    rafId = requestAnimationFrame(rotate)

    return () => {
      cancelAnimationFrame(rafId)
      map.off('sourcedata', syncRasterGates)
      map.off('moveend', syncRasterGates)
      map.off('zoom', syncRasterGates)
      window.clearTimeout(revealTimeoutId)
      readyRef.current = false
      appliedInitialFocusRef.current = false
      window.clearInterval(nearbyCountryIntervalId)
      map.remove()
      mapRef.current = null
    }
  }, [])

  // 이벤트 목록이 바뀔 때마다 지도 위 별 데이터 갱신
  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    return whenSourceReady(map, EVENTS_SOURCE_ID, () => {
      const source = map.getSource(EVENTS_SOURCE_ID) as GeoJSONSource | undefined
      source?.setData(toEventGeoJson(events))
    })
  }, [events])

  // 임시 별 목록 갱신 (생성 / 등록 완료 / 수명 만료로 바뀐다)
  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    return whenSourceReady(map, DRAFTS_SOURCE_ID, () => {
      const source = map.getSource(DRAFTS_SOURCE_ID) as GeoJSONSource | undefined
      source?.setData(toDraftGeoJson(draftStars))
    })
  }, [draftStars])

  // 이벤트를 선택하면 해당 좌표로 확대 이동
  useEffect(() => {
    const map = mapRef.current
    if (!map || selectedEventId == null) return
    const event = events.find((e) => e.id === selectedEventId)
    if (!event) return

    isInteractingRef.current = true
    map.flyTo({
      center: [event.longitude, event.latitude],
      zoom: EVENT_FOCUS_ZOOM,
      // 우측 상세 패널이 덮는 만큼 시야를 왼쪽으로 밀어, 선택한 별이 패널에 가려지지 않게 한다.
      padding: { top: 0, bottom: 0, left: 0, right: EVENT_PANEL_WIDTH_PX },
      speed: 1.2,
      curve: 1.4,
      essential: true,
    })
    // 자전 재개는 애니메이션이 실제로 끝나는 시점(moveend)에 맞춘다. 고정 타이머로 풀면
    // 이동이 아직 진행 중일 때 자전이 끼어들어 확대가 중간에 멈춘다.
    // moveend가 끝내 오지 않는 경우(중간 취소 등)에 대비해 넉넉한 타이머도 함께 건다.
    const releaseRotation = () => {
      isInteractingRef.current = false
    }
    map.once('moveend', releaseRotation)
    const timer = window.setTimeout(releaseRotation, 8000)
    return () => {
      map.off('moveend', releaseRotation)
      window.clearTimeout(timer)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedEventId])

  // 접속 위치(GPS/IP)로 최초 1회만 이동
  useEffect(() => {
    const map = mapRef.current
    if (!map || !initialFocusLatLng || appliedInitialFocusRef.current) return

    const apply = () => {
      // 플래그는 실제로 이동을 실행하는 순간에만 세운다. 진입 시점에 세워버리면
      // apply가 끝내 호출되지 않았을 때 재시도 경로까지 영구히 막힌다.
      if (appliedInitialFocusRef.current) return
      appliedInitialFocusRef.current = true
      map.off('render', tryApply)
      // 실제 접속 위치를 알게 됐으니 '첫 화면'의 기준도 여기로 옮긴다.
      // 위도는 극지방 접속자에게 지구본이 극만 크게 보이지 않도록 잘라 쓴다.
      homeCenterRef.current = [
        initialFocusLatLng.longitude,
        Math.max(-HOME_VIEW_MAX_ABS_LATITUDE, Math.min(HOME_VIEW_MAX_ABS_LATITUDE, initialFocusLatLng.latitude)),
      ]
      isInteractingRef.current = true
      map.flyTo({
        center: [initialFocusLatLng.longitude, initialFocusLatLng.latitude],
        zoom: COUNTRY_FOCUS_ZOOM,
        speed: 1.0,
        curve: 1.4,
        essential: true,
      })
      setTimeout(() => {
        isInteractingRef.current = false
      }, 2500)
    }

    function tryApply() {
      if (map!.isStyleLoaded()) apply()
    }

    // 'load'는 딱 한 번만 발생하므로 이미 지나간 뒤 once('load')를 걸면 영영 실행되지
    // 않는다. 게다가 map.loaded()는 타일 로딩 상태까지 보기 때문에 load가 끝난 뒤에도
    // false가 될 수 있어, 그 순간에 걸리면 이동이 통째로 유실된다.
    // 매 프레임 발생하는 'render'에서 스타일 준비 여부를 직접 확인한다.
    if (map.isStyleLoaded()) apply()
    else map.on('render', tryApply)

    return () => {
      map.off('render', tryApply)
    }
  }, [initialFocusLatLng])

  // 로고 클릭 → 첫 화면(지구본 전체)으로 복귀
  const appliedResetTokenRef = useRef(resetViewToken)
  useEffect(() => {
    const map = mapRef.current
    // 최초 마운트 시점은 이미 지구본 전체 화면이므로 아무것도 하지 않는다.
    if (!map || appliedResetTokenRef.current === resetViewToken) return
    appliedResetTokenRef.current = resetViewToken

    isInteractingRef.current = true
    map.flyTo({
      center: homeCenterRef.current,
      zoom: INITIAL_ZOOM,
      // 별을 선택할 때 넣었던 우측 패널 여백이 지도에 그대로 남아 있다.
      // 0으로 되돌리지 않으면 지구본이 화면 왼쪽으로 치우친 채 복귀한다.
      padding: { top: 0, bottom: 0, left: 0, right: 0 },
      speed: 1.2,
      curve: 1.4,
      essential: true,
    })

    const releaseRotation = () => {
      isInteractingRef.current = false
    }
    map.once('moveend', releaseRotation)
    const timer = window.setTimeout(releaseRotation, 8000)
    return () => {
      map.off('moveend', releaseRotation)
      window.clearTimeout(timer)
    }
  }, [resetViewToken])

  return (
    <div className="map-globe" ref={rootRef}>
      <Starfield />
      <div ref={containerRef} className="map-globe__container" />
      <div ref={haloRef} className="globe-halo" />
    </div>
  )
}
