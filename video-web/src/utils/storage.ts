import type { UserInfo } from '@/types/auth'

/**
 * 登录态持久化：Token 与用户信息都放 localStorage。
 * 键名集中在此，api/request 与 stores/user 共用，避免各写一份字符串。
 */
export const TOKEN_STORAGE_KEY = 'vidkeep_token'
export const USER_STORAGE_KEY = 'vidkeep_user'

/** 运行时收窄 localStorage 里的用户信息，避免把脏数据当 UserInfo 用 */
function isUserInfo(value: unknown): value is UserInfo {
  if (typeof value !== 'object' || value === null) return false
  const record = value as Record<string, unknown>
  return (
    typeof record.id === 'number' &&
    typeof record.username === 'string' &&
    typeof record.nickname === 'string' &&
    typeof record.role === 'string'
  )
}

export function readToken(): string {
  try {
    return window.localStorage.getItem(TOKEN_STORAGE_KEY) ?? ''
  } catch {
    // 隐私模式下访问 localStorage 可能抛错，按未登录处理
    return ''
  }
}

export function writeToken(token: string): void {
  try {
    window.localStorage.setItem(TOKEN_STORAGE_KEY, token)
  } catch {
    // 写入失败不影响本次会话内存中的登录态
  }
}

export function readUser(): UserInfo | null {
  try {
    const raw = window.localStorage.getItem(USER_STORAGE_KEY)
    if (raw === null || raw === '') return null
    const parsed: unknown = JSON.parse(raw)
    return isUserInfo(parsed) ? parsed : null
  } catch {
    return null
  }
}

export function writeUser(user: UserInfo | null): void {
  try {
    if (user === null) {
      window.localStorage.removeItem(USER_STORAGE_KEY)
      return
    }
    window.localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user))
  } catch {
    // 同上，忽略写入失败
  }
}

/** 退出登录 / Token 失效时清理本地登录态 */
export function clearAuthStorage(): void {
  try {
    window.localStorage.removeItem(TOKEN_STORAGE_KEY)
    window.localStorage.removeItem(USER_STORAGE_KEY)
  } catch {
    // 忽略
  }
}
