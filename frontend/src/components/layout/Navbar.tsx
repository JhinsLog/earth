import { useAuthStore } from '../../store/authStore'
import { API_BASE_URL } from '../../lib/api'
import './Navbar.css'

interface Props {
  placing: boolean
  onTogglePlacing: () => void
}

export default function Navbar({ placing, onTogglePlacing }: Props) {
  const { user, logout } = useAuthStore()

  const handleLogin = () => {
    window.location.href = `${API_BASE_URL}/oauth2/authorization/google`
  }

  return (
    <header className="navbar">
      <div className="navbar__brand">
        <span className="navbar__dot" />
        EARTH
      </div>

      <div className="navbar__actions">
        {user && (
          <button
            className={`navbar__place-btn ${placing ? 'navbar__place-btn--active' : ''}`}
            onClick={onTogglePlacing}
          >
            {placing ? '지구본을 클릭해 위치 지정' : '+ 이벤트 등록'}
          </button>
        )}

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
