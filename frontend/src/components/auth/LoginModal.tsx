import { useEffect, useState } from 'react'
import { API_BASE_URL } from '../../lib/api'
import './LoginModal.css'

/**
 * 구글 로그인으로 넘어가는 동안 "로그인 유지" 선택을 보관하는 키.
 *
 * 로그인은 구글 페이지를 거쳐 /oauth/redirect로 돌아오는 전체 페이지 이동이라 React 상태가 남지 않는다.
 * sessionStorage는 같은 탭에서 다른 사이트에 다녀와도 유지되므로 그 왕복을 건너 선택을 전달할 수 있다.
 */
export const PENDING_REMEMBER_KEY = 'earth:pendingRemember'

interface Props {
  onClose: () => void
  /** 주면 "로그인 없이 둘러보기" 버튼이 나타난다. 홈 지구본에서 진입할 때 쓴다. */
  onGuest?: () => void
}

export default function LoginModal({ onClose, onGuest }: Props) {
  const [remember, setRemember] = useState(false)

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  const handleGoogleLogin = () => {
    sessionStorage.setItem(PENDING_REMEMBER_KEY, remember ? '1' : '0')
    window.location.href = `${API_BASE_URL}/oauth2/authorization/google`
  }

  return (
    <div className="login-modal__backdrop" onClick={onClose}>
      <div className="login-modal" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
        <h3>로그인</h3>
        <button className="login-modal__google" onClick={handleGoogleLogin}>
          Google로 로그인
        </button>
        <label className="login-modal__remember">
          <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
          일주일 동안 로그인 유지
        </label>
        <p className="login-modal__hint">
          {remember
            ? '이 브라우저에서 7일 동안 로그인 상태가 유지됩니다. 공용 PC에서는 체크하지 마세요.'
            : '탭을 닫으면 로그아웃됩니다.'}
        </p>
        {onGuest && (
          <button className="login-modal__guest" onClick={onGuest}>
            로그인 없이 둘러보기
          </button>
        )}
        <button className="login-modal__cancel" onClick={onClose}>
          닫기
        </button>
      </div>
    </div>
  )
}
