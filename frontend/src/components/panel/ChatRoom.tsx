import { useEffect, useRef, useState } from 'react'
import { api } from '../../lib/api'
import { publish, subscribeTopic } from '../../lib/ws'
import { useAuthStore } from '../../store/authStore'
import type { ChatMessage } from '../../types'
import './ChatRoom.css'

interface Props {
  eventId: number
}

export default function ChatRoom({ eventId }: Props) {
  const user = useAuthStore((s) => s.user)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [draft, setDraft] = useState('')
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let unsubscribe: (() => void) | undefined
    let cancelled = false

    api.get<ChatMessage[]>(`/api/events/${eventId}/chat`).then(({ data }) => {
      if (!cancelled) setMessages(data)
    })

    subscribeTopic(`/topic/chat.${eventId}`, (msg) => {
      const chatMessage = JSON.parse(msg.body) as ChatMessage
      setMessages((prev) => [...prev, chatMessage])
    }).then((unsub) => {
      unsubscribe = unsub
    })

    return () => {
      cancelled = true
      unsubscribe?.()
    }
  }, [eventId])

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight })
  }, [messages])

  const send = () => {
    const content = draft.trim()
    if (!content) return
    publish(`/app/chat.${eventId}.send`, { content })
    setDraft('')
  }

  return (
    <div className="chat-room">
      <div className="chat-room__list" ref={listRef}>
        {messages.length === 0 && <p className="chat-room__empty">아직 대화가 없습니다.</p>}
        {messages.map((m) => (
          <div key={m.id} className="chat-room__message">
            <span className="chat-room__nickname">{m.nickname}</span>
            <span className="chat-room__content">{m.content}</span>
          </div>
        ))}
      </div>

      {user ? (
        <div className="chat-room__composer">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && send()}
            placeholder="메시지 입력..."
            maxLength={500}
          />
          <button onClick={send}>전송</button>
        </div>
      ) : (
        <p className="chat-room__login-hint">채팅에 참여하려면 로그인이 필요합니다.</p>
      )}
    </div>
  )
}
