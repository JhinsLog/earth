import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useAuthStore } from '../../store/authStore'
import { useBlockStore } from '../../store/blockStore'
import { api } from '../../lib/api'
import { trackAction } from '../../lib/telemetry'
import type { EarthUser } from '../../types'
// 판(panel)·입력칸·목록 모양은 옵션 창과 같게 둔다. 원래 옵션 창 안에 있던 칸들이라 그 스타일을 그대로 쓴다.
import '../settings/SettingsMenu.css'
import './UserMenu.css'

/** 서버 규칙(Nicknames.java)과 같은 길이. 형식 검사는 서버가 최종 판정한다. */
const NICKNAME_MAX = 16

/** 다음에 바꿀 수 있는 날까지 남은 날(올림). 서버 문구와 같은 계산이다. */
function daysUntil(iso: string | null): number {
  if (!iso) return 0
  return Math.max(0, Math.ceil((Date.parse(iso) - Date.now()) / 86_400_000))
}

/**
 * 닉네임 바꾸기. 가입하면 '지구인-XXXXXX'가 자동으로 붙는다(구글 계정 이름은 받지 않는다).
 * 서버가 거절하면(형식 400, 욕설 400, 중복·비슷한 이름 409, 30일 안에 다시 바꿈 429) 그 사유를 그대로 보여 준다.
 */
function NicknameEditor({ user }: { user: EarthUser }) {
  const setUser = useAuthStore((s) => s.setUser)
  const [value, setValue] = useState(user.nickname)
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null)
  const [saving, setSaving] = useState(false)
  const changed = value.trim() !== user.nickname

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!changed || saving) return
    setSaving(true)
    setMessage(null)
    try {
      const { data } = await api.patch<EarthUser>('/api/users/me/nickname', { nickname: value })
      setUser(data)
      setValue(data.nickname)
      setMessage({ text: '바꿨습니다.', error: false })
      trackAction('mypage.nickname')
    } catch (err) {
      const response = (err as { response?: { status?: number; data?: { message?: string } } }).response
      const known = response?.status === 400 || response?.status === 409 || response?.status === 429
      setMessage({ text: (known && response?.data?.message) || '잠시 후 다시 시도해 주세요.', error: true })
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="settings__section" onSubmit={(e) => void submit(e)}>
      <label className="settings__label" htmlFor="settings-nickname">
        닉네임
      </label>
      <div className="settings__nickname">
        <input
          id="settings-nickname"
          value={value}
          maxLength={NICKNAME_MAX}
          autoComplete="off"
          spellCheck={false}
          onChange={(e) => {
            setValue(e.target.value)
            setMessage(null)
          }}
        />
        <button type="submit" disabled={!changed || saving}>
          {saving ? '저장 중' : '저장'}
        </button>
      </div>
      <p className={message?.error ? 'settings__hint settings__hint--error' : 'settings__hint'} role="status">
        {message?.text ??
          (daysUntil(user.nicknameChangeableAt) > 0
            ? `${daysUntil(user.nicknameChangeableAt)}일 뒤에 다른 이름으로 바꿀 수 있습니다 (띄어쓰기·대소문자는 지금 가능)`
            : '2~16자, 한글·영문·숫자 · 30일에 한 번 바꿀 수 있습니다')}
      </p>
    </form>
  )
}

function BlockedUsers() {
  const { blocked, unblock } = useBlockStore()
  return (
    <div className="settings__section">
      <div className="settings__label">차단한 사용자</div>
      {blocked.length === 0 ? (
        <p className="settings__empty">없음</p>
      ) : (
        <ul className="settings__blocked">
          {blocked.map((b) => (
            <li key={b.userId}>
              <span>{b.nickname}</span>
              <button onClick={() => void unblock(b.userId).catch(() => {})}>해제</button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

type View = 'closed' | 'menu' | 'mypage'

/**
 * 로그인한 사용자 표시(레벨 + 닉네임). 누르면 메뉴(마이페이지 · 로그아웃)가 열리고, 마이페이지에서 닉네임과 차단한
 * 사용자를 다룬다. 로그아웃을 바로 옆에 두지 않은 이유: 휴대폰 상단바가 좁고, 잘못 눌러 로그아웃되기 쉬웠다(2026-11-16).
 */
export default function UserMenu({ user }: { user: EarthUser }) {
  const logout = useAuthStore((s) => s.logout)
  const [view, setView] = useState<View>('closed')
  const rootRef = useRef<HTMLDivElement>(null)

  // 바깥을 누르거나 Esc를 누르면 닫는다(옵션 창과 같은 규칙).
  useEffect(() => {
    if (view === 'closed') return
    const onPointerDown = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setView('closed')
    }
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setView('closed')
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [view])

  return (
    <div className="user-menu" ref={rootRef}>
      <button
        className="navbar__user"
        aria-haspopup="menu"
        aria-expanded={view !== 'closed'}
        onClick={() => {
          if (view === 'closed') trackAction('usermenu.open')
          setView((v) => (v === 'closed' ? 'menu' : 'closed'))
        }}
      >
        <span className="navbar__level">Lv.{user.level}</span>
        <span className="navbar__nickname">{user.nickname}</span>
      </button>
      {view === 'menu' && (
        <div className="user-menu__list" role="menu">
          <button
            role="menuitem"
            onClick={() => {
              trackAction('mypage.open')
              setView('mypage')
            }}
          >
            마이페이지
          </button>
          <button
            role="menuitem"
            onClick={() => {
              trackAction('auth.logout')
              setView('closed')
              logout()
            }}
          >
            로그아웃
          </button>
        </div>
      )}
      {view === 'mypage' && (
        <div className="settings__panel user-menu__page" role="dialog" aria-label="마이페이지">
          <div className="settings__title">마이페이지</div>
          <NicknameEditor key={user.id} user={user} />
          <BlockedUsers />
        </div>
      )}
    </div>
  )
}
