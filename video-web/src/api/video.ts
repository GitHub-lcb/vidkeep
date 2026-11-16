import http from './request'
import type { PageResult } from '@/types/api'
import type {
  VideoAdminDetail,
  VideoAdminItem,
  VideoListQuery,
  VideoSaveRequest,
  VideoStatusRequest,
} from '@/types/video'

/**
 * 把列表查询参数转成 http.get 的 params。
 * 只传递有意义的字段：空标题 / 未选择的筛选条件不发给后端，避免生成 `status=undefined` 之类的脏参数。
 */
function toListParams(query: VideoListQuery): Record<string, unknown> {
  const params: Record<string, unknown> = {}
  if (query.page !== undefined) params.page = query.page
  if (query.size !== undefined) params.size = query.size

  const title = query.title?.trim() ?? ''
  if (title.length > 0) params.title = title
  if (query.status !== undefined) params.status = query.status
  if (query.categoryId !== undefined) params.categoryId = query.categoryId
  return params
}

/** 后台视频分页列表：GET /api/admin/videos（文档 6.2 / 6.3 视频管理） */
export function listAdminVideos(query: VideoListQuery): Promise<PageResult<VideoAdminItem>> {
  return http.get<PageResult<VideoAdminItem>>('/admin/videos', toListParams(query))
}

/** 后台视频详情（含剧集）：GET /api/admin/videos/{id} */
export function getAdminVideo(id: number): Promise<VideoAdminDetail> {
  return http.get<VideoAdminDetail>(`/admin/videos/${id}`)
}

/** 新建作品壳：POST /api/admin/videos，成功返回新视频 id */
export function createAdminVideo(payload: VideoSaveRequest): Promise<number> {
  return http.post<number>('/admin/videos', payload)
}

/** 编辑作品基础信息：PUT /api/admin/videos/{id} */
export function updateAdminVideo(id: number, payload: VideoSaveRequest): Promise<null> {
  return http.put<null>(`/admin/videos/${id}`, payload)
}

/** 删除作品（级联删除剧集与文件）：DELETE /api/admin/videos/{id} */
export function deleteAdminVideo(id: number): Promise<null> {
  return http.delete<null>(`/admin/videos/${id}`)
}

/** 上下架：PUT /api/admin/videos/{id}/status，status 1 上线 / 0 下线 */
export function updateAdminVideoStatus(id: number, payload: VideoStatusRequest): Promise<null> {
  return http.put<null>(`/admin/videos/${id}/status`, payload)
}

/** 删除单集（含原片与 HLS 产物）：DELETE /api/admin/episodes/{id} */
export function deleteAdminEpisode(id: number): Promise<null> {
  return http.delete<null>(`/admin/episodes/${id}`)
}
