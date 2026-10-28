import { create } from 'zustand'
import type { EarthUser } from '../types'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: EarthUser | null
  /** 로그인 직후 한 번. remember에 따라 저장 위치와 유지 기한이 정해진다. */
  login: (accessToken: string, refreshToken: string, remember: boolean) => void
  /** 토큰 재발급. 로그인 때 고른 저장 위치와 기한을 그대로 따른다. */
  setTokens: (accessToken: string, refreshToken: string) => void
  setUser: (user: EarthUser | null) => void
  logout: () => void
}

const ACCESS_TOKEN_KEY = 'earth:accessToken'
const REFRESH_TOKEN_KEY = 'earth:refreshToken'
const REMEMBER_UNTIL_KEY = 'earth:rememberUntil'

/**
 * "일주일 동안 로그인 유지"의 기한.
 *
 * 서버의 refresh token은 14일짜리 그대로다. 유지 기간은 브라우저에서만 관리하기로 했으므로,
 * 기한이 지나면 서버 토큰이 아직 유효해도 브라우저가 스스로 버린다. 그래서 화면의 "일주일"이
 * 실제 동작과 일치한다. 대신 그 토큰을 누가 빼돌렸다면 서버에서는 14일까지 유효하다.
 */
export const REMEMBER_DURATION_MS = 7 * 24 * 60 * 60 * 1000

/**
 * 유지를 고르면 localStorage(브라우저를 꺼도 남음), 아니면 sessionStorage(탭을 닫으면 사라짐).
 * 기한 기록이 있느냐로 어느 쪽에 저장됐는지 구분한다.
 */
function activeStorage(): Storage {
  return localStorage.getItem(REMEMBER_UNTIL_KEY) !== null ? localStorage : sessionStorage
}

function clearAll() {
  for (const storage of [localStorage, sessionStorage]) {
    storage.removeItem(ACCESS_TOKEN_KEY)
    storage.removeItem(REFRESH_TOKEN_KEY)
  }
  localStorage.removeItem(REMEMBER_UNTIL_KEY)
}

function loadInitialTokens(): { accessToken: string | null; refreshToken: string | null } {
  const until = Number(localStorage.getItem(REMEMBER_UNTIL_KEY))
  if (localStorage.getItem(REMEMBER_UNTIL_KEY) !== null && !(until > Date.now())) {
    clearAll()
    return { accessToken: null, refreshToken: null }
  }
  // 이 기능 이전에 로그인해 기한 없이 localStorage에만 남은 토큰은 버린다. 유지 여부를 고른 적이
  // 없는 로그인이 기한 없이 계속 유지되는 것을 막기 위해서다. 한 번 다시 로그인하면 된다.
  if (localStorage.getItem(REMEMBER_UNTIL_KEY) === null && localStorage.getItem(REFRESH_TOKEN_KEY) !== null) {
    clearAll()
  }
  const storage = activeStorage()
  return {
    accessToken: storage.getItem(ACCESS_TOKEN_KEY),
    refreshToken: storage.getItem(REFRESH_TOKEN_KEY),
  }
}

export const useAuthStore = create<AuthState>((set) => ({
  ...loadInitialTokens(),
  user: null,
  login: (accessToken, refreshToken, remember) => {
    clearAll()
    const storage = remember ? localStorage : sessionStorage
    if (remember) localStorage.setItem(REMEMBER_UNTIL_KEY, String(Date.now() + REMEMBER_DURATION_MS))
    storage.setItem(ACCESS_TOKEN_KEY, accessToken)
    storage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    set({ accessToken, refreshToken })
  },
  setTokens: (accessToken, refreshToken) => {
    // 탭을 열어 둔 채 기한을 넘기면 시작 시점 검사(loadInitialTokens)를 다시 거치지 않는다.
    // 재발급 시점에 한 번 더 막는다.
    const until = localStorage.getItem(REMEMBER_UNTIL_KEY)
    if (until !== null && !(Number(until) > Date.now())) {
      clearAll()
      set({ accessToken: null, refreshToken: null, user: null })
      return
    }
    // 재발급은 기한을 늘리지 않는다. 늘리면 일주일 안에 한 번만 들어와도 영원히 유지된다.
    const storage = activeStorage()
    storage.setItem(ACCESS_TOKEN_KEY, accessToken)
    storage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    set({ accessToken, refreshToken })
  },
  setUser: (user) => set({ user }),
  logout: () => {
    clearAll()
    set({ accessToken: null, refreshToken: null, user: null })
  },
}))
