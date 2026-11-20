export type FlareCategory = 'FIRE' | 'ACCIDENT' | 'WEATHER' | 'CROWD' | 'LEISURE' | 'VOLCANO' | 'ETC'
/** HIDDEN은 신고가 쌓여 임시 비공개, REMOVED는 운영자 삭제(D-24). ACTIVE가 아니면 화면에서 걷어낸다. */
export type FlareStatus = 'ACTIVE' | 'CLOSED' | 'EXPIRED' | 'DELETED' | 'HIDDEN' | 'REMOVED'
/** 사실 확인의 종류. 한 사람은 한 플레어에 하나만 남긴다. SEEN만 수명을 늘린다(백엔드 ConfirmationKind, D-20). */
export type ConfirmationKind = 'SEEN' | 'ENDED' | 'DISPUTED'
/**
 * 플레어를 만든 주체(백엔드 FlareOrigin, D-22). USER는 사용자가 직접, PUBLIC은 공공 정보(교통 돌발 등 지금 벌어지는 상황),
 * FESTIVAL은 축제·행사(스키장 개장 등 공식 공지를 옮긴 일정). API는 V15 전의 값이라 배포가 엇갈린 몇 분 동안만 온다 — PUBLIC으로 다룬다.
 */
export type FlareOrigin = 'USER' | 'PUBLIC' | 'FESTIVAL' | 'API'

/**
 * 플레어 하나. 서버가 상세를 줄 수 있는지에 따라 두 모양 중 하나로 온다.
 * 비로그인 사용자가 접속 국가 밖의 플레어를 볼 때는 위치와 존재만(RestrictedFlare) 온다.
 * 판정은 서버가 하고, 화면은 restricted로 갈라서 쓴다.
 */
export type Flare = DetailedFlare | RestrictedFlare

export interface RestrictedFlare {
  id: number
  status: FlareStatus
  latitude: number
  longitude: number
  expiresAt: string
  countryCode: string | null
  restricted: true
}

export interface DetailedFlare {
  id: number
  title: string
  content: string | null
  category: FlareCategory
  status: FlareStatus
  latitude: number
  longitude: number
  authorId: number
  authorNickname: string
  createdAt: string
  /** 수정된 적이 없으면 null. */
  updatedAt: string | null
  /** 이 시각이 지나면 플레어는 라스에서 사라진다(기본 30분, 공감이 쌓이면 연장된다). */
  expiresAt: string
  /** "나도 봤어요"를 누른 사람 수. 플레어의 크기·밝기와 수명에 반영된다. */
  confirmCount: number
  /** "끝났어요"를 누른 사람 수. */
  endedCount: number
  /** "사실과 달라요"를 누른 사람 수. */
  disputedCount: number
  /** 지금 로그인한 사용자가 "나도 봤어요"를 눌렀는지(= myConfirmation === 'SEEN'). 비로그인이면 항상 false. */
  confirmedByMe: boolean
  /** 지금 로그인한 사용자가 남긴 사실 확인. 없으면 null. 실시간 전파로 온 값에는 늘 null이라 스토어가 이전 값을 지킨다. */
  myConfirmation: ConfirmationKind | null
  /** 플레어가 속한 국가(ISO 3166-1 alpha-2). 공해 등은 null. */
  countryCode: string | null
  restricted: false
  origin: FlareOrigin
  /** 채팅방을 여는지. 외부 데이터의 레저/행사 플레어(스키장 개장 등)는 false — 서버도 메시지를 거절한다. */
  chatOpen: boolean
  /** 사실 확인 버튼을 보여 주는지. 공식 안내를 옮긴 플레어(스키장 개장 등)는 false — 서버도 거절한다. */
  confirmationsOpen: boolean
}

/**
 * 아직 서버에 등록되지 않은 임시 플레어. 우클릭으로 찍은 직후 상태이며,
 * 생성자 본인 화면에만 보인다. 내용을 입력해 등록하지 않으면 일정 시간 뒤 사라진다.
 */
export interface DraftFlare {
  id: string
  latitude: number
  longitude: number
  createdAt: number
}

export interface EarthUser {
  id: number
  nickname: string
  level: number
  exp: number
  /** 닉네임을 다른 이름으로 다시 바꿀 수 있는 시각(ISO). 지금 바꿀 수 있으면 null. */
  nicknameChangeableAt: string | null
}

export interface ChatMessage {
  id: number
  flareId: number
  userId: number
  nickname: string
  content: string
  createdAt: string
}

export interface RegionSubscription {
  id: number
  label: string
  latitude: number
  longitude: number
  radiusKm: number
}

export const FLARE_CATEGORY_LABEL: Record<FlareCategory, string> = {
  FIRE: '화재',
  ACCIDENT: '사고',
  WEATHER: '기상',
  CROWD: '군중/혼잡',
  LEISURE: '레저/행사',
  VOLCANO: '화산',
  ETC: '기타',
}

/**
 * 사용자가 고를 수 있는 분류. 화산은 공식 기관 발표로만 표시하므로 뺀다(서버도 거부한다, D-25).
 * 순서는 이름의 가나다순이고, '기타'만 관례대로 맨 끝에 둔다(어느 것에도 안 맞을 때 마지막에 찾는 항목).
 */
export const USER_FLARE_CATEGORIES = (Object.keys(FLARE_CATEGORY_LABEL) as FlareCategory[])
  .filter((category) => category !== 'VOLCANO')
  .sort((a, b) =>
    a === 'ETC' ? 1 : b === 'ETC' ? -1 : FLARE_CATEGORY_LABEL[a].localeCompare(FLARE_CATEGORY_LABEL[b], 'ko'),
  )

/** 상세 화면의 생성 주체 표시. 외부 데이터 플레어는 작성자 이름 자리에 출처 이름(예: 서울시 교통정보)이 온다. */
export const FLARE_ORIGIN_LABEL: Record<FlareOrigin, string> = {
  USER: '관측자',
  // 출처 이름은 작성자 자리와 본문에 있다.
  PUBLIC: '공공 정보',
  FESTIVAL: '축제·행사',
  API: '공공 정보',
}

/** 위치만 아는 플레어의 색. 분류를 모르므로 어느 분류 색과도 겹치지 않는 중립색을 쓴다. */
export const RESTRICTED_FLARE_COLOR = '#c9d4e6'

export const FLARE_CATEGORY_COLOR: Record<FlareCategory, string> = {
  FIRE: '#ff5c4d',
  ACCIDENT: '#ffb84d',
  WEATHER: '#4dc9ff',
  CROWD: '#c98bff',
  // 눈·잔디 같은 바깥 놀이를 떠올리는 민트. 기상(하늘색)·기타(노랑)와 겹치지 않게 골랐다.
  LEISURE: '#5ef2b8',
  // 용암색. 화재(산호빛 빨강 #ff5c4d)보다 짙고 주황 쪽으로 기울여, 둘이 나란히 있어도 구별되게 했다.
  // 예전엔 '기타'(노랑)였는데 노란 점은 화산으로 읽히지 않았다(D-25).
  VOLCANO: '#ff3d00',
  ETC: '#ffe14d',
}

/**
 * 공식 관측기관이 발표한 지진(GET /api/globe/quakes). 글은 발표 기관의 원문 그대로이고 우리가 판단을 덧붙이지 않는다.
 * 한반도 인근은 기상청만, 같은 지진이면 기상청이다(D-18, docs/data-sources.md). 플레어와 달리 사람이 남기지 않으므로 채팅·공감이 없다.
 */
export interface Quake {
  id: string
  occurredAt: string
  announcedAt: string
  latitude: number
  longitude: number
  magnitude: number | null
  depthKm: number | null
  intensity: string
  location: string
  remark: string
  /** 발표 기관(출처). "기상청" 또는 해외 공식 기관(예: "USGS"). 한반도 인근은 늘 기상청이다(서버 QuakeSources). */
  source?: string
}
