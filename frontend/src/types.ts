export type EventCategory = 'FIRE' | 'ACCIDENT' | 'WEATHER' | 'CROWD' | 'ETC'
export type EventStatus = 'ACTIVE' | 'CLOSED'

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
