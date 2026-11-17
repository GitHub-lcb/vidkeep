/**
 * P1 管理后台类型（F-15 播放统计 / F-19 操作日志）。
 * 字段与后端契约一一对应，新增字段需同步后端 VO。
 */

/** 站点统计（GET /api/admin/stats，F-15 播放统计） */
export interface AdminStats {
  /** 作品总数（含未上线） */
  videoCount: number
  /** 分类数 */
  categoryCount: number
  /** 剧集/文件总数 */
  episodeCount: number
  /** 转码中任务数（排队 + 处理中） */
  transcodingCount: number
  /** 累计播放次数 */
  totalViewCount: number
  /** 累计观看时长（秒） */
  totalWatchSeconds: number
}

/** 操作日志项（GET /api/admin/operation-logs，F-19 操作日志） */
export interface OperationLogItem {
  id: number
  /** 操作人 id */
  userId: number
  /** 操作人用户名 */
  username: string
  /** 所属模块，如：视频 / 分类 / 上传 / 转码 */
  module: string
  /** 动作描述，如：删除视频 */
  action: string
  /** HTTP 方法：GET / POST / PUT / DELETE */
  method: string
  /** 请求路径 */
  path: string
  /** 操作详情，可能为空 */
  detail: string | null
  /** 客户端 IP，可能为空（如本地调用） */
  ip: string | null
  /** 接口耗时（毫秒） */
  durationMs: number
  /** 操作时间 */
  createdAt: string
}

/** 操作日志分页查询参数（空字符串表示不筛选，由 api/admin.ts 统一裁剪） */
export interface OperationLogQuery {
  page?: number
  size?: number
  /** 用户名精确/模糊筛选 */
  username?: string
  /** 模块筛选：视频 / 分类 / 上传 / 转码 */
  module?: string
}
