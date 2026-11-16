/** 播放地址（GET /api/public/videos/{videoId}/episodes/{episodeId}/play?deviceId=...） */
export interface PlayInfo {
  type: 'HLS'
  /** HLS master.m3u8 的站点相对地址，如 /hls/20/master.m3u8 */
  url: string
  /** 封面地址 */
  poster: string | null
  /** 时长（秒） */
  duration: number | null
  /** 上次播放位置（秒），无记录为 0；≥95% 视为看完并归零 */
  lastPosition: number
}

/** 上报播放进度（POST /api/public/play/history） */
export interface PlayHistoryReport {
  deviceId: string
  videoId: number
  episodeId: number
  /** 秒 */
  position: number
  /** 秒 */
  duration?: number | null
}

/** 播放进度项（GET /api/public/play/history?deviceId=...&videoId=...） */
export interface PlayHistoryItem {
  episodeId: number
  position: number
  duration: number | null
  updatedAt: string
}
