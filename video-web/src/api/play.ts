import { http } from '@/api/request'
import type { PageResult } from '@/types/api'
import type { CategoryItem } from '@/types/category'
import type { PlayHistoryItem, PlayHistoryReport, PlayInfo } from '@/types/play'
import type {
  PublicVideoDetail,
  PublicVideoItem,
  PublicVideoListQuery,
} from '@/types/video'

/**
 * 前台播放相关接口（文档 6.2 播放模块 / 6.3.7、6.3.8）。
 * 全部为公开接口，无需登录；设备识别依赖 deviceId（匿名访客）。
 */

/** 前台视频列表：仅返回已上线作品，支持关键字与分类筛选（文档 6.2） */
export function listPublicVideos(
  query: PublicVideoListQuery,
): Promise<PageResult<PublicVideoItem>> {
  return http.get<PageResult<PublicVideoItem>>('/public/videos', {
    page: query.page,
    size: query.size,
    keyword: query.keyword,
    categoryId: query.categoryId,
  })
}

/** 前台视频详情：含剧集列表（只暴露播放需要的字段） */
export function getPublicVideoDetail(id: number): Promise<PublicVideoDetail> {
  return http.get<PublicVideoDetail>(`/public/videos/${id}`)
}

/** 前台分类列表：按 sort 升序返回 */
export function listPublicCategories(): Promise<CategoryItem[]> {
  return http.get<CategoryItem[]>('/public/categories')
}

/**
 * 获取播放地址（文档 6.3.7）：返回 HLS 地址、封面、时长与上次播放位置。
 * lastPosition 由后端按「≥95% 归零」处理过，可直接用于续播。
 */
export function getPlayInfo(
  videoId: number,
  episodeId: number,
  deviceId: string,
): Promise<PlayInfo> {
  return http.get<PlayInfo>(`/public/videos/${videoId}/episodes/${episodeId}/play`, {
    deviceId,
  })
}

/** 上报播放进度（文档 6.3.8）：每 15 秒一次，后端按 deviceId + episodeId 记忆 */
export function reportPlayHistory(report: PlayHistoryReport): Promise<null> {
  return http.post<null>('/public/play/history', report)
}

/** 查询某作品下各集播放进度（deviceId 维度），用于详情页/播放页兜底 */
export function listPlayHistory(
  deviceId: string,
  videoId: number,
): Promise<PlayHistoryItem[]> {
  return http.get<PlayHistoryItem[]>('/public/play/history', { deviceId, videoId })
}
