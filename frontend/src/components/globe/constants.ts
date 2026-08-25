export const INITIAL_ZOOM = 1.5
export const MIN_ZOOM = 1.0
export const MAX_ZOOM = 22.0
export const AUTO_ROTATE_MAX_ZOOM = 5 // 이 줌보다 확대하면 자동 자전을 멈춘다
export const EVENT_FOCUS_ZOOM = 11.0 // 이벤트를 선택했을 때 확대되는 줌 레벨
export const COUNTRY_FOCUS_ZOOM = 4.0 // 접속 지역으로 처음 진입할 때의 줌 레벨

/** 1시간에 1바퀴 자전 (60fps 기준 프레임당 이동 각도). */
export const AUTO_ROTATE_DEGREES_PER_FRAME = 360 / (3600 * 60)

/** 현재 보고 있는 중심에서 이 거리(km) 안의 국가만 이름을 표시한다 — 시야가 분산되지 않도록. */
export const COUNTRY_LABEL_MAX_DISTANCE_KM = 3000
