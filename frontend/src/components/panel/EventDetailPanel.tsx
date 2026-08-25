import { EVENT_CATEGORY_LABEL, type EarthEvent } from '../../types'
import ChatRoom from './ChatRoom'
import './EventDetailPanel.css'

interface Props {
  event: EarthEvent
  onClose: () => void
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
        {event.authorNickname} · {new Date(event.createdAt).toLocaleString()}
      </p>
      {event.content && <p className="event-panel__content">{event.content}</p>}
      <p className="event-panel__coords">
        {event.latitude.toFixed(4)}, {event.longitude.toFixed(4)}
      </p>

      <ChatRoom eventId={event.id} />
    </aside>
  )
}
