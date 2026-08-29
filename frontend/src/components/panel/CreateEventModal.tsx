import { useState } from 'react'
import { api } from '../../lib/api'
import { EVENT_CATEGORY_LABEL, type EarthEvent, type EventCategory } from '../../types'
import './CreateEventModal.css'

interface Props {
  latitude: number
  longitude: number
  onClose: () => void
  onCreated: (event: EarthEvent) => void
  onDelete: () => void
}

export default function CreateEventModal({ latitude, longitude, onClose, onCreated, onDelete }: Props) {
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [category, setCategory] = useState<EventCategory>('FIRE')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async () => {
    // 내용을 입력해야 별이 확정된다. 그 전까지는 본인 화면의 임시 별로만 존재한다.
    if (!title.trim() || !content.trim()) {
      setError('제목과 내용을 모두 입력해야 별이 만들어집니다.')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const { data } = await api.post<EarthEvent>('/api/events', {
        title: title.trim(),
        content: content.trim(),
        category,
        latitude,
        longitude,
      })
      onCreated(data)
      onClose()
    } catch (e) {
      // 서버가 1시간 등록 제한(429)을 걸었다면 그 사유를 그대로 보여준다.
      const response = (e as { response?: { status?: number; data?: { message?: string } } }).response
      if (response?.status === 429) {
        setError(response.data?.message ?? '잠시 후 다시 시도해 주세요.')
      } else {
        setError('이벤트 등록에 실패했습니다. 다시 시도해 주세요.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="create-modal__backdrop" onClick={onClose}>
      <div className="create-modal" onClick={(e) => e.stopPropagation()}>
        <h3>이 위치에서 무슨 일이 있었나요?</h3>
        <p className="create-modal__coords">
          {latitude.toFixed(4)}, {longitude.toFixed(4)}
        </p>

        <select value={category} onChange={(e) => setCategory(e.target.value as EventCategory)}>
          {Object.entries(EVENT_CATEGORY_LABEL).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>

        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="제목 (최대 80자)"
          maxLength={80}
        />

        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="내용 (필수 — 입력해야 별이 만들어집니다)"
          maxLength={1000}
          rows={4}
        />

        {error && <p className="create-modal__error">{error}</p>}

        <div className="create-modal__actions">
          <button className="create-modal__delete" onClick={onDelete} disabled={submitting}>
            별 삭제
          </button>
          <div className="create-modal__actions-right">
            <button className="create-modal__cancel" onClick={onClose}>
              취소
            </button>
            <button className="create-modal__submit" onClick={submit} disabled={submitting}>
              {submitting ? '등록 중...' : '등록'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
