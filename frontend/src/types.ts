export type EventCategory = 'FIRE' | 'ACCIDENT' | 'WEATHER' | 'CROWD' | 'ETC'
export type EventStatus = 'ACTIVE' | 'CLOSED' | 'EXPIRED' | 'DELETED'

/**
 * 별 하나. 서버가 상세를 줄 수 있는지에 따라 두 모양 중 하나로 온다.
 * 비로그인 사용자가 접속 국가 밖의 별을 볼 때는 위치와 존재만(RestrictedEvent) 온다.
 * 판정은 서버가 하고, 화면은 restricted로 갈라서 쓴다.
 */
export type EarthEvent = DetailedEvent | RestrictedEvent

export interface RestrictedEvent {
  id: number
  status: EventStatus
  latitude: number
  longitude: number
  expiresAt: string
  countryCode: string | null
  restricted: true
}

export interface DetailedEvent {
  id: number
  title: string
  content: string | null
  category: EventCategory
  status: EventStatus
  latitude: number
  longitude: number
  authorId: number
  authorNickname: string
  createdAt: string
  /** 수정된 적이 없으면 null. */
  updatedAt: string | null
  /** 이 시각이 지나면 별은 지구본에서 사라진다(기본 30분, 공감이 쌓이면 연장된다). */
  expiresAt: string
  /** "나도 봤다"고 확인한 사람 수. 별의 크기·밝기와 수명에 반영된다. */
  confirmCount: number
  /** 지금 로그인한 사용자가 이미 공감했는지. 비로그인이면 항상 false. */
  confirmedByMe: boolean
  /** 별이 속한 국가(ISO 3166-1 alpha-2). 공해 등은 null. */
  countryCode: string | null
  restricted: false
}

/**
 * 아직 서버에 등록되지 않은 임시 별. 우클릭으로 찍은 직후 상태이며,
 * 생성자 본인 화면에만 보인다. 내용을 입력해 등록하지 않으면 일정 시간 뒤 사라진다.
 */
export interface DraftStar {
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
  eventId: number
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

export const EVENT_CATEGORY_LABEL: Record<EventCategory, string> = {
  FIRE: '화재',
  ACCIDENT: '사고',
  WEATHER: '기상',
  CROWD: '군중/혼잡',
  ETC: '기타',
}

/** 위치만 아는 별의 색. 분류를 모르므로 어느 분류 색과도 겹치지 않는 중립색을 쓴다. */
export const RESTRICTED_STAR_COLOR = '#c9d4e6'

export const EVENT_CATEGORY_COLOR: Record<EventCategory, string> = {
  FIRE: '#ff5c4d',
  ACCIDENT: '#ffb84d',
  WEATHER: '#4dc9ff',
  CROWD: '#c98bff',
  ETC: '#ffe14d',
}
