import { useEffect, useState } from 'react'
import { api } from '../../lib/api'
import { useAuthStore } from '../../store/authStore'
import { useBlockStore } from '../../store/blockStore'
import { FLARE_CATEGORY_LABEL, FLARE_ORIGIN_LABEL, type ConfirmationKind, type DetailedFlare } from '../../types'
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

/**
 * 남은 시간을 "12분 30초" 형태로. 이미 지났으면 null.
 * 외부 정보 플레어(스키장 개장 등)는 수명이 몇 주라, 1시간이 넘으면 "3일 4시간"·"5시간 20분"처럼 큰 단위 둘만 쓴다.
 */
function formatRemaining(expiresAt: string, now: number): string | null {
  const ms = new Date(expiresAt).getTime() - now
  if (ms <= 0) return null
  const totalSeconds = Math.floor(ms / 1000)
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  if (days > 0) return hours > 0 ? `${days}일 ${hours}시간` : `${days}일`
  if (hours > 0) return `${hours}시간 ${minutes}분`
  return minutes > 0 ? `${minutes}분 ${seconds}초` : `${seconds}초`
}

/**
 * 사실 확인 버튼. "나도 봤어요"만 수명을 늘리고, 나머지 둘은 지금은 수만 보여 준다(D-20).
 * 감정 반응(좋아요 등)과 섞지 않는다 — 이 셋은 플레어가 사실인지·아직 유효한지를 알리는 신호다.
 */
const CONFIRMATION_BUTTONS: { kind: ConfirmationKind; icon: string; label: string }[] = [
  { kind: 'SEEN', icon: '👀', label: '나도 봤어요' },
  { kind: 'ENDED', icon: '✅', label: '끝났어요' },
  { kind: 'DISPUTED', icon: '❓', label: '사실과 달라요' },
]

/** 신고 사유(백엔드 ReportReason, D-24). 거짓 정보는 "사실과 달라요"가 맡는다 — 신고는 "보이면 안 되는 것"이다. */
const REPORT_REASONS: { reason: string; label: string }[] = [
  { reason: 'LOCATION_EXPOSURE', label: '집·피해자가 특정되는 위치' },
  { reason: 'PERSONAL_INFO', label: '개인정보' },
  { reason: 'ABUSE', label: '욕설·비방' },
]

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
  // 신고. 서로 다른 사람의 신고가 쌓이면 운영자가 보기 전까지 가려진다. 몇 건 쌓였는지는 서버가 알려 주지 않는다.
  const [reportOpen, setReportOpen] = useState(false)
  const [reportNote, setReportNote] = useState<string | null>(null)
  const report = async (reason: string) => {
    setReportOpen(false)
    try {
      await api.post(`/api/flares/${flare.id}/report`, null, { params: { reason } })
      setReportNote('신고했습니다. 신고가 쌓이면 운영자가 보기 전까지 가려집니다.')
    } catch (e) {
      const status = (e as { response?: { status?: number } }).response?.status
      setReportNote(status === 409 ? '이미 신고한 플레어입니다.' : '신고하지 못했습니다. 다시 시도해 주세요.')
    }
  }
  // 한 사람은 하나만 남긴다. 누른 것을 다시 누르면 취소, 다른 것을 누르면 바뀐다(서버가 이전 것을 정확히 되돌린다).
  const mine = flare.myConfirmation ?? (flare.confirmedByMe ? 'SEEN' : null)
  const toggleConfirm = async (kind: ConfirmationKind) => {
    setConfirming(true)
    setConfirmError(null)
    try {
      const { data } =
        mine === kind
          ? await api.delete<DetailedFlare>(`/api/flares/${flare.id}/confirm`)
          : await api.post<DetailedFlare>(`/api/flares/${flare.id}/confirm`, null, { params: { kind } })
      onConfirmed(data)
    } catch {
      setConfirmError('처리에 실패했습니다. 다시 시도해 주세요.')
    } finally {
      setConfirming(false)
    }
  }
  const counts: Record<ConfirmationKind, number> = {
    SEEN: flare.confirmCount,
    ENDED: flare.endedCount ?? 0,
    DISPUTED: flare.disputedCount ?? 0,
  }
  // 로그인하지 않았거나 내 플레어면 수만 보여 주고 누를 수 없다.
  const canConfirm = user != null && !isAuthor

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
      </p>

      {/* 값이 없으면 보인다. 프론트가 백엔드보다 먼저 배포되면 옛 응답에는 confirmationsOpen이 없다. */}
      {flare.confirmationsOpen !== false && (
        <div className="flare-panel__confirm">
          <div className="flare-panel__confirm-row" role="group" aria-label="사실 확인">
            {CONFIRMATION_BUTTONS.map(({ kind, icon, label }) => (
              <button
                key={kind}
                className={`flare-panel__confirm-btn${mine === kind ? ' is-on' : ''}`}
                onClick={() => toggleConfirm(kind)}
                disabled={confirming || !canConfirm}
                aria-pressed={mine === kind}
                title={user == null ? '로그인하면 누를 수 있습니다' : isAuthor ? '내 플레어에는 누를 수 없습니다' : undefined}
              >
                <span className="flare-panel__confirm-top">
                  <span aria-hidden="true">{icon}</span>
                  <span className="flare-panel__confirm-count">{counts[kind]}</span>
                </span>
                <span className="flare-panel__confirm-label">{label}</span>
              </button>
            ))}
          </div>
          {confirmError && <p className="flare-panel__confirm-note">{confirmError}</p>}
        </div>
      )}
      {/* 외부 플레어는 사람이 쓴 글이 아니다. 끄려면 옵션의 '공공 정보'·'축제·행사' 스위치를 쓴다. */}
      {user && !isAuthor && flare.origin === 'USER' && (
        <div className="flare-panel__moderation">
          <button className="flare-panel__block-btn" onClick={blockAuthor}>
            작성자 차단
          </button>
          <button
            className="flare-panel__block-btn"
            onClick={() => setReportOpen((open) => !open)}
            disabled={reportNote != null}
            aria-expanded={reportOpen}
          >
            신고
          </button>
        </div>
      )}
      {reportOpen && (
        <div className="flare-panel__report" role="group" aria-label="신고 사유">
          {REPORT_REASONS.map(({ reason, label }) => (
            <button key={reason} className="flare-panel__report-btn" onClick={() => report(reason)}>
              {label}
            </button>
          ))}
        </div>
      )}
      {reportNote && <p className="flare-panel__confirm-note">{reportNote}</p>}

      {/* 채팅을 닫은 플레어는 아무것도 그리지 않는다. 값이 없으면 연다(프론트가 백엔드보다 먼저 배포되면 옛 응답에는 chatOpen이 없다). */}
      {flare.chatOpen !== false && <ChatRoom flareId={flare.id} />}
    </aside>
  )
}
