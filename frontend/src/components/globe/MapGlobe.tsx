import { useEffect, useRef } from 'react'
import * as maplibregl from 'maplibre-gl'
import type { GeoJSONSource, Map as MapLibreMap, MapLayerMouseEvent, MapMouseEvent } from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { createEarthMapStyle } from './mapStyle'
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

interface Props {
  events: EarthEvent[]
  selectedEventId: number | null
  onSelect: (id: number | null) => void
  placing: boolean
  onPlaceLocation: (latitude: number, longitude: number) => void
  initialFocusLatLng?: GeoPoint | null
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
  selectedEventId,
  onSelect,
  placing,
  onPlaceLocation,
  initialFocusLatLng,
}: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const haloRef = useRef<HTMLDivElement>(null)
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
      attributionControl: false,
    })
    mapRef.current = map
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
      map.setProjection({ type: 'globe' })

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
      window.clearInterval(nearbyCountryIntervalId)
      map.remove()
      mapRef.current = null
    }
  }, [])

  // 이벤트 목록이 바뀔 때마다 지도 위 별 데이터 갱신
  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    const apply = () => {
      const source = map.getSource(EVENTS_SOURCE_ID) as GeoJSONSource | undefined
      source?.setData(toGeoJson(events))
    }
    if (map.isStyleLoaded()) apply()
    else map.once('load', apply)
  }, [events])

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
    appliedInitialFocusRef.current = true

    const apply = () => {
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
    if (map.loaded()) apply()
    else map.once('load', apply)
  }, [initialFocusLatLng])

  // 배치(이벤트 등록) 모드일 때 커서를 십자선으로
  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    const canvas = map.getCanvas()
    canvas.style.cursor = placing ? 'crosshair' : ''
  }, [placing])

  return (
    <div className="map-globe">
      <Starfield />
      <div ref={containerRef} className="map-globe__container" />
      <div ref={haloRef} className="globe-halo" />
    </div>
  )
}
