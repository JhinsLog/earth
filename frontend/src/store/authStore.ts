import { create } from 'zustand'
import type { EarthUser } from '../types'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: EarthUser | null
  setTokens: (accessToken: string, refreshToken: string) => void
  setUser: (user: EarthUser | null) => void
  logout: () => void
}

const ACCESS_TOKEN_KEY = 'earth:accessToken'
const REFRESH_TOKEN_KEY = 'earth:refreshToken'

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: localStorage.getItem(ACCESS_TOKEN_KEY),
  refreshToken: localStorage.getItem(REFRESH_TOKEN_KEY),
  user: null,
  setTokens: (accessToken, refreshToken) => {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    set({ accessToken, refreshToken })
  },
  setUser: (user) => set({ user }),
  logout: () => {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    set({ accessToken: null, refreshToken: null, user: null })
  },
}))
