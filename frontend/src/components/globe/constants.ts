export const INITIAL_ZOOM = 1.5
/**
 * 첫 화면(지구본 전체)의 중심 위도 상한. 접속 위치의 위도를 그대로 쓰되 이 값으로 자른다.
 * 고위도에 있는 접속자에게 지구본이 극지방만 크게 보이는 화면을 주지 않기 위함이다.
 * 경도는 고정값 없이 접속 지역에서 구한다 — 그래야 첫 화면이 자기 대륙을 보여준다.
 */
export const HOME_VIEW_MAX_ABS_LATITUDE = 55
export const MIN_ZOOM = 1.0
export const MAX_ZOOM = 22.0
export const AUTO_ROTATE_MAX_ZOOM = 5 // 이 줌보다 확대하면 자동 자전을 멈춘다
/**
 * 이벤트를 선택했을 때 확대되는 줌 레벨 — 타일 원본 해상도의 한계.
 *
 * 배경-도로-도로명이 모두 Esri Dark Gray 래스터 한 장에서 나오고 이 타일은 z16까지만
 * 존재한다. z16을 넘기면 없는 타일을 늘려 그리는 것이라 (z17=2배, z20=16배) 확대가 아니라
 * 화질 저하일 뿐이다. 뭉개진 지도를 보여주지 않기 위해 원본이 존재하는 최대치인 16에 맞춘다.
 * 이보다 더 당기려면 도로를 벡터 레이어(openmaptiles transportation)로 그려야 한다.
 */
export const EVENT_FOCUS_ZOOM = 16.0

/** 우측 상세 패널 너비(px). EventDetailPanel.css의 `.event-panel { width }`와 맞춰야 한다. */
export const EVENT_PANEL_WIDTH_PX = 360
export const COUNTRY_FOCUS_ZOOM = 4.0 // 접속 지역으로 처음 진입할 때의 줌 레벨

/** 1시간에 1바퀴 자전 (60fps 기준 프레임당 이동 각도). */
export const AUTO_ROTATE_DEGREES_PER_FRAME = 360 / (3600 * 60)

/** 현재 보고 있는 중심에서 이 거리(km) 안의 국가만 이름을 표시한다 — 시야가 분산되지 않도록. */
export const COUNTRY_LABEL_MAX_DISTANCE_KM = 3000

/** 내용을 입력해 등록하지 않은 임시 별이 지도에서 사라지기까지의 시간. */
export const DRAFT_STAR_LIFETIME_MS = 5 * 60 * 1000

/** 임시 별 색상 — 등록된 별(카테고리 색)과 구분되도록 중립적인 하늘색을 쓴다. */
export const DRAFT_STAR_COLOR = '#9fd6ff'

/**
 * "내 위치" 버튼이 GPS 좌표로 이동할 때의 줌 — 도로와 건물이 구분되는 수준.
 *
 * 배경 래스터(Esri Dark Gray)가 z16까지만 존재하므로 그 아래인 15는 원본 타일이
 * 그대로 그려지는 안전한 값이다. EVENT_FOCUS_ZOOM의 주석 참고.
 */
export const MY_LOCATION_ZOOM = 15.0

/**
 * GPS를 못 써서 IP로만 위치를 알아냈을 때의 줌.
 *
 * IP 위치는 도시나 ISP 관문 단위라 실제와 수 km 어긋나는 일이 흔하다. 그 값을 줌 15로
 * 당기면 틀린 골목을 "내 위치"라고 단언하는 화면이 된다. 데이터의 실제 정확도에 맞춰
 * 구/시 단위로만 보여주고, 화면에도 대략적인 위치임을 표시한다.
 */
export const MY_LOCATION_APPROX_ZOOM = 12.0

/** 내 위치 표시 색 — 별(이벤트)과 혼동되지 않도록 채도가 다른 청록을 쓴다. */
export const MY_LOCATION_COLOR = '#4dd2ff'
