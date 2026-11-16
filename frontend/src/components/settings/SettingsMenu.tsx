import { useEffect, useRef, useState } from 'react'
import { useSettingsStore } from '../../store/settingsStore'
import { trackAction } from '../../lib/telemetry'
import './SettingsMenu.css'

/**
 * 화면 표시 옵션. 브라우저에만 기억한다(달빛은 기억하지 않는다). 계정 정보(닉네임·차단한 사용자)는 여기가 아니라
 * 마이페이지(account/UserMenu)에 둔다 — 옵션은 화면을 어떻게 볼지, 마이페이지는 나에 관한 것이다(2026-11-16).
 * 화면에는 '옵션'으로 보인다. 코드의 이름(Settings*)은 저장소·로그 키와 맞추려고 그대로 둔다.
 */
export default function SettingsMenu() {
  const { cityLights, setCityLights, publicFlares, setPublicFlares, subwayLines, setSubwayLines, autoRotate, setAutoRotate, moonlight, setMoonlight } =
    useSettingsStore()
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
          {/*
            순서는 영향이 미치는 범위가 넓은 것부터: 지구 전체(자전) → 밤 지역 전체(달빛) → 밤의 도시(도시 불빛)
            → 도시 안(지하철 노선) → 한 지점(공공 정보). 새 옵션도 이 기준으로 끼워 넣는다(2026-11-16 사용자 지정).
          */}
          <label className="settings__row">
            <span className="settings__label">
              자전
            </span>
            <input
              type="checkbox"
              role="switch"
              className="settings__switch"
              checked={autoRotate}
              onChange={(e) => setAutoRotate(e.target.checked)}
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
          </div>
        </div>
      )}
    </div>
  )
}
