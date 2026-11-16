import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, me as meApi } from '@/api/auth'
import { USER_ROLE, type LoginRequest, type UserInfo } from '@/types/auth'
import { clearAuthStorage, readToken, readUser, writeToken, writeUser } from '@/utils/storage'

interface UserState {
  token: string
  user: UserInfo | null
}

/**
 * 登录态 Store：
 * - token / user 双向持久化到 localStorage（key: vidkeep_token / vidkeep_user）
 * - 真正的权限校验以后端为准，前端守卫只做体验层拦截
 */
export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: readToken(),
    user: readUser(),
  }),

  getters: {
    /** 本地是否有登录态；Token 是否真的有效由后端 2001 判断 */
    isLoggedIn: (state): boolean => state.token.length > 0,
    /** 是否管理员（路由守卫使用，文档 7.4.3） */
    isAdmin: (state): boolean => state.user?.role === USER_ROLE.ADMIN,
    /** 顶栏展示名：优先昵称，其次用户名 */
    displayName: (state): string => state.user?.nickname || state.user?.username || '管理员',
  },

  actions: {
    /** 登录并持久化登录态 */
    async login(payload: LoginRequest): Promise<UserInfo> {
      const result = await loginApi(payload)
      this.token = result.token
      this.user = result.user
      writeToken(result.token)
      writeUser(result.user)
      return result.user
    },

    /** 拉取当前用户信息（刷新页面后可用来校准本地缓存） */
    async fetchMe(): Promise<UserInfo | null> {
      if (this.token.length === 0) return null
      const user = await meApi()
      this.user = user
      writeUser(user)
      return user
    },

    /** 退出登录：后端失败也要清本地，保证一定能退出 */
    async logout(): Promise<void> {
      try {
        if (this.token.length > 0) {
          await logoutApi()
        }
      } catch {
        // 后端不可用或 Token 已失效时忽略，本地清理在 finally 中执行
      } finally {
        this.clearAuth()
      }
    },

    /** 只清理登录态（Token 失效、角色不符时使用） */
    clearAuth(): void {
      this.token = ''
      this.user = null
      clearAuthStorage()
    },
  },
})
