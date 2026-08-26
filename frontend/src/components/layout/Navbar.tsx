import { useAuthStore } from '../../store/authStore'
import { API_BASE_URL } from '../../lib/api'
import './Navbar.css'

interface Props {
  /** 로고를 눌렀을 때 첫 화면(지구본 전체)으로 되돌리는 동작. */
  onBrandClick?: () => void
}

export default function Navbar({ onBrandClick }: Props) {
  const { user, logout } = useAuthStore()

  const handleLogin = () => {
    window.location.href = `${API_BASE_URL}/oauth2/authorization/google`
  }

  return (
    <header className="navbar">
      <button className="navbar__brand" onClick={onBrandClick} title="처음 화면으로">
        <span className="navbar__dot" />
        EARTH
      </button>

      <div className="navbar__actions">
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
          <button className="navbar__login" onClick={handleLogin}>
            Google로 로그인
          </button>
        )}
      </div>
    </header>
  )
}
