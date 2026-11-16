import { defineStore } from 'pinia'
import { getDeviceId } from '@/utils/device'

/**
 * 前台播放器 Store（文档 7.3.3 进度记忆）：
 * - deviceId：匿名访客设备标识，登录态无关
 * - localProgress：{ [episodeId]: 秒数 }，localStorage 缓存，服务端记录不可用时的兜底
 */

/** 本地进度缓存键：JSON 对象，键为 episodeId，值为秒数 */
export const PROGRESS_STORAGE_KEY = 'vidkeep_progress'

interface PlayerState {
  deviceId: string
  localProgress: Record<string, number>
}

/** 读取本地进度缓存；脏数据（非对象/非法数值）一律丢弃，避免影响播放续播 */
function readProgressMap(): Record<string, number> {
  try {
    const raw = window.localStorage.getItem(PROGRESS_STORAGE_KEY)
    if (raw === null || raw === '') return {}

    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) return {}

    const result: Record<string, number> = {}
    for (const [key, value] of Object.entries(parsed)) {
      if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
        result[key] = value
      }
    }
    return result
  } catch {
    // 隐私模式或缓存被清空：按无进度处理
    return {}
  }
}

/** 写入本地进度缓存；失败不影响播放本身 */
function writeProgressMap(map: Record<string, number>): void {
  try {
    window.localStorage.setItem(PROGRESS_STORAGE_KEY, JSON.stringify(map))
  } catch {
    // 忽略写入失败，本次会话仍以内存中的 localProgress 为准
  }
}

export const usePlayerStore = defineStore('player', {
  state: (): PlayerState => ({
    deviceId: getDeviceId(),
    localProgress: readProgressMap(),
  }),

  actions: {
    /** 保存某集本地播放进度（秒）；传入 0 表示清空（播完归零） */
    saveLocalProgress(episodeId: number, seconds: number): void {
      if (!Number.isFinite(seconds) || seconds < 0) return
      if (seconds <= 0) {
        if (!(String(episodeId) in this.localProgress)) return
        const next = { ...this.localProgress }
        delete next[String(episodeId)]
        this.localProgress = next
        writeProgressMap(next)
        return
      }
      const next = { ...this.localProgress, [String(episodeId)]: Math.floor(seconds) }
      this.localProgress = next
      writeProgressMap(next)
    },

    /** 读取某集本地播放进度（秒），无记录返回 0 */
    readLocalProgress(episodeId: number): number {
      const value = this.localProgress[String(episodeId)]
      return typeof value === 'number' && Number.isFinite(value) ? value : 0
    },
  },
})
