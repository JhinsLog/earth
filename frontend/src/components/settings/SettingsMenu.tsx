import { useEffect, useRef, useState } from 'react'
import { useSettingsStore } from '../../store/settingsStore'
import { useAuthStore } from '../../store/authStore'
import { useBlockStore } from '../../store/blockStore'
import './SettingsMenu.css'

/** 화면 표시 설정. 브라우저에만 기억한다. */
export default function SettingsMenu() {
  const { cityLights, setCityLights, publicFlares, setPublicFlares, subwayLines, setSubwayLines } = useSettingsStore()
  const loggedIn = useAuthStore((s) => s.accessToken != null)
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
        onClick={() => setOpen((v) => !v)}
        aria-label="설정"
        aria-expanded={open}
        title="설정"
      >
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <path
            fill="currentColor"
            d="M19.14 12.94a7.4 7.4 0 0 0 0-1.88l2.03-1.58a.5.5 0 0 0 .12-.64l-1.92-3.32a.5.5 0 0 0-.6-.22l-2.39.96a7 7 0 0 0-1.63-.94l-.36-2.54A.5.5 0 0 0 13.9 2.5h-3.84a.5.5 0 0 0-.49.42l-.36 2.54c-.59.24-1.13.56-1.63.94l-2.39-.96a.5.5 0 0 0-.6.22L2.67 8.98a.5.5 0 0 0 .12.64l2.03 1.58a7.4 7.4 0 0 0 0 1.88l-2.03 1.58a.5.5 0 0 0-.12.64l1.92 3.32c.13.22.39.3.6.22l2.39-.96c.5.38 1.04.7 1.63.94l.36 2.54c.04.24.25.42.49.42h3.84c.24 0 .45-.18.49-.42l.36-2.54c.59-.24 1.13-.56 1.63-.94l2.39.96c.22.08.47 0 .6-.22l1.92-3.32a.5.5 0 0 0-.12-.64zM12 15.5A3.5 3.5 0 1 1 12 8.5a3.5 3.5 0 0 1 0 7z"
          />
        </svg>
      </button>
      {open && (
        <div className="settings__panel" role="dialog" aria-label="설정">
          <div className="settings__title">설정</div>
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
              공공데이터 플레어
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
