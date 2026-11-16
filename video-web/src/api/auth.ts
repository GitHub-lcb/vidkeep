import http from './request'
import type { LoginRequest, LoginResponse, UserInfo } from '@/types/auth'

/**
 * 登录（文档 6.3.1）：POST /api/auth/login
 * 成功后返回 token（单位秒的 expiresIn）与用户信息。
 */
export function login(payload: LoginRequest): Promise<LoginResponse> {
  return http.post<LoginResponse>('/auth/login', payload)
}

/** 当前登录用户信息：GET /api/auth/me（需 Authorization: Bearer <jwt>） */
export function me(): Promise<UserInfo> {
  return http.get<UserInfo>('/auth/me')
}

/** 登出：POST /api/auth/logout（后端返回 data: null，前端同时清理本地登录态） */
export function logout(): Promise<null> {
  return http.post<null>('/auth/logout')
}
