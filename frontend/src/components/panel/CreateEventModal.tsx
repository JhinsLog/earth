import { useState } from 'react'
import { api } from '../../lib/api'
import { EVENT_CATEGORY_LABEL, type EarthEvent, type EventCategory } from '../../types'
import type { LocatedPoint } from '../../lib/geolocate'
import { checkWitnessRange, formatDistance } from '../../lib/witnessRule'
import './CreateEventModal.css'

interface Props {
  latitude: number
  longitude: number
  /** 내 위치. 아직 못 구했으면 null이며, 그때는 거리 검사를 하지 않는다. */
  myLocation: LocatedPoint | null
  /** GPS로 위치를 다시 확인한다. IP 오차 때문에 막힌 사용자의 탈출구. */
  onRefineLocation: () => Promise<LocatedPoint | null>
  locating: boolean
  onClose: () => void
  onCreated: (event: EarthEvent) => void
  onDelete: () => void
}

export default function CreateEventModal({
  latitude,
  longitude,
  myLocation,
  onRefineLocation,
  locating,
  onClose,
  onCreated,
  onDelete,
}: Props) {
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [category, setCategory] = useState<EventCategory>('FIRE')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 반경은 카테고리마다 다르므로 선택이 바뀔 때마다 다시 판정한다. 제출 버튼을 누른
  // 뒤에야 막히면 글을 다 쓰고 거부당하는 셈이라, 고르는 즉시 알려준다.
  const witness = myLocation ? checkWitnessRange(myLocation, { latitude, longitude }, category) : null
  const outOfRange = witness != null && !witness.ok

  const submit = async () => {
    // 내용을 입력해야 별이 확정된다. 그 전까지는 본인 화면의 임시 별로만 존재한다.
    if (!title.trim() || !content.trim()) {
      setError('제목과 내용을 모두 입력해야 별이 만들어집니다.')
      return
    }
    if (outOfRange) {
      setError('직접 보거나 겪은 사건만 등록할 수 있습니다.')
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

        {outOfRange && witness && (
          <div className="create-modal__range create-modal__range--blocked">
            <p>
              여기는 <strong>{formatDistance(witness.distanceKm)}</strong> 떨어진 곳입니다.
              <br />
              직접 보거나 겪은 사건만 등록할 수 있어요 — 이 종류는{' '}
              {formatDistance(witness.allowedKm)} 안에서만 등록됩니다.
            </p>
            {myLocation?.source === 'ip' && (
              <>
                <p className="create-modal__range-hint">
                  지금은 접속 정보로 대략적인 위치만 알고 있어요. 실제로 현장에 계신다면
                  정확한 위치를 확인해 주세요.
                </p>
                <button
                  className="create-modal__refine"
                  onClick={onRefineLocation}
                  disabled={locating}
                >
                  {locating ? '확인 중…' : '정확한 위치로 다시 확인'}
                </button>
              </>
            )}
          </div>
        )}

        {!outOfRange && witness && (
          <p className="create-modal__range">
            내 위치에서 {formatDistance(witness.distanceKm)}
          </p>
        )}

        {error && <p className="create-modal__error">{error}</p>}

        <div className="create-modal__actions">
          <button className="create-modal__delete" onClick={onDelete} disabled={submitting}>
            별 삭제
          </button>
          <div className="create-modal__actions-right">
            <button className="create-modal__cancel" onClick={onClose}>
              취소
            </button>
            <button
              className="create-modal__submit"
              onClick={submit}
              disabled={submitting || outOfRange}
            >
              {submitting ? '등록 중...' : '등록'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
