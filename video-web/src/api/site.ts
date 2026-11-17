import { http } from '@/api/request'

/**
 * 站点访问口令接口（F-18 站点访问口令）。
 * 两个接口都不需要登录；校验成功后由后端下发 HttpOnly Cookie，
 * 之后公开接口与媒体请求会自动携带（前端无需、也无法读取该 Cookie）。
 */

/** 站点口令状态（GET /api/public/site/status） */
export interface SiteStatus {
  /** 是否开启访问口令 */
  enabled: boolean
}

/** 口令校验结果（POST /api/public/site/verify） */
export interface SiteVerifyResult {
  verified: boolean
}

/** 查询站点是否开启访问口令：GET /api/public/site/status（免口令） */
export function getSiteStatus(): Promise<SiteStatus> {
  return http.get<SiteStatus>('/public/site/status')
}

/**
 * 提交访问口令：POST /api/public/site/verify。
 * 口令错误时后端返回 code 2006，由 http 封装抛 BizError，调用方展示 message。
 */
export function verifySitePassword(password: string): Promise<SiteVerifyResult> {
  return http.post<SiteVerifyResult>('/public/site/verify', { password })
}
