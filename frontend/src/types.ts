export type EventCategory = 'FIRE' | 'ACCIDENT' | 'WEATHER' | 'CROWD' | 'ETC'
export type EventStatus = 'ACTIVE' | 'CLOSED' | 'EXPIRED' | 'DELETED'

export interface EarthEvent {
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

export const EVENT_CATEGORY_COLOR: Record<EventCategory, string> = {
  FIRE: '#ff5c4d',
  ACCIDENT: '#ffb84d',
  WEATHER: '#4dc9ff',
  CROWD: '#c98bff',
  ETC: '#ffe14d',
}
