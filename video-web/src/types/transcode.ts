/** 转码状态机（与后端 video_episode.transcode_status 一致） */
export type TranscodeStatus = 'NONE' | 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED'

/** 转码方式：REMUX 只转封装（快、无损），TRANSCODE 重编码 */
export type TranscodeMode = 'REMUX' | 'TRANSCODE'

/** 转码进度（GET /api/admin/episodes/{id}/transcode） */
export interface TranscodeProgress {
  episodeId: number
  /** 转码状态 */
  status: TranscodeStatus
  /** 0-100 */
  progress: number
  /** 转码方式，尚未开始为 null */
  mode: TranscodeMode | null
  /** 失败原因，成功或进行中为 null */
  errorMsg: string | null
}
