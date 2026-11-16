import dayjs from 'dayjs'

const pad2 = (value: number): string => String(value).padStart(2, '0')

/**
 * 秒 → 时长文本。
 * - 不足 1 小时：mm:ss（如 03:07）
 * - 超过 1 小时：h:mm:ss（如 1:02:33）
 */
export function formatDuration(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return '--'
  const total = Math.floor(seconds)
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const remainSeconds = total % 60
  if (hours > 0) {
    return `${hours}:${pad2(minutes)}:${pad2(remainSeconds)}`
  }
  return `${pad2(minutes)}:${pad2(remainSeconds)}`
}

/** 字节 → 可读文件大小（B / KB / MB / GB / TB，保留 1 位小数） */
export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) return '--'
  if (bytes < 1024) return `${Math.floor(bytes)} B`
  const units = ['KB', 'MB', 'GB', 'TB'] as const
  let size = bytes / 1024
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex += 1
  }
  const digits = size >= 100 ? 0 : 1
  return `${size.toFixed(digits)} ${units[unitIndex]}`
}

/** 时间 → 本地时间文本，默认 YYYY-MM-DD HH:mm（M1+ 列表页使用） */
export function formatDateTime(value: string | number | Date, template = 'YYYY-MM-DD HH:mm'): string {
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format(template) : '--'
}
