import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useSettingsStore } from '../../store/settingsStore'
import { useAuthStore } from '../../store/authStore'
import { useBlockStore } from '../../store/blockStore'
import { api } from '../../lib/api'
import { trackAction } from '../../lib/telemetry'
import type { EarthUser } from '../../types'
import './SettingsMenu.css'

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
      trackAction('settings.nickname')
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

/**
 * 화면 표시 옵션. 브라우저에만 기억한다(달빛은 기억하지 않는다).
 * 화면에는 '옵션'으로 보인다. 코드의 이름(Settings*)은 저장소·로그 키와 맞추려고 그대로 둔다.
 */
export default function SettingsMenu() {
  const { cityLights, setCityLights, publicFlares, setPublicFlares, subwayLines, setSubwayLines, moonlight, setMoonlight } =
    useSettingsStore()
  const loggedIn = useAuthStore((s) => s.accessToken != null)
  const user = useAuthStore((s) => s.user)
  const { blocked, unblock } = useBlockStore()
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  // 바깥을 누르거나 Esc를 누르면 닫는다.
  useEffect(() => {
    if (!open) return
    const onPointerDown = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  return (
    <div className="settings" ref={rootRef}>
      <button
        className="settings__button"
        onClick={() => {
          if (!open) trackAction('settings.open')
          setOpen((v) => !v)
        }}
        aria-label="옵션"
        aria-expanded={open}
        title="옵션"
      >
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <path
            fill="currentColor"
            d="M19.14 12.94a7.4 7.4 0 0 0 0-1.88l2.03-1.58a.5.5 0 0 0 .12-.64l-1.92-3.32a.5.5 0 0 0-.6-.22l-2.39.96a7 7 0 0 0-1.63-.94l-.36-2.54A.5.5 0 0 0 13.9 2.5h-3.84a.5.5 0 0 0-.49.42l-.36 2.54c-.59.24-1.13.56-1.63.94l-2.39-.96a.5.5 0 0 0-.6.22L2.67 8.98a.5.5 0 0 0 .12.64l2.03 1.58a7.4 7.4 0 0 0 0 1.88l-2.03 1.58a.5.5 0 0 0-.12.64l1.92 3.32c.13.22.39.3.6.22l2.39-.96c.5.38 1.04.7 1.63.94l.36 2.54c.04.24.25.42.49.42h3.84c.24 0 .45-.18.49-.42l.36-2.54c.59-.24 1.13-.56 1.63-.94l2.39.96c.22.08.47 0 .6-.22l1.92-3.32a.5.5 0 0 0-.12-.64zM12 15.5A3.5 3.5 0 1 1 12 8.5a3.5 3.5 0 0 1 0 7z"
          />
        </svg>
      </button>
      {open && (
        <div className="settings__panel" role="dialog" aria-label="옵션">
          <div className="settings__title">옵션</div>
          <div className="settings__rows">
          <label className="settings__row">
            <span className="settings__label">
              도시 불빛
            </span>
            <input
              type="checkbox"
              role="switch"
              className="settings__switch"
              checked={cityLights}
              onChange={(e) => setCityLights(e.target.checked)}
            />
          </label>
          <label className="settings__row">
            <span className="settings__label">
              공공 정보
            </span>
            <input
              type="checkbox"
              role="switch"
              className="settings__switch"
              checked={publicFlares}
              onChange={(e) => setPublicFlares(e.target.checked)}
            />
          </label>
          <label className="settings__row">
            <span className="settings__label">
              지하철 노선
            </span>
            <input
              type="checkbox"
              role="switch"
              className="settings__switch"
              checked={subwayLines}
              onChange={(e) => setSubwayLines(e.target.checked)}
            />
          </label>
          {/*
            달빛: 해가 진 지역 전체를 이만큼 밝힌다. 원래 휴대폰 첫 화면 아래에 막대로 띄웠으나, 화면을 가려
            옵션으로 옮겼다(2026-11-16). 우클릭·길게 누르기 손전등은 그대로 있다.
          */}
          <label className="settings__row settings__row--range">
            <span className="settings__label">달빛</span>
            <input
              type="range"
              className="settings__range"
              min={0}
              max={100}
              step={1}
              value={Math.round(moonlight * 100)}
              aria-label="밤 지역 달빛 밝기"
              onChange={(e) => setMoonlight(Number(e.target.value) / 100)}
              // 끌 때마다 남기면 로그가 넘친다. 손을 뗀 값만 남긴다.
              onPointerUp={(e) => trackAction('settings.change', { key: 'moonlight', level: Number(e.currentTarget.value) })}
            />
          </label>
          </div>
          {loggedIn && user && <NicknameEditor key={user.id} user={user} />}
          {loggedIn && (
            <div className="settings__section">
              <div className="settings__label">
                차단한 사용자
              </div>
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
          )}
        </div>
      )}
    </div>
  )
}
