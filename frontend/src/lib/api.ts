import axios from 'axios'
import { useAuthStore } from '../store/authStore'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

export const api = axios.create({ baseURL: API_BASE_URL })

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshing: Promise<string | null> | null = null

/** 리프레시 토큰으로 액세스 토큰을 새로 받는다. 동시에 여러 번 불려도 요청은 하나만 나간다. 실패하면 null(서버가 거절했을 때만 로그아웃). */
export async function refreshAccessToken(): Promise<string | null> {
  const { refreshToken, setTokens, logout } = useAuthStore.getState()
  if (!refreshToken) return null

  if (!refreshing) {
    refreshing = axios
      .post(`${API_BASE_URL}/api/auth/refresh`, { refreshToken })
      .then(({ data }) => {
        setTokens(data.accessToken, data.refreshToken)
        // 로그인 유지 기한이 지났으면 setTokens가 저장하지 않고 로그아웃한다. 그때는 null이라 재시도하지 않는다.
        return useAuthStore.getState().accessToken
      })
      .catch((error) => {
        // 서버에 닿지 못한 실패(배포 중 재시작, 네트워크 끊김)는 토큰이 무효라는 뜻이 아니다.
        // 여기서 로그아웃하면 백엔드가 재시작되는 몇 초 사이에 접속자가 전부 로그아웃된다.
        // 서버가 갱신을 거절했을 때만 로그아웃한다.
        if (axios.isAxiosError(error) && !error.response) return null
        logout()
        return null
      })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config
    if (error.response?.status === 401 && !original._retry) {
      original._retry = true
      const newToken = await refreshAccessToken()
      if (newToken) {
        original.headers.Authorization = `Bearer ${newToken}`
        return api(original)
      }
    }
    return Promise.reject(error)
  },
)
