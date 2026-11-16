/**
 * 匿名访客设备标识（M2 播放进度记忆使用）。
 *
 * 访客免登录，用 deviceId 区分「同一台设备」的播放记录：
 * - 首次访问生成 `web-{uuid}` 并写入 localStorage
 * - 之后每次读取同一 ID，保证刷新/重开页面后进度可恢复
 * - localStorage 不可用（隐私模式、禁用 Cookie）时退化为内存 ID，本次会话内仍可记忆
 */

export const DEVICE_ID_STORAGE_KEY = 'vidkeep_device_id'

/** localStorage 不可用时的进程内兜底 ID（模块级变量，刷新即失效） */
let memoryDeviceId = ''

/** 生成随机片段：优先 crypto.randomUUID，非安全上下文则用时间戳 + 随机数兜底 */
function randomId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

/**
 * 读取（必要时生成并持久化）设备 ID，形如 `web-3f2a...`。
 * 同一页面生命周期内结果稳定，不会重复生成。
 */
export function getDeviceId(): string {
  if (memoryDeviceId !== '') return memoryDeviceId

  try {
    const cached = window.localStorage.getItem(DEVICE_ID_STORAGE_KEY)
    if (cached !== null && cached.length > 0) {
      memoryDeviceId = cached
      return cached
    }
    const generated = `web-${randomId()}`
    window.localStorage.setItem(DEVICE_ID_STORAGE_KEY, generated)
    memoryDeviceId = generated
    return generated
  } catch {
    // 访问或写入 localStorage 被拒绝：使用内存兜底，保证播放/上报流程不中断
    memoryDeviceId = `web-${randomId()}`
    return memoryDeviceId
  }
}
