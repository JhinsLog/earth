import { useEffect, useState } from 'react'
import { api } from '../../lib/api'
import { useAuthStore } from '../../store/authStore'
import { EVENT_CATEGORY_LABEL, type EarthEvent, type EventCategory } from '../../types'
import ChatRoom from './ChatRoom'
import './EventDetailPanel.css'

interface Props {
  event: EarthEvent
  onClose: () => void
  onUpdated: (event: EarthEvent) => void
  onDeleted: (id: number) => void
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

export default function EventDetailPanel({ event, onClose, onUpdated, onDeleted }: Props) {
  const user = useAuthStore((s) => s.user)
  const isAuthor = user != null && user.id === event.authorId

  // 다른 별을 선택하면 HomePage가 key로 이 컴포넌트를 리마운트하므로,
  // 편집 상태는 마운트 시 props에서 초기화되면 충분하다.
  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState(event.title)
  const [content, setContent] = useState(event.content ?? '')
  const [category, setCategory] = useState<EventCategory>(event.category)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 1초마다 남은 시간을 다시 그린다.
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [])

  const remaining = formatRemaining(event.expiresAt, now)

  const submitEdit = async () => {
    if (!title.trim() || !content.trim()) {
      setError('제목과 내용을 모두 입력해 주세요.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const { data } = await api.put<EarthEvent>(`/api/events/${event.id}`, {
        title: title.trim(),
        content: content.trim(),
        category,
      })
      onUpdated(data)
      setEditing(false)
    } catch {
      setError('수정에 실패했습니다. 다시 시도해 주세요.')
    } finally {
      setBusy(false)
    }
  }

  const submitDelete = async () => {
    if (!window.confirm('이 별을 삭제할까요? 되돌릴 수 없습니다.')) return
    setBusy(true)
    setError(null)
    try {
      await api.delete(`/api/events/${event.id}`)
      onDeleted(event.id)
      onClose()
    } catch {
      setError('삭제에 실패했습니다. 다시 시도해 주세요.')
      setBusy(false)
    }
  }

  return (
    <aside className="event-panel">
      <button className="event-panel__close" onClick={onClose}>
        ×
      </button>

      {editing ? (
        <div className="event-panel__edit">
          <select value={category} onChange={(e) => setCategory(e.target.value as EventCategory)}>
            {Object.entries(EVENT_CATEGORY_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          <input
            value={title}
            maxLength={80}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="제목"
          />
          <textarea
            value={content}
            maxLength={1000}
            rows={4}
            onChange={(e) => setContent(e.target.value)}
            placeholder="내용"
          />
          <div className="event-panel__actions">
            <button className="event-panel__btn" onClick={submitEdit} disabled={busy}>
              {busy ? '저장 중…' : '저장'}
            </button>
            <button
              className="event-panel__btn event-panel__btn--ghost"
              onClick={() => setEditing(false)}
              disabled={busy}
            >
              취소
            </button>
          </div>
        </div>
      ) : (
        <>
          <span className="event-panel__category">{EVENT_CATEGORY_LABEL[event.category]}</span>
          <h2 className="event-panel__title">{event.title}</h2>
          <p className="event-panel__meta">
            {event.authorNickname} · {new Date(event.createdAt).toLocaleString()}
            {event.updatedAt && ' · 수정됨'}
          </p>
          {event.content && <p className="event-panel__content">{event.content}</p>}
          <p className="event-panel__coords">
            {event.latitude.toFixed(4)}, {event.longitude.toFixed(4)}
          </p>

          <p className="event-panel__expiry">
            {remaining ? `${remaining} 후 사라집니다` : '곧 사라집니다'}
          </p>

          {isAuthor && (
            <div className="event-panel__actions">
              <button className="event-panel__btn" onClick={() => setEditing(true)} disabled={busy}>
                수정
              </button>
              <button
                className="event-panel__btn event-panel__btn--danger"
                onClick={submitDelete}
                disabled={busy}
              >
                {busy ? '삭제 중…' : '삭제'}
              </button>
            </div>
          )}
        </>
      )}

      {error && <p className="event-panel__error">{error}</p>}

      <ChatRoom eventId={event.id} />
    </aside>
  )
}
