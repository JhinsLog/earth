import { useEffect, useState } from 'react'
import { EVENT_CATEGORY_LABEL, type EarthEvent } from '../../types'
import ChatRoom from './ChatRoom'
import './EventDetailPanel.css'

interface Props {
  event: EarthEvent
  onClose: () => void
}

/**
 * 생성 시각을 24시간제로 표기한다.
 * toLocaleString() 기본값은 한국어 로케일에서 "오전 1:04:45"처럼 12시간제로 나오는데,
 * 사건 발생 시각은 오전/오후를 한 번 더 해석해야 하는 형태보다 00~23시가 읽기 쉽다.
 */
function formatCreatedAt(isoString: string): string {
  return new Date(isoString).toLocaleString(undefined, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    // hour12 대신 hourCycle을 쓴다. hour12: false는 로케일에 따라 자정을 24시로 표기한다.
    hourCycle: 'h23',
  })
}

/** 남은 시간을 "12분 30초" 형태로. 이미 지났으면 null. */
function formatRemaining(expiresAt: string, now: number): string | null {
  const ms = new Date(expiresAt).getTime() - now
  if (ms <= 0) return null
  const totalSeconds = Math.floor(ms / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return minutes > 0 ? `${minutes}분 ${seconds}초` : `${seconds}초`
}

export default function EventDetailPanel({ event, onClose }: Props) {
  // 별은 수명이 다하면 예고 없이 지구본에서 사라진다. 남은 시간을 보여줘야
  // 사라진 것이 오류가 아니라 정책이라는 걸 알 수 있다.
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [])

  const remaining = formatRemaining(event.expiresAt, now)

  return (
    <aside className="event-panel">
      <button className="event-panel__close" onClick={onClose}>
        ×
      </button>

      <span className="event-panel__category">{EVENT_CATEGORY_LABEL[event.category]}</span>
      <h2 className="event-panel__title">{event.title}</h2>
      <p className="event-panel__meta">
        {event.authorNickname} · {formatCreatedAt(event.createdAt)}
      </p>
      {event.content && <p className="event-panel__content">{event.content}</p>}

      <p className="event-panel__expiry">
        {remaining ? `${remaining} 후 사라집니다` : '곧 사라집니다'}
      </p>

      <ChatRoom eventId={event.id} />
    </aside>
  )
}
