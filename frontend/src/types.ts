export type FlareCategory = 'FIRE' | 'ACCIDENT' | 'WEATHER' | 'CROWD' | 'ETC'
export type FlareStatus = 'ACTIVE' | 'CLOSED' | 'EXPIRED' | 'DELETED'
/** 플레어를 만든 주체. USER는 사용자가 직접, API는 공공데이터 등 외부 데이터에서 만든 플레어. */
export type FlareOrigin = 'USER' | 'API'

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
  /** "나도 봤다"고 확인한 사람 수. 플레어의 크기·밝기와 수명에 반영된다. */
  confirmCount: number
  /** 지금 로그인한 사용자가 이미 공감했는지. 비로그인이면 항상 false. */
  confirmedByMe: boolean
  /** 플레어가 속한 국가(ISO 3166-1 alpha-2). 공해 등은 null. */
  countryCode: string | null
  restricted: false
  origin: FlareOrigin
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
  email: string
  nickname: string
  profileImageUrl: string | null
  level: number
  exp: number
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
  ETC: '기타',
}

/** 상세 화면의 생성 주체 표시. 외부 데이터 플레어는 작성자 이름 자리에 출처 이름(예: 서울시 교통정보)이 온다. */
export const FLARE_ORIGIN_LABEL: Record<FlareOrigin, string> = {
  USER: '관측자',
  API: '공공데이터',
}

/** 위치만 아는 플레어의 색. 분류를 모르므로 어느 분류 색과도 겹치지 않는 중립색을 쓴다. */
export const RESTRICTED_FLARE_COLOR = '#c9d4e6'

export const FLARE_CATEGORY_COLOR: Record<FlareCategory, string> = {
  FIRE: '#ff5c4d',
  ACCIDENT: '#ffb84d',
  WEATHER: '#4dc9ff',
  CROWD: '#c98bff',
  ETC: '#ffe14d',
}
