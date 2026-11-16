/** 用户角色（文档 7.4.3 权限模型：本期只有 ADMIN 生效，USER 预留） */
export const USER_ROLE = {
  ADMIN: 'ADMIN',
  USER: 'USER',
} as const

export type UserRole = (typeof USER_ROLE)[keyof typeof USER_ROLE]

/** POST /api/auth/login 请求体 */
export interface LoginRequest {
  username: string
  password: string
}

/** 用户信息：GET /api/auth/me 的 data，同时是登录响应里的 user */
export interface UserInfo {
  id: number
  username: string
  nickname: string
  /** 后端以字符串返回，前端用 USER_ROLE 判断 */
  role: string
}

/** POST /api/auth/login 响应 data */
export interface LoginResponse {
  token: string
  /** 有效期，单位秒，默认 604800（7 天） */
  expiresIn: number
  user: UserInfo
}
