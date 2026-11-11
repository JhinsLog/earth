import { useEffect, useState } from 'react'
import { api } from '../../lib/api'
import { useAuthStore } from '../../store/authStore'
import { useBlockStore } from '../../store/blockStore'
import { FLARE_CATEGORY_LABEL, FLARE_ORIGIN_LABEL, type DetailedFlare } from '../../types'
import ChatRoom from './ChatRoom'
import './FlareDetailPanel.css'

interface Props {
  /** 상세를 받은 플레어만 연다. 위치만 아는 플레어는 HomePage가 여기까지 보내지 않는다. */
  flare: DetailedFlare
  onClose: () => void
  onConfirmed: (flare: DetailedFlare) => void
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

export default function FlareDetailPanel({ flare, onClose, onConfirmed }: Props) {
  const user = useAuthStore((s) => s.user)
  const [confirming, setConfirming] = useState(false)
  const [confirmError, setConfirmError] = useState<string | null>(null)

  const isAuthor = user != null && user.id === flare.authorId
  const blockUser = useBlockStore((s) => s.block)
  // 차단하면 이 플레어도 목록에서 걸러져 패널이 저절로 닫힌다.
  const blockAuthor = async () => {
    if (!window.confirm(`${flare.authorNickname}님을 차단할까요? 이 사람의 플레어와 채팅이 보이지 않습니다. 설정에서 풀 수 있습니다.`)) return
    try {
      await blockUser(flare.authorId, flare.authorNickname)
    } catch {
      setConfirmError('차단하지 못했습니다. 다시 시도해 주세요.')
    }
  }
  const toggleConfirm = async () => {
    setConfirming(true)
    setConfirmError(null)
    try {
      const { data } = flare.confirmedByMe
        ? await api.delete<DetailedFlare>(`/api/flares/${flare.id}/confirm`)
        : await api.post<DetailedFlare>(`/api/flares/${flare.id}/confirm`)
      onConfirmed(data)
    } catch {
      setConfirmError('처리에 실패했습니다. 다시 시도해 주세요.')
    } finally {
      setConfirming(false)
    }
  }

  // 플레어는 수명이 다하면 예고 없이 라스에서 사라진다. 남은 시간을 보여줘야
  // 사라진 것이 오류가 아니라 정책이라는 걸 알 수 있다.
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [])

  const remaining = formatRemaining(flare.expiresAt, now)

  return (
    <aside className="flare-panel">
      <button className="flare-panel__close" onClick={onClose}>
        ×
      </button>

      <span className="flare-panel__category">{FLARE_CATEGORY_LABEL[flare.category]}</span>
      <h2 className="flare-panel__title">{flare.title}</h2>
      <p className="flare-panel__meta">
        <span className={`flare-panel__origin flare-panel__origin--${flare.origin.toLowerCase()}`}>
          {FLARE_ORIGIN_LABEL[flare.origin]}
        </span>{' '}
        {flare.authorNickname} · {formatCreatedAt(flare.createdAt)}
      </p>
      {flare.content && <p className="flare-panel__content">{flare.content}</p>}

      <p className="flare-panel__expiry">
        {remaining ? `${remaining} 후 사라집니다` : '곧 사라집니다'}
        {flare.confirmCount > 0 && ` · 공감 ${flare.confirmCount}`}
      </p>

      {user && !isAuthor && (
        <div className="flare-panel__confirm">
          <button
            className={`flare-panel__confirm-btn${flare.confirmedByMe ? ' is-on' : ''}`}
            onClick={toggleConfirm}
            disabled={confirming}
          >
            {flare.confirmedByMe ? '✓ 나도 봤어요' : '나도 봤어요'}
          </button>
          {/* 공공데이터 플레어는 사람이 쓴 글이 아니다. 끄려면 설정의 공공데이터 스위치를 쓴다. */}
          {flare.origin !== 'API' && (
            <button className="flare-panel__block-btn" onClick={blockAuthor}>
              작성자 차단
            </button>
          )}
          {confirmError && <p className="flare-panel__confirm-note">{confirmError}</p>}
        </div>
      )}

      <ChatRoom flareId={flare.id} />
    </aside>
  )
}
