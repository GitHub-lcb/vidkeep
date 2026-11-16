import type { AxiosProgressEvent } from 'axios'
import http from './request'
import type {
  UploadCheckRequest,
  UploadCheckResponse,
  UploadCompleteRequest,
  UploadCompleteResponse,
  UploadInitRequest,
  UploadInitResponse,
  UploadStatusResponse,
} from '@/types/upload'

/** 分片上传响应：索引 + 后端是否已接收（文档 6.3.4） */
export interface UploadChunkResponse {
  index: number
  received: boolean
}

/**
 * 秒传检查 / 续传会话查询：POST /api/admin/upload/check
 * 命中时返回 exists=true 与已存在的 episodeId；未命中返回可续传的会话信息。
 */
export function checkUpload(payload: UploadCheckRequest): Promise<UploadCheckResponse> {
  return http.post<UploadCheckResponse>('/admin/upload/check', payload)
}

/** 初始化上传会话：POST /api/admin/upload/init（check 未命中且未返回会话时兜底） */
export function initUpload(payload: UploadInitRequest): Promise<UploadInitResponse> {
  return http.post<UploadInitResponse>('/admin/upload/init', payload)
}

/**
 * 上传单个分片：PUT /api/admin/upload/{uploadId}/chunk/{index}
 * Body 为原始二进制（application/octet-stream），onProgress 回调当前分片的上传百分比。
 */
export function uploadChunk(
  uploadId: string,
  index: number,
  chunk: Blob,
  onProgress?: (percent: number) => void,
): Promise<UploadChunkResponse> {
  return http.put<UploadChunkResponse>(
    `/admin/upload/${encodeURIComponent(uploadId)}/chunk/${index}`,
    chunk,
    {
      headers: { 'Content-Type': 'application/octet-stream' },
      // 5MB 分片在慢速网络上可能超过默认 20s，单独放宽到 120s
      timeout: 120_000,
      onUploadProgress: (event: AxiosProgressEvent) => {
        if (onProgress === undefined || event.total === undefined || event.total <= 0) return
        const percent = Math.min(100, Math.round((event.loaded / event.total) * 100))
        onProgress(percent)
      },
    },
  )
}

/** 查询已上传分片（断点续传）：GET /api/admin/upload/{uploadId}/status */
export function getUploadStatus(uploadId: string): Promise<UploadStatusResponse> {
  return http.get<UploadStatusResponse>(`/admin/upload/${encodeURIComponent(uploadId)}/status`)
}

/** 完成上传（合并 / 创建 episode / 入队转码）：POST /api/admin/upload/{uploadId}/complete */
export function completeUpload(
  uploadId: string,
  payload: UploadCompleteRequest,
): Promise<UploadCompleteResponse> {
  return http.post<UploadCompleteResponse>(
    `/admin/upload/${encodeURIComponent(uploadId)}/complete`,
    payload,
  )
}
