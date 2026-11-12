import type { ExpressionSpecification, GeoJSONSource, LineLayerSpecification, Map as MapLibreMap } from 'maplibre-gl'
import {
  STATION_LABEL_MIN_ZOOM,
  STATION_MIN_ZOOM,
  SUBWAY_FULL_ZOOM,
  SUBWAY_MIN_ZOOM,
  TRANSFER_LABEL_MIN_ZOOM,
} from './constants'

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
// 역은 노선 관계의 정차 위치에서 뽑아 같은 역끼리 묶었다(scripts/subway/stations.py). 노선 스위치를 같이 따른다.
const STATION_SOURCE_ID = 'earth-subway-station-source'
const STATION_LAYER_ID = 'subway-stations'
const STATION_LABEL_LAYER_ID = 'subway-station-labels'
// 노선끼리 만나지 않는 환승역에서 점과 다른 노선을 잇는 짧은 선. 역 데이터에 LineString으로 들어 있다.
const STATION_LINK_LAYER_ID = 'subway-station-links'
const STATION_DATA_URL = '/data/subway-stations-kr.geojson'
const LAYER_IDS = [LAYER_ID, STATION_LINK_LAYER_ID, STATION_LAYER_ID, STATION_LABEL_LAYER_ID]
const OSM_ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
const EMPTY = { type: 'FeatureCollection', features: [] } as const

/**
 * 어두운 배경 위, 국경선·지명·플레어 아래. 낮·밤 레이어보다 위라 밤에도 노선이 보인다.
 * 역 이름은 지명보다 아래 레이어라, 겹치면 지명이 남고 역 이름이 빠진다.
 */
export function addSubwayLayer(map: MapLibreMap, beforeId: string, visible: boolean, labelLanguage: string) {
  // 처음에는 비워 둔다. 데이터(노선 약 65KB + 역 약 25KB, gzip)는 실제로 그 줌에 닿았을 때만 받는다(ensureSubwayData).
  map.addSource(SOURCE_ID, { type: 'geojson', data: EMPTY, attribution: OSM_ATTRIBUTION })
  map.addSource(STATION_SOURCE_ID, { type: 'geojson', data: EMPTY, attribution: OSM_ATTRIBUTION })
  const linePaint = {
    'line-color': ['get', 'colour'],
    'line-width': ['interpolate', ['linear'], ['zoom'], SUBWAY_MIN_ZOOM, 1, 13, 2, 16, 4],
    // 어두운 배경이 다 깔릴 때(SUBWAY_FULL_ZOOM) 함께 다 나타난다. 위성사진 위에 색선이 먼저 뜨지 않게 한다.
    'line-opacity': ['interpolate', ['linear'], ['zoom'], SUBWAY_MIN_ZOOM, 0, SUBWAY_FULL_ZOOM, 0.9],
  } satisfies LineLayerSpecification['paint']
  const lineLayout = {
    visibility: visible ? 'visible' : 'none',
    'line-join': 'round',
    'line-cap': 'round',
  } satisfies LineLayerSpecification['layout']
  map.addLayer(
    { id: LAYER_ID, type: 'line', source: SOURCE_ID, minzoom: SUBWAY_MIN_ZOOM, layout: lineLayout, paint: linePaint },
    beforeId,
  )
  // 연결선은 역 점이 보이는 줌부터 그린다. 가까운 것은 노선의 연장이라 노선과 같은 모양, 승강장이 멀리 떨어진
  // 환승(walk)은 갈아타는 통로라 가는 흰 선이다 — 노선 색으로 그리면 가지 노선처럼 보인다.
  const isPoint: ExpressionSpecification = ['==', ['geometry-type'], 'Point']
  const isWalk: ExpressionSpecification = ['==', ['get', 'walk'], true]
  map.addLayer(
    {
      id: STATION_LINK_LAYER_ID,
      type: 'line',
      source: STATION_SOURCE_ID,
      minzoom: STATION_MIN_ZOOM,
      filter: ['==', ['geometry-type'], 'LineString'],
      layout: lineLayout,
      paint: {
        ...linePaint,
        'line-color': ['case', isWalk, '#e6e9ef', ['get', 'colour']],
        'line-width': [
          'interpolate', ['linear'], ['zoom'],
          13, ['case', isWalk, 1, 2],
          16, ['case', isWalk, 2, 4],
        ],
      },
    },
    beforeId,
  )

  const isTransfer: ExpressionSpecification = ['==', ['get', 'transfer'], true]
  // 일반역은 노선 색 테두리의 작은 점, 환승역은 조금 더 큰 흰 점에 어두운 테두리(노선도 관례).
  map.addLayer(
    {
      id: STATION_LAYER_ID,
      type: 'circle',
      source: STATION_SOURCE_ID,
      minzoom: STATION_MIN_ZOOM,
      filter: isPoint,
      layout: { visibility: visible ? 'visible' : 'none' },
      paint: {
        'circle-radius': [
          'interpolate', ['linear'], ['zoom'],
          STATION_MIN_ZOOM, ['case', isTransfer, 3, 2],
          16, ['case', isTransfer, 7, 5],
        ],
        'circle-color': '#ffffff',
        'circle-stroke-color': ['case', isTransfer, '#1f2430', ['get', 'colour']],
        'circle-stroke-width': ['interpolate', ['linear'], ['zoom'], STATION_MIN_ZOOM, 1, 16, 2],
        'circle-opacity': ['interpolate', ['linear'], ['zoom'], STATION_MIN_ZOOM, 0, STATION_MIN_ZOOM + 0.5, 1],
        'circle-stroke-opacity': ['interpolate', ['linear'], ['zoom'], STATION_MIN_ZOOM, 0, STATION_MIN_ZOOM + 0.5, 1],
      },
    },
    beforeId,
  )

  // 한국어 화면이면 한글 역명, 그 밖에는 영문 역명이 있으면 그것을 쓴다(지명 라벨과 같은 규칙).
  const nameField: ExpressionSpecification =
    labelLanguage === 'ko' ? ['get', 'name'] : ['coalesce', ['get', 'name_en'], ['get', 'name']]
  map.addLayer(
    {
      id: STATION_LABEL_LAYER_ID,
      type: 'symbol',
      source: STATION_SOURCE_ID,
      minzoom: TRANSFER_LABEL_MIN_ZOOM,
      // 일반역 이름은 STATION_LABEL_MIN_ZOOM부터. minzoom은 레이어 단위라 필터로 나눈다.
      filter: ['all', isPoint, ['any', isTransfer, ['>=', ['zoom'], STATION_LABEL_MIN_ZOOM]]],
      layout: {
        visibility: visible ? 'visible' : 'none',
        'text-field': nameField,
        'text-font': ['Noto Sans Regular'],
        'text-size': ['interpolate', ['linear'], ['zoom'], TRANSFER_LABEL_MIN_ZOOM, 10, 16, 13],
        'text-anchor': 'top',
        'text-offset': [0, 0.6],
        // 겹치면 환승역 이름을 먼저 남긴다.
        'symbol-sort-key': ['case', isTransfer, 0, 1],
      },
      paint: {
        'text-color': '#e6e9ef',
        'text-halo-color': 'rgba(0, 0, 0, 0.85)',
        'text-halo-width': 1.2,
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
  const stations = map.getSource(STATION_SOURCE_ID) as GeoJSONSource | undefined
  stations?.setData(STATION_DATA_URL)
}

export function setSubwayVisible(map: MapLibreMap, visible: boolean) {
  for (const id of LAYER_IDS) {
    if (map.getLayer(id)) map.setLayoutProperty(id, 'visibility', visible ? 'visible' : 'none')
  }
  ensureSubwayData(map, visible)
}
