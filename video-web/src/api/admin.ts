import { http } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { AdminStats, OperationLogItem, OperationLogQuery } from '@/types/admin'

/**
 * P1 管理后台接口（F-15 播放统计 / F-19 操作日志），全部需要管理员身份。
 */

/**
 * 把操作日志查询参数转成 http.get 的 params：
 * 用户名裁剪后为空、模块未选择时不下发，避免生成 `module=` 之类的脏参数。
 */
function toLogParams(query: OperationLogQuery): Record<string, unknown> {
  const params: Record<string, unknown> = {}
  if (query.page !== undefined) params.page = query.page
  if (query.size !== undefined) params.size = query.size

  const username = query.username?.trim() ?? ''
  if (username.length > 0) params.username = username

  const module = query.module?.trim() ?? ''
  if (module.length > 0) params.module = module
  return params
}

/** 站点统计（视频数/分类数/剧集数/转码中/播放次数/观看时长）：GET /api/admin/stats */
export function getAdminStats(): Promise<AdminStats> {
  return http.get<AdminStats>('/admin/stats')
}

/** 操作日志分页列表：GET /api/admin/operation-logs?page=&size=&username=&module= */
export function listOperationLogs(query: OperationLogQuery): Promise<PageResult<OperationLogItem>> {
  return http.get<PageResult<OperationLogItem>>('/admin/operation-logs', toLogParams(query))
}
