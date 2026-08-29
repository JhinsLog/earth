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

export default function EventDetailPanel({ event, onClose }: Props) {
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

      <ChatRoom eventId={event.id} />
    </aside>
  )
}
