import type { TranscodeStatus } from './transcode'

/** 秒传检查请求（POST /api/admin/upload/check） */
export interface UploadCheckRequest {
  /** 抽样指纹：前 2MB + 中间 2MB + 末尾 2MB + 文件大小 的 SHA-256（64 位） */
  fingerprint: string
  fileName: string
  fileSize: number
}

/** 秒传检查响应：命中返回 episodeId；未命中返回可续传会话信息 */
export interface UploadCheckResponse {
  exists: boolean
  /** 秒传命中：已存在的剧集 ID */
  episodeId?: number
  /** 未命中：上传会话 ID */
  uploadId?: string
  chunkSize?: number
  totalChunks?: number
  /** 未命中：已上传分片序号（断点续传） */
  uploadedChunks?: number[]
}

/** 初始化上传请求（POST /api/admin/upload/init） */
export interface UploadInitRequest {
  fileName: string
  fileSize: number
  fingerprint: string
}

/** 初始化上传响应 */
export interface UploadInitResponse {
  uploadId: string
  chunkSize: number
  totalChunks: number
}

/** 已上传分片查询响应（GET /api/admin/upload/{uploadId}/status） */
export interface UploadStatusResponse {
  uploadId: string
  chunkSize: number
  totalChunks: number
  uploadedChunks: number[]
}

/** 完成上传请求（POST /api/admin/upload/{uploadId}/complete） */
export interface UploadCompleteRequest {
  videoId: number
  /** 不传则自动取当前作品最大集数 +1 */
  episodeNo?: number
  title?: string
  /** AUTO：转码时自动截帧生成封面（作品无封面时生效） */
  coverMode?: 'AUTO' | 'NONE'
}

/** 完成上传响应 */
export interface UploadCompleteResponse {
  episodeId: number
  transcodeStatus: TranscodeStatus
}
