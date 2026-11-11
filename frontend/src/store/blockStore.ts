import { create } from 'zustand'
import { api } from '../lib/api'

export interface BlockedUser {
  userId: number
  nickname: string
  blockedAt: string
}

interface BlockState {
  blocked: BlockedUser[]
  /** 거를 때 쓰는 집합. blocked와 항상 같이 바뀐다. */
  blockedIds: Set<number>
  load: () => Promise<void>
  /** 로그아웃하면 비운다. 다음 사람의 화면에 이전 사람의 차단이 남지 않게 한다. */
  clear: () => void
  block: (userId: number, nickname: string) => Promise<void>
  unblock: (userId: number) => Promise<void>
}

const withIds = (blocked: BlockedUser[]) => ({ blocked, blockedIds: new Set(blocked.map((b) => b.userId)) })

/**
 * 내 차단 목록. 목록은 서버에 있고(기기를 바꿔도 유지), 거르는 일은 화면이 한다(docs/decisions.md D-13).
 * REST로 받은 것과 실시간으로 받은 것을 같은 집합으로 거르므로, 차단하는 순간 기존 글도 바로 사라진다.
 */
export const useBlockStore = create<BlockState>((set, get) => ({
  ...withIds([]),

  load: async () => {
    const { data } = await api.get<BlockedUser[]>('/api/users/me/blocks')
    set(withIds(data))
  },

  clear: () => set(withIds([])),

  block: async (userId, nickname) => {
    await api.put(`/api/users/me/blocks/${userId}`)
    if (get().blockedIds.has(userId)) return
    set(withIds([{ userId, nickname, blockedAt: new Date().toISOString() }, ...get().blocked]))
  },

  unblock: async (userId) => {
    await api.delete(`/api/users/me/blocks/${userId}`)
    set(withIds(get().blocked.filter((b) => b.userId !== userId)))
  },
}))
