/**
 * 后端统一响应结构（对应文档 6.1「统一响应体」）。
 *
 * 业务错误同样返回 HTTP 200，由 code 判断结果：
 * - code === 0 表示成功，解包后的 data 即业务数据
 * - code !== 0 表示业务错误，由 api/request.ts 抛出 BizError
 */
export interface Result<T> {
  /** 0 成功；非 0 为业务错误码 */
  code: number
  /** 提示信息，成功时为 ok */
  message: string
  /** 业务数据 */
  data: T
}

/** 统一分页响应（文档 6.1「分页响应」） */
export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

/** 业务错误码（文档 6.1「错误码表」） */
export const ERROR_CODE = {
  SUCCESS: 0,
  PARAM_INVALID: 1001,
  PAYLOAD_TOO_LARGE: 1002,
  UNAUTHORIZED: 2001,
  LOGIN_FAILED: 2002,
  FORBIDDEN: 2003,
  /** P1：站点访问口令未通过 */
  SITE_PASSWORD_REQUIRED: 2004,
  /** P1：播放地址签名无效或已过期 */
  PLAY_TOKEN_INVALID: 2005,
  /** P1：站点访问口令错误 */
  SITE_PASSWORD_INCORRECT: 2006,
  NOT_FOUND: 3001,
  STATUS_NOT_ALLOWED: 3002,
  UPLOAD_SESSION_NOT_FOUND: 4001,
  CHUNK_INDEX_OUT_OF_RANGE: 4002,
  MERGE_FAILED: 4003,
  DISK_FULL: 4004,
  TRANSCODE_TASK_FAILED: 5001,
  FFMPEG_FAILED: 5002,
  SYSTEM_ERROR: 9001,
} as const

export type ErrorCode = (typeof ERROR_CODE)[keyof typeof ERROR_CODE]
