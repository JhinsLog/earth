import type { ExpressionSpecification, StyleSpecification } from 'maplibre-gl'

/**
 * API 키가 필요 없는 무료 소스만으로 구성한 통합 스타일.
 *
 * - 최저 줌(우주에서 보는 지구본): NASA GIBS의 Blue Marble Next Generation
 *   실사 이미지를 그대로 사용 — 대륙 전체가 선명하게 보이는 "지구본" 퀄리티를 위함.
 * - 줌 3~7: NASA 이미지 → Esri 위성 이미지로 크로스페이드 (더 높은 해상도로 자연스럽게 전환).
 * - 줌 8~10.5: Esri 위성 → Esri Dark Gray Canvas로 크로스페이드 — 확대할수록 배경이
 *   어두워져 이벤트(별) 마커가 도드라지게 하기 위함. 라벨이 없는 Base 레이어를 써서
 *   아래 벡터 라벨과 중복되지 않게 한다.
 *   (원래 CARTO Dark Matter를 썼으나 CARTO가 무인증 사용을 막으면서 타일에
 *    "API KEY REQUIRED" 워터마크가 찍혀 나와 키가 필요 없는 Esri로 교체했다.)
 * - 국경선/지명 라벨은 OpenFreeMap(OpenMapTiles 스키마)의 벡터 타일을 사용 — 언어별
 *   name:xx 필드를 갖고 있어 접속 언어에 맞춰 동적으로 라벨을 바꿀 수 있다.
 *   저줌에서는 국가명만, 줌이 깊어질수록 도/성 → 도시 → 마을 순으로 단계적으로 나타난다.
 */
export function createEarthMapStyle(labelLanguage: string): StyleSpecification {
  const nameField: ExpressionSpecification = [
    'coalesce',
    ['get', `name:${labelLanguage}`],
    ['get', 'name_en'],
    ['get', 'name'],
  ]

  const labelPaint = {
    'text-color': '#ffffff',
    'text-halo-color': 'rgba(0, 0, 0, 0.8)',
    'text-halo-width': 1.2,
  } as const

  return {
    version: 8,
    // 첫 프레임부터 globe로 그린다. setProjection을 on('load')에서 호출하면
    // 그 전에 기본값인 mercator(평면 지도)로 최소 한 프레임이 렌더링된다.
    projection: { type: 'globe' },
    glyphs: 'https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf',
    sources: {
      'nasa-blue-marble': {
        type: 'raster',
        // GIBS는 'no-store, no-cache'로 응답해 브라우저가 타일을 캐싱할 수 없다.
        // 줌/회전할 때마다 매번 300ms를 들여 다시 받아오느라 타일이 제각각 도착하고,
        // 색감이 다른 Esri와 겹치는 구간에서 사각형 경계로 드러났다.
        // 크로스페이드가 줌 4.5에서 끝나므로 z0~z4(341장 / 2.5MB)만 있으면 충분해
        // 정적 파일로 직접 서빙한다. NASA 자료는 퍼블릭 도메인.
        tiles: ['/tiles/bluemarble/{z}/{y}/{x}.jpg'],
        tileSize: 256,
        attribution:
          'Imagery provided by NASA Global Imagery Browse Services (GIBS), part of NASA ESDIS',
        maxzoom: 4,
      },
      'esri-satellite': {
        type: 'raster',
        tiles: [
          'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
        ],
        tileSize: 256,
        attribution: 'Tiles &copy; Esri, Maxar, Earthstar Geographics, and the GIS User Community',
        maxzoom: 19,
      },
      'esri-dark-gray': {
        type: 'raster',
        tiles: [
          'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}',
        ],
        tileSize: 256,
        attribution:
          'Tiles &copy; Esri, HERE, Garmin, &copy; OpenStreetMap contributors, and the GIS user community',
        maxzoom: 16,
      },
      openmaptiles: {
        type: 'vector',
        url: 'https://tiles.openfreemap.org/planet',
        // OpenStreetMap 파생 데이터라 ODbL에 따라 출처 표기가 의무 사항이다.
        attribution:
          '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &middot; ' +
          '<a href="https://openfreemap.org/">OpenFreeMap</a>',
      },
    },
    layers: [
      // 타일이 아직 로딩되지 않은 영역이 우주(검정)로 비쳐 보이는 것을 막는다.
      // 지구본 표면 전체를 덮는 바닥색이므로 반드시 첫 번째 레이어여야 한다.
      {
        id: 'globe-background',
        type: 'background',
        paint: { 'background-color': '#0a2038' },
      },
      {
        id: 'nasa-blue-marble-base',
        type: 'raster',
        source: 'nasa-blue-marble',
        minzoom: 0,
        maxzoom: 4.5,
        paint: {
          'raster-fade-duration': 0,
          'raster-opacity': ['interpolate', ['linear'], ['zoom'], 3.0, 1.0, 4.5, 0.0],
        },
      },
      {
        id: 'satellite-base',
        type: 'raster',
        source: 'esri-satellite',
        minzoom: 2.5,
        maxzoom: 11,
        paint: {
          'raster-fade-duration': 0,
          'raster-opacity': ['interpolate', ['linear'], ['zoom'], 3.0, 0.0, 4.5, 1.0],
        },
      },
      {
        id: 'dark-base',
        type: 'raster',
        source: 'esri-dark-gray',
        minzoom: 7.5,
        maxzoom: 24,
        paint: {
          'raster-fade-duration': 0,
          'raster-opacity': ['interpolate', ['linear'], ['zoom'], 8.0, 0.0, 10.5, 1.0],
          // Esri Dark Gray는 중간 회색이라 그대로 쓰면 별빛이 묻힌다.
          // 밝기 상한을 눌러 밤하늘에 가깝게 만든다.
          'raster-brightness-max': 0.4,
        },
      },
      {
        id: 'country-boundaries',
        type: 'line',
        source: 'openmaptiles',
        'source-layer': 'boundary',
        filter: ['all', ['<=', ['get', 'admin_level'], 2], ['!=', ['get', 'maritime'], 1]],
        paint: {
          'line-color': '#ffffff',
          'line-width': ['interpolate', ['linear'], ['zoom'], 2, 0.4, 8, 1.1],
          'line-opacity': 0.55,
        },
      },
      // 국가명 — 우주에서 보이는 줌부터 항상 표시
      {
        id: 'place-country-labels',
        type: 'symbol',
        source: 'openmaptiles',
        'source-layer': 'place',
        minzoom: 1,
        filter: ['==', ['get', 'class'], 'country'],
        layout: {
          'text-field': nameField,
          'text-font': ['Noto Sans Bold'],
          'text-size': ['interpolate', ['linear'], ['zoom'], 1, 11, 5, 17],
          'text-letter-spacing': 0.05,
        },
        paint: labelPaint,
      },
      // 도/성 단위 — 국가 안쪽이 어느 정도 보이기 시작하는 줌부터
      {
        id: 'place-state-labels',
        type: 'symbol',
        source: 'openmaptiles',
        'source-layer': 'place',
        minzoom: 5,
        filter: ['==', ['get', 'class'], 'state'],
        layout: {
          'text-field': nameField,
          'text-font': ['Noto Sans Bold'],
          'text-size': ['interpolate', ['linear'], ['zoom'], 5, 11, 9, 14],
        },
        paint: labelPaint,
      },
      // 도시 단위 — 한 국가/지역이 화면에 꽉 차는 줌부터
      {
        id: 'place-city-labels',
        type: 'symbol',
        source: 'openmaptiles',
        'source-layer': 'place',
        minzoom: 5.5,
        filter: ['in', ['get', 'class'], ['literal', ['city', 'town']]],
        layout: {
          'text-field': nameField,
          'text-font': ['Noto Sans Regular'],
          'text-size': ['interpolate', ['linear'], ['zoom'], 5.5, 10, 10, 14],
        },
        paint: labelPaint,
      },
      // 마을 단위 — 다크 스타일로 전환되는 최고 줌 구간에서만
      {
        id: 'place-village-labels',
        type: 'symbol',
        source: 'openmaptiles',
        'source-layer': 'place',
        minzoom: 9,
        filter: ['==', ['get', 'class'], 'village'],
        layout: {
          'text-field': nameField,
          'text-font': ['Noto Sans Regular'],
          'text-size': ['interpolate', ['linear'], ['zoom'], 9, 10, 12, 13],
        },
        paint: labelPaint,
      },
    ],
  }
}
