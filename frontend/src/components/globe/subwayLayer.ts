import type { GeoJSONSource, Map as MapLibreMap } from 'maplibre-gl'
import { SUBWAY_FULL_ZOOM, SUBWAY_MIN_ZOOM } from './constants'

/**
 * 국내 지하철·전철 노선. 노선마다 공식 색으로 그린다.
 *
 * 지도 타일(OpenMapTiles)에도 지하철 선이 있지만 줌 14부터만 들어 있고 노선 색이 없다(실측: 서울 타일의
 * transit/subway는 z14에서 처음 나옴). 도시 전체가 보이는 줌에서 노선을 보여 주려고 OpenStreetMap의
 * 노선 관계(route relation)에서 뽑은 GeoJSON을 따로 둔다. 만드는 방법은 frontend/scripts/subway/.
 */
const SOURCE_ID = 'earth-subway-source'
const LAYER_ID = 'subway-lines'
const DATA_URL = '/data/subway-kr.geojson'

/** 어두운 배경 위, 국경선·지명·플레어 아래. 낮·밤 레이어보다 위라 밤에도 노선이 보인다. */
export function addSubwayLayer(map: MapLibreMap, beforeId: string, visible: boolean) {
  // 처음에는 비워 둔다. 데이터(약 100KB, gzip)는 실제로 그 줌에 닿았을 때만 받는다(ensureSubwayData).
  map.addSource(SOURCE_ID, {
    type: 'geojson',
    data: { type: 'FeatureCollection', features: [] },
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  })
  map.addLayer(
    {
      id: LAYER_ID,
      type: 'line',
      source: SOURCE_ID,
      minzoom: SUBWAY_MIN_ZOOM,
      layout: {
        visibility: visible ? 'visible' : 'none',
        'line-join': 'round',
        'line-cap': 'round',
      },
      paint: {
        'line-color': ['get', 'colour'],
        'line-width': ['interpolate', ['linear'], ['zoom'], SUBWAY_MIN_ZOOM, 1, 13, 2, 16, 4],
        // 어두운 배경이 다 깔릴 때(SUBWAY_FULL_ZOOM) 함께 다 나타난다. 위성사진 위에 색선이 먼저 뜨지 않게 한다.
        'line-opacity': ['interpolate', ['linear'], ['zoom'], SUBWAY_MIN_ZOOM, 0, SUBWAY_FULL_ZOOM, 0.9],
      },
    },
    beforeId,
  )
}

/**
 * 데이터를 이미 요청한 지도. 라스로 돌아갔다 오면 지도가 새로 만들어지므로 모듈 전역 플래그 하나로 두면
 * 새 지도에는 데이터가 다시 들어가지 않는다. 지도마다 기억한다(브라우저가 파일을 캐시하므로 다시 받아도 싸다).
 */
const requested = new WeakSet<MapLibreMap>()

/** 켜져 있고 노선이 보일 줌에 가까워졌을 때 한 번만 데이터를 받는다. */
export function ensureSubwayData(map: MapLibreMap, visible: boolean) {
  if (requested.has(map) || !visible || map.getZoom() < SUBWAY_MIN_ZOOM - 0.5) return
  const source = map.getSource(SOURCE_ID) as GeoJSONSource | undefined
  if (!source) return
  requested.add(map)
  source.setData(DATA_URL)
}

export function setSubwayVisible(map: MapLibreMap, visible: boolean) {
  if (!map.getLayer(LAYER_ID)) return
  map.setLayoutProperty(LAYER_ID, 'visibility', visible ? 'visible' : 'none')
  ensureSubwayData(map, visible)
}
