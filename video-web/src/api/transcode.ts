import http from './request'
import type { TranscodeProgress } from '@/types/transcode'

/** 查询转码进度：GET /api/admin/episodes/{episodeId}/transcode（前端每 2s 轮询） */
export function getTranscodeProgress(episodeId: number): Promise<TranscodeProgress> {
  return http.get<TranscodeProgress>(`/admin/episodes/${episodeId}/transcode`)
}

/** 重试转码：POST /api/admin/episodes/{episodeId}/transcode/retry（仅 FAILED 可重试） */
export function retryTranscode(episodeId: number): Promise<null> {
  return http.post<null>(`/admin/episodes/${episodeId}/transcode/retry`)
}

/** 取消转码：POST /api/admin/episodes/{episodeId}/transcode/cancel */
export function cancelTranscode(episodeId: number): Promise<null> {
  return http.post<null>(`/admin/episodes/${episodeId}/transcode/cancel`)
}
