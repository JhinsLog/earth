import type { ExpressionSpecification, GeoJSONSource, Map as MapLibreMap } from 'maplibre-gl'
import type { Quake } from '../../types'
import { QUAKE_CORE, QUAKE_WARM, RING_COUNT, quakeLook } from './quakeShader'

/**
 * 지도(MapLibre 라스) 위의 지진: 진앙의 점과 그 둘레로 퍼지는 원(규칙은 quakeShader.ts).
 *
 * MapLibre 원 레이어(circle)로 그린다. 반지름이 화면 픽셀이라 확대해도 매끈하고, 원이 영향 범위로 읽히지 않는다.
 * 'circle-pitch-alignment: map'으로 원을 지표면에 눕혀, 라스 가장자리에서는 곡면을 따라 납작해 보인다.
 * 원이 퍼지는 정도는 지진마다 feature-state(t0~t2)로 매 프레임 넣는다. 처음에는 시각만 바꾼 식을 setPaintProperty로
 * 넣었는데(플레어 반짝임 방식), 데이터를 쓰는 식(['get', …])을 바꾸면 MapLibre가 소스를 워커에서 처음부터 다시 처리해,
 * 라스 배율(타일이 많다)에서는 처리가 끝나기 전에 다음 프레임이 와 지진이 한 번도 그려지지 않았다(2026-11-17 헤드리스).
 * feature-state는 다시 처리하지 않고 그리는 값만 바꾼다.
 */

export const QUAKES_SOURCE_ID = 'earth-quakes-source'
/** 누르기 판정. 점이 작아(3~6px) 손가락으로 누르기 어려워 보이지 않는 14px 원을 따로 둔다. */
export const QUAKE_HIT_LAYER_ID = 'quakes-hit'
const RING_LAYER_IDS = Array.from({ length: RING_COUNT }, (_, k) => `quakes-ring-${k}`)
const HALO_LAYER_ID = 'quakes-halo'
const DOT_LAYER_ID = 'quakes-dot'
// 초당 30번이면 충분히 부드럽다.
const FRAME_MS = 1000 / 30

export function toQuakeGeoJson(quakes: Quake[], now = Date.now()) {
  return {
    type: 'FeatureCollection' as const,
    features: quakes.map((q) => {
      const look = quakeLook(q, now)
      return {
        type: 'Feature' as const,
        geometry: { type: 'Point' as const, coordinates: [q.longitude, q.latitude] },
        properties: { id: q.id, ...look },
      }
    }),
  }
}

/** k번째 원이 지금 얼마나 퍼졌는지(0~1). animateQuakes가 넣는다. */
function progress(k: number): ExpressionSpecification {
  return ['coalesce', ['feature-state', `t${k}`], 0]
}

function ringPaint(k: number) {
  const t = progress(k)
  return {
    radius: ['*', ['get', 'reachPx'], t] as ExpressionSpecification,
    // 퍼질수록 옅어지고, 막 생길 때는 진앙 점과 겹치지 않게 잠깐 숨긴다.
    opacity: ['*', ['get', 'strength'], ['^', ['-', 1, t], 1.5], ['min', 1, ['/', t, 0.08]]] as ExpressionSpecification,
  }
}

export function addQuakeLayers(map: MapLibreMap, quakes: Quake[], attribution: string, beforeId?: string) {
  // feature-state는 지진 id로 찾는다.
  map.addSource(QUAKES_SOURCE_ID, { type: 'geojson', data: toQuakeGeoJson(quakes), attribution, promoteId: 'id' })
  rememberTiming(quakes)
  RING_LAYER_IDS.forEach((id, k) => {
    const paint = ringPaint(k)
    map.addLayer(
      {
        id,
        type: 'circle',
        source: QUAKES_SOURCE_ID,
        paint: {
          'circle-radius': paint.radius,
          'circle-color': QUAKE_WARM,
          'circle-opacity': 0,
          'circle-stroke-color': QUAKE_WARM,
          'circle-stroke-width': 1.6,
          'circle-stroke-opacity': paint.opacity,
        },
      },
      beforeId,
    )
  })
  map.addLayer(
    {
      id: HALO_LAYER_ID,
      type: 'circle',
      source: QUAKES_SOURCE_ID,
      paint: {
        'circle-radius': ['*', ['get', 'dotPx'], 2.6],
        'circle-color': QUAKE_WARM,
        'circle-blur': 1,
        'circle-opacity': ['*', 0.6, ['get', 'strength']],
      },
    },
    beforeId,
  )
  map.addLayer(
    {
      id: DOT_LAYER_ID,
      type: 'circle',
      source: QUAKES_SOURCE_ID,
      paint: {
        'circle-radius': ['get', 'dotPx'],
        'circle-color': QUAKE_CORE,
        'circle-opacity': ['max', 0.6, ['get', 'strength']],
        'circle-stroke-color': QUAKE_WARM,
        'circle-stroke-width': 1.2,
        'circle-stroke-opacity': ['get', 'strength'],
      },
    },
    beforeId,
  )
  map.addLayer(
    {
      id: QUAKE_HIT_LAYER_ID,
      type: 'circle',
      source: QUAKES_SOURCE_ID,
      paint: { 'circle-radius': 14, 'circle-opacity': 0, 'circle-stroke-opacity': 0 },
    },
    beforeId,
  )
}

/** 목록이 바뀌었거나(1분마다) 경과 시간에 따른 밝기를 새로 반영할 때. */
export function setQuakeData(map: MapLibreMap, quakes: Quake[]) {
  const source = map.getSource(QUAKES_SOURCE_ID) as GeoJSONSource | undefined
  source?.setData(toQuakeGeoJson(quakes))
  rememberTiming(quakes)
}

/** 지진마다 원이 퍼지는 박자. 데이터가 바뀔 때만 다시 잰다. */
let timings: { id: string; period: number; phase: number }[] = []

function rememberTiming(quakes: Quake[]) {
  const now = Date.now()
  timings = quakes.map((q) => {
    const look = quakeLook(q, now)
    return { id: q.id, period: look.period, phase: look.phase }
  })
}

let lastFrame = 0

/** 렌더 루프(MapGlobe rotate)에서 매 프레임 부른다. 지진이 없으면 아무것도 하지 않는다. */
export function animateQuakes(map: MapLibreMap) {
  const now = performance.now()
  if (timings.length === 0 || now - lastFrame < FRAME_MS || !map.getSource(QUAKES_SOURCE_ID)) return
  lastFrame = now
  const time = now / 1000
  for (const { id, period, phase } of timings) {
    const state: Record<string, number> = {}
    for (let k = 0; k < RING_COUNT; k++) {
      const x = time / period + phase + k / RING_COUNT
      state[`t${k}`] = x - Math.floor(x)
    }
    map.setFeatureState({ source: QUAKES_SOURCE_ID, id }, state)
  }
}
