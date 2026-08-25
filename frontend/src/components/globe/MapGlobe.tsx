import { useEffect, useRef } from 'react'
import * as maplibregl from 'maplibre-gl'
import type { GeoJSONSource, Map as MapLibreMap, MapLayerMouseEvent, MapMouseEvent } from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import {
  CARTO_DARK_OPACITY,
  CARTO_GATE_MAX_ZOOM,
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
  EVENT_FOCUS_ZOOM,
  INITIAL_ZOOM,
  MAX_ZOOM,
  MIN_ZOOM,
} from './constants'
import type { GeoPoint } from '../../lib/geolocate'
import { EVENT_CATEGORY_COLOR, type EarthEvent } from '../../types'
import './MapGlobe.css'

const STAR_LAYER_IDS = ['events-aura-layer', 'events-white-core'] as const
const EVENTS_SOURCE_ID = 'earth-events-source'
const STAR_FLARE_IMAGE_ID = 'earth-star-flare-sdf'
/** 우클릭으로 찍었지만 아직 내용을 입력하지 않은 "임시 별". 확정 전까지는 본인 화면에만 보인다. */
const PENDING_SOURCE_ID = 'earth-pending-star-source'

interface Props {
  events: EarthEvent[]
  /** 우클릭으로 찍은 임시 별의 위치. 확정 또는 취소되면 null이 된다. */
  pendingLocation?: { lat: number; lng: number } | null
  selectedEventId: number | null
  onSelect: (id: number | null) => void
  placing: boolean
  onPlaceLocation: (latitude: number, longitude: number) => void
  initialFocusLatLng?: GeoPoint | null
}

/**
 * 스타일이 준비된 뒤 콜백을 실행하고, 정리 함수를 돌려준다.
 *
 * map.once('load', ...)를 쓰면 안 된다. 'load'는 딱 한 번만 발생하는데
 * isStyleLoaded()는 소스를 갱신하는 순간 일시적으로 false가 되므로, 이미 load가
 * 지나간 뒤 false를 만나면 콜백이 영영 실행되지 않는다(임시 별이 지워지지 않던 원인).
 * 매 프레임 발생하는 'render'에서 준비 여부를 직접 확인한다.
 */
function whenStyleReady(map: MapLibreMap, run: () => void): () => void {
  if (map.isStyleLoaded()) {
    run()
    return () => {}
  }
  const handler = () => {
    if (!map.isStyleLoaded()) return
    map.off('render', handler)
    run()
  }
  map.on('render', handler)
  return () => map.off('render', handler)
}

function toPendingGeoJson(point: { lat: number; lng: number } | null | undefined) {
  return {
    type: 'FeatureCollection' as const,
    features: point
      ? [
          {
            type: 'Feature' as const,
            geometry: { type: 'Point' as const, coordinates: [point.lng, point.lat] },
            properties: {},
          },
        ]
      : [],
  }
}

function toGeoJson(events: EarthEvent[]) {
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

export default function MapGlobe({
  events,
  pendingLocation,
  selectedEventId,
  onSelect,
  placing,
  onPlaceLocation,
  initialFocusLatLng,
}: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const haloRef = useRef<HTMLDivElement>(null)
  const rootRef = useRef<HTMLDivElement>(null)
  const readyRef = useRef(false)
  const mapRef = useRef<MapLibreMap | null>(null)
  const isInteractingRef = useRef(false)
  const suppressNextMapClickRef = useRef(false)
  const appliedInitialFocusRef = useRef(false)

  // 최신 콜백/props를 ref로 보관 — map 인스턴스 이벤트 리스너는 마운트 시 1회만 등록되므로
  // 클로저 안에서 항상 최신 값을 읽기 위함.
  const placingRef = useRef(placing)
  placingRef.current = placing
  const onSelectRef = useRef(onSelect)
  onSelectRef.current = onSelect
  const onPlaceLocationRef = useRef(onPlaceLocation)
  onPlaceLocationRef.current = onPlaceLocation
  const eventsRef = useRef(events)
  eventsRef.current = events

  useEffect(() => {
    if (!containerRef.current) return

    const map = new maplibregl.Map({
      container: containerRef.current,
      style: createEarthMapStyle(detectLabelLanguage()),
      center: [0, 20],
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
      if (map.getLayer('carto-dark-base')) {
        const gated =
          map.getZoom() < CARTO_GATE_MAX_ZOOM && !map.isSourceLoaded('carto-dark')
        map.setPaintProperty('carto-dark-base', 'raster-opacity', gated ? 0 : CARTO_DARK_OPACITY)
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
        data: toGeoJson(eventsRef.current),
      })

      // 넓게 퍼지는 은은한 오라
      map.addLayer({
        id: 'events-aura-layer',
        type: 'circle',
        source: EVENTS_SOURCE_ID,
        paint: {
          'circle-radius': ['interpolate', ['linear'], ['zoom'], 1, 7, 10, 16],
          'circle-color': ['get', 'color'],
          'circle-opacity': 0.4,
          'circle-blur': 0.85,
        },
      })

      // 중간 헤일로
      map.addLayer({
        id: 'events-halo-layer',
        type: 'circle',
        source: EVENTS_SOURCE_ID,
        paint: {
          'circle-radius': ['interpolate', ['linear'], ['zoom'], 1, 3.5, 10, 8],
          'circle-color': ['get', 'color'],
          'circle-opacity': 0.8,
          'circle-blur': 0.25,
        },
      })

      // 십자 성광 + 백색 코어
      map.addLayer({
        id: 'events-color-flare',
        type: 'symbol',
        source: EVENTS_SOURCE_ID,
        layout: {
          'icon-image': STAR_FLARE_IMAGE_ID,
          'icon-size': ['interpolate', ['linear'], ['zoom'], 1, 0.18, 10, 0.38],
          'icon-allow-overlap': true,
          'icon-ignore-placement': true,
        },
        paint: {
          'icon-color': ['get', 'color'],
          'icon-opacity': 0.95,
        },
      })

      map.addLayer({
        id: 'events-white-core',
        type: 'symbol',
        source: EVENTS_SOURCE_ID,
        layout: {
          'icon-image': STAR_FLARE_IMAGE_ID,
          'icon-size': ['interpolate', ['linear'], ['zoom'], 1, 0.09, 10, 0.2],
          'icon-allow-overlap': true,
          'icon-ignore-placement': true,
        },
        paint: {
          'icon-color': '#ffffff',
          'icon-opacity': 1.0,
        },
      })

      // 임시 별. 아직 저장되지 않았음을 드러내기 위해 무채색으로, 확정된 별보다 크고
      // 옅게 그린다. 확정된 별 위에 올라오도록 마지막에 추가한다.
      map.addSource(PENDING_SOURCE_ID, {
        type: 'geojson',
        data: toPendingGeoJson(null),
      })

      map.addLayer({
        id: 'pending-star-aura',
        type: 'circle',
        source: PENDING_SOURCE_ID,
        paint: {
          'circle-radius': ['interpolate', ['linear'], ['zoom'], 1, 11, 10, 26],
          'circle-color': '#cfe4ff',
          'circle-opacity': 0.32,
          'circle-blur': 0.9,
        },
      })

      map.addLayer({
        id: 'pending-star-flare',
        type: 'symbol',
        source: PENDING_SOURCE_ID,
        layout: {
          'icon-image': STAR_FLARE_IMAGE_ID,
          'icon-size': ['interpolate', ['linear'], ['zoom'], 1, 0.26, 10, 0.5],
          'icon-allow-overlap': true,
          'icon-ignore-placement': true,
        },
        paint: {
          'icon-color': '#ffffff',
          'icon-opacity': 0.9,
        },
      })

      STAR_LAYER_IDS.forEach((layerId) => {
        map.on('click', layerId, (e: MapLayerMouseEvent) => {
          suppressNextMapClickRef.current = true
          const feature = e.features?.[0]
          const id = feature?.properties?.id
          if (typeof id === 'number') onSelectRef.current(id)
        })
        map.on('mouseenter', layerId, () => {
          map.getCanvas().style.cursor = 'pointer'
        })
        map.on('mouseleave', layerId, () => {
          map.getCanvas().style.cursor = placingRef.current ? 'crosshair' : ''
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

    // 우클릭으로 그 지점에 임시 별을 찍는다. 브라우저 기본 컨텍스트 메뉴는 막는다.
    map.on('contextmenu', (e: MapMouseEvent) => {
      e.originalEvent.preventDefault()
      onPlaceLocationRef.current(e.lngLat.lat, e.lngLat.lng)
    })

    map.on('click', (e: MapMouseEvent) => {
      if (suppressNextMapClickRef.current) {
        suppressNextMapClickRef.current = false
        return
      }
      if (placingRef.current) {
        onPlaceLocationRef.current(e.lngLat.lat, e.lngLat.lng)
      } else {
        onSelectRef.current(null)
      }
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

    let rafId: number
    const rotate = () => {
      if (!isInteractingRef.current && map.getZoom() < AUTO_ROTATE_MAX_ZOOM) {
        const center = map.getCenter()
        center.lng -= AUTO_ROTATE_DEGREES_PER_FRAME
        map.setCenter(center)
      }
      updateHalo()
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
    return whenStyleReady(map, () => {
      const source = map.getSource(EVENTS_SOURCE_ID) as GeoJSONSource | undefined
      source?.setData(toGeoJson(events))
    })
  }, [events])

  // 우클릭으로 찍은 임시 별을 지도에 반영
  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    return whenStyleReady(map, () => {
      const source = map.getSource(PENDING_SOURCE_ID) as GeoJSONSource | undefined
      source?.setData(toPendingGeoJson(pendingLocation))
    })
  }, [pendingLocation])

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
      speed: 1.2,
      curve: 1.4,
      essential: true,
    })
    const timer = setTimeout(() => {
      isInteractingRef.current = false
    }, 2000)
    return () => clearTimeout(timer)
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

  // 배치(이벤트 등록) 모드일 때 커서를 십자선으로
  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    const canvas = map.getCanvas()
    canvas.style.cursor = placing ? 'crosshair' : ''
  }, [placing])

  return (
    <div className="map-globe" ref={rootRef}>
      <Starfield />
      <div ref={containerRef} className="map-globe__container" />
      <div ref={haloRef} className="globe-halo" />
    </div>
  )
}
