import { useState } from 'react'
import { useAuthStore } from '../../store/authStore'
import LoginModal from '../auth/LoginModal'
import SettingsMenu from '../settings/SettingsMenu'
import UserMenu from '../account/UserMenu'
import { trackAction } from '../../lib/telemetry'
import './Navbar.css'

interface Props {
  /** 로고를 눌렀을 때 첫 화면(라스 전체)으로 되돌리는 동작. */
  onBrandClick?: () => void
}

export default function Navbar({ onBrandClick }: Props) {
  const user = useAuthStore((s) => s.user)
  const [showLogin, setShowLogin] = useState(false)

  return (
    <header className="navbar">
      <button className="navbar__brand" onClick={onBrandClick} title="처음 화면으로">
        <span className="navbar__dot" />
        EARTH
      </button>

      <div className="navbar__actions">
        <SettingsMenu />
        {user ? (
          <UserMenu user={user} />
        ) : (
          <button
            className="navbar__login"
            onClick={() => {
              trackAction('login.open')
              setShowLogin(true)
            }}
          >
            로그인
          </button>
        )}
      </div>
      {showLogin && <LoginModal onClose={() => setShowLogin(false)} />}
    </header>
  )
}
