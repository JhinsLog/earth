import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { PENDING_REMEMBER_KEY } from '../components/auth/LoginModal'

export default function OAuthRedirectPage() {
  const [searchParams] = useSearchParams()
  const login = useAuthStore((s) => s.login)
  const navigate = useNavigate()

  useEffect(() => {
    const accessToken = searchParams.get('accessToken')
    const refreshToken = searchParams.get('refreshToken')
    // 선택 기록이 없으면(다른 탭에서 로그인을 시작했거나 기록이 지워진 경우) 유지하지 않는 쪽으로 둔다.
    const remember = sessionStorage.getItem(PENDING_REMEMBER_KEY) === '1'
    sessionStorage.removeItem(PENDING_REMEMBER_KEY)
    if (accessToken && refreshToken) {
      login(accessToken, refreshToken, remember)
    }
    navigate('/home', { replace: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return null
}
