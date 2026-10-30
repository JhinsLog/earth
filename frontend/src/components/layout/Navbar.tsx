import { useState } from 'react'
import { useAuthStore } from '../../store/authStore'
import LoginModal from '../auth/LoginModal'
import SettingsMenu from '../settings/SettingsMenu'
import './Navbar.css'

interface Props {
  /** 로고를 눌렀을 때 첫 화면(지구본 전체)으로 되돌리는 동작. */
  onBrandClick?: () => void
}

export default function Navbar({ onBrandClick }: Props) {
  const { user, logout } = useAuthStore()
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
          <div className="navbar__user">
            {user.profileImageUrl && <img src={user.profileImageUrl} alt="" className="navbar__avatar" />}
            <span className="navbar__nickname">{user.nickname}</span>
            <span className="navbar__level">Lv.{user.level}</span>
            <button className="navbar__logout" onClick={logout}>
              로그아웃
            </button>
          </div>
        ) : (
          <button className="navbar__login" onClick={() => setShowLogin(true)}>
            로그인
          </button>
        )}
      </div>
      {showLogin && <LoginModal onClose={() => setShowLogin(false)} />}
    </header>
  )
}
