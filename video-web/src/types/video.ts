import type { TranscodeMode, TranscodeStatus } from './transcode'

/** 剧集/播放单元（后台详情、前台详情共用结构来源） */
export interface EpisodeItem {
  id: number
  videoId: number
  /** 第几集；单文件作品固定为 1 */
  episodeNo: number
  title: string | null
  playType: string
  /** 时长（秒） */
  duration: number | null
  fileSize: number | null
  width: number | null
  height: number | null
  /** 码率 bps */
  bitRate: number | null
  transcodeStatus: TranscodeStatus
  transcodeMode: TranscodeMode | null
  hlsMasterUrl: string | null
  createdAt: string
}

/** 后台视频列表项（GET /api/admin/videos） */
export interface VideoAdminItem {
  id: number
  title: string
  description: string | null
  coverUrl: string | null
  categoryId: number | null
  categoryName: string | null
  viewCount: number
  /** 1 上线，0 下线 */
  status: number
  episodeCount: number
  createdAt: string
  updatedAt: string
}

/** 后台视频详情（GET /api/admin/videos/{id}，含剧集） */
export interface VideoAdminDetail extends VideoAdminItem {
  episodes: EpisodeItem[]
}

/** 新建/编辑作品请求（POST/PUT /api/admin/videos） */
export interface VideoSaveRequest {
  title: string
  description?: string | null
  coverUrl?: string | null
  categoryId?: number | null
}

/** 上下架请求（PUT /api/admin/videos/{id}/status） */
export interface VideoStatusRequest {
  status: 0 | 1
}

/** 后台列表查询参数 */
export interface VideoListQuery {
  page?: number
  size?: number
  /** 标题模糊搜索 */
  title?: string
  status?: number
  categoryId?: number
}

/** 前台视频列表项（GET /api/public/videos，只返回已上线） */
export interface PublicVideoItem {
  id: number
  title: string
  coverUrl: string | null
  categoryId: number | null
  categoryName: string | null
  viewCount: number
  episodeCount: number
  duration: number | null
  createdAt: string
}

/** 前台剧集项（只暴露播放需要的字段） */
export interface PublicEpisodeItem {
  id: number
  episodeNo: number
  title: string | null
  duration: number | null
  transcodeStatus: TranscodeStatus
}

/** 前台视频详情（GET /api/public/videos/{id}） */
export interface PublicVideoDetail {
  id: number
  title: string
  description: string | null
  coverUrl: string | null
  categoryId: number | null
  categoryName: string | null
  viewCount: number
  createdAt: string
  episodes: PublicEpisodeItem[]
}

/** 前台列表查询参数 */
export interface PublicVideoListQuery {
  page?: number
  size?: number
  /** 标题搜索 */
  keyword?: string
  categoryId?: number
}
