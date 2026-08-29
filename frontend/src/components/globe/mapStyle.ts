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
/**
 * 어두운 배경(Esri Dark Gray) 레이어의 줌별 불투명도 램프.
 *
 * 이 레이어는 Esri 위성 위에 부분 불투명도로 덮여 배경을 어둡게 만든다. 그런데 타일이
 * 일부만 도착한 상태로 그려지면 도착한 타일 영역만 어두워져 사각형 경계가 그대로
 * 드러난다(줌 8.6 기준 실효 불투명도 0.24). 그래서 MapGlobe에서 소스가 완전히
 * 준비되기 전에는 0으로 눌러두고 준비된 뒤 이 램프를 되돌린다. 양쪽에서 같은 값을
 * 써야 하므로 상수로 공유한다.
 */
/**
 * Esri 위성 레이어의 줌별 불투명도 램프.
 *
 * NASA(항상 불투명도 1)가 아래 깔려 있는 상태에서 이 값만큼 위에 덮인다.
 */
export const ESRI_OPACITY: ExpressionSpecification = [
  'interpolate',
  ['linear'],
  ['zoom'],
  3.0,
  0.0,
  4.5,
  1.0,
]

/**
 * Esri를 타일 준비 상태로 게이팅할 최대 줌.
 *
 * NASA와 Esri는 색보정이 완전히 달라, 크로스페이드 구간에서 Esri 타일이 하나씩
 * 도착하면 도착한 타일만 색이 달라져 사각형 경계로 드러난다. 그 구간에서는 소스가
 * 준비될 때까지 Esri를 아예 감춘다(아래 NASA만 보이므로 화면은 계속 온전하다).
 *
 * 반대로 이 줌 위에서는 게이팅하지 않는다. Esri가 주 이미지라 항상 게이팅하면 화면
 * 전체가 흐려졌다 선명해지길 반복한다. 실측상 미준비 구간은 줌 4.5~5.7에 몰려 있고
 * (연속 줌 중 9%), 그 구간은 Esri가 이미 불투명도 1이라 색 혼합 문제가 없다.
 */
export const ESRI_GATE_MAX_ZOOM = 5.0

/**
 * 어두운 배경 레이어를 타일 준비 상태로 게이팅할 최대 줌.
 *
 * 게이팅은 "가려도 아래 레이어가 화면을 온전히 덮어줄 때"만 안전하다. 이 레이어가
 * 불투명도 1에 도달하는 줌 10.5 위로는 위성 레이어(maxzoom 11)도 사라져 이 레이어가 화면을
 * 혼자 책임지므로, 여기서 가리면 화면에 아무것도 남지 않는다.
 */
export const DARK_GATE_MAX_ZOOM = 10.5

export const DARK_BASE_OPACITY: ExpressionSpecification = [
  'interpolate',
  ['linear'],
  ['zoom'],
  8.0,
  0.0,
  10.5,
  1.0,
]

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
        // 저줌에서는 실사 지구본 그 자체이고, 고줌에서는 Esri 타일이 아직 도착하지 않은
        // 자리를 메우는 backfill 역할을 한다. 예전에는 줌 4.5에서 꺼버렸는데, 그러면
        // Esri 타일이 비는 순간 배경색이 그대로 드러나 사각형 경계가 보였다.
        //
        // 항상 불투명도 1로 깔아두어도 위에 덮이는 Esri가 불투명하므로 합성 결과는
        // 동일하다(alpha 합성상 Esri*a + NASA*(1-a)로 기존 크로스페이드와 같은 값).
        //
        // 다만 상한을 둔다. 소스 maxzoom이 4라 그 위에서는 z4 타일 한 장을 늘려 쓰는데,
        // 줌 6이면 이미 4배 확대라 흐릿하고 그 이상은 알아볼 수 없는 색 얼룩이 된다.
        // 백필이 필요한 구간은 Esri 게이팅이 걸리는 줌 5 아래이므로 6이면 충분하다.
        id: 'nasa-blue-marble-base',
        type: 'raster',
        source: 'nasa-blue-marble',
        minzoom: 0,
        maxzoom: 6,
        paint: {
          'raster-fade-duration': 0,
          'raster-opacity': 1,
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
          // 소스가 준비되면 MapGlobe가 ESRI_OPACITY 램프를 되돌려 준다.
          'raster-opacity': 0,
          'raster-opacity-transition': { duration: 400, delay: 0 },
        },
      },
      {
        id: 'dark-base',
        type: 'raster',
        source: 'esri-dark-gray',
        // 불투명도가 올라오기(줌 8) 전에 미리 타일을 받아두기 위해 레이어를 일찍 켠다.
        minzoom: 6.5,
        maxzoom: 24,
        paint: {
          'raster-fade-duration': 0,
          // 소스가 준비되면 MapGlobe가 이 램프를 되돌려 준다. 급격히 튀지 않도록 전환을 준다.
          'raster-opacity': 0,
          'raster-opacity-transition': { duration: 400, delay: 0 },
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
