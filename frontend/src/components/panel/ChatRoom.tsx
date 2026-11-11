import { useEffect, useRef, useState } from 'react'
import { api } from '../../lib/api'
import { onReconnect, publish, subscribeTopic } from '../../lib/ws'
import { useAuthStore } from '../../store/authStore'
import type { ChatMessage } from '../../types'
import './ChatRoom.css'

interface Props {
  flareId: number
}

export default function ChatRoom({ flareId }: Props) {
  const user = useAuthStore((s) => s.user)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [draft, setDraft] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let cancelled = false
    const unsubscribers: Array<() => void> = []

    const track = (pending: Promise<() => void>) => {
      pending
        .then((unsubscribe) => {
          if (cancelled) unsubscribe()
          else unsubscribers.push(unsubscribe)
        })
        .catch(() => {
          if (!cancelled) setError('실시간 연결에 실패했습니다. 페이지를 새로고침 해주세요.')
        })
    }

    // 끊긴 동안 온 메시지는 실시간으로 다시 오지 않는다. 재연결될 때마다 내역을 다시 받아
    // 이미 받은 것과 id로 합친다. 합치지 않고 교체하면 그사이 실시간으로 받은 것이 잠깐 사라질 수 있다.
    const loadHistory = () =>
      api
        .get<ChatMessage[]>(`/api/flares/${flareId}/chat`)
        .then(({ data }) => {
          if (cancelled) return
          setMessages((prev) => {
            const byId = new Map(prev.map((m) => [m.id, m]))
            data.forEach((m) => byId.set(m.id, m))
            return [...byId.values()].sort((a, b) => a.id - b.id)
          })
        })
        .catch(() => {
          if (!cancelled) setError('대화 내역을 불러오지 못했습니다.')
        })
    loadHistory()
    unsubscribers.push(onReconnect(() => {
      setError(null)
      loadHistory()
    }))

    track(
      subscribeTopic(`/topic/chat.${flareId}`, (msg) => {
        const chatMessage = JSON.parse(msg.body) as ChatMessage
        // 재연결 등으로 같은 메시지가 두 번 올 수 있어 id로 걸러낸다.
        setMessages((prev) => (prev.some((m) => m.id === chatMessage.id) ? prev : [...prev, chatMessage]))
      }),
    )

    // 서버가 메시지를 거절한 사유를 보낸 사람에게만 돌려주는 채널.
    // 이걸 구독하지 않으면 전송 실패가 아무 흔적도 남기지 않고 사라진다.
    track(
      subscribeTopic('/user/queue/errors', (msg) => {
        const body = JSON.parse(msg.body) as { message?: string }
        setError(body.message ?? '메시지 전송에 실패했습니다.')
      }),
    )

    return () => {
      cancelled = true
      unsubscribers.forEach((unsubscribe) => unsubscribe())
    }
  }, [flareId])

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight })
  }, [messages])

  const send = async () => {
    const content = draft.trim()
    if (!content || sending) return
    setError(null)
    setSending(true)
    try {
      await publish(`/app/chat.${flareId}.send`, { content })
      // 보낸 메시지는 서버를 한 바퀴 돌아 /topic으로 되돌아올 때 목록에 나타난다.
      // 화면에 떴다는 것 자체가 실제로 저장·전파됐다는 뜻이다.
      setDraft('')
    } catch {
      setError('연결이 끊겨 메시지를 보내지 못했습니다. 다시 연결되면 보내 주세요.')
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="chat-room">
      <div className="chat-room__list" ref={listRef}>
        <div className="chat-room__messages">
          {messages.length === 0 && <p className="chat-room__empty">아직 대화가 없습니다.</p>}
          {messages.map((m) => {
            const mine = m.userId === user?.id
            return (
              <div key={m.id} className={`chat-room__message${mine ? ' chat-room__message--mine' : ''}`}>
                {/* 내 메시지는 오른쪽 정렬만으로 이미 구분되므로 닉네임을 반복하지 않는다. */}
                {!mine && <span className="chat-room__nickname">{m.nickname}</span>}
                <span className="chat-room__content">{m.content}</span>
              </div>
            )
          })}
        </div>
      </div>

      {error && <p className="chat-room__error">{error}</p>}

      {user ? (
        <div className="chat-room__composer">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && send()}
            placeholder="메시지 입력..."
            maxLength={500}
          />
          <button onClick={send} disabled={sending}>
            {sending ? '전송 중' : '전송'}
          </button>
        </div>
      ) : (
        <p className="chat-room__login-hint">채팅에 참여하려면 로그인이 필요합니다.</p>
      )}
    </div>
  )
}
