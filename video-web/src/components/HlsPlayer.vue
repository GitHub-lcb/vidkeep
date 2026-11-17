<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import Artplayer from 'artplayer'
import Hls from 'hls.js'
import { throttle } from '@/utils/throttle'

/**
 * 通用 HLS 播放器（文档 7.3.2）：
 * - hls.js 支持 MSE 时用 hls.js 播放（maxBufferLength 30 秒）
 * - Safari / iOS 走 video 原生 HLS
 * - 每 15 秒节流抛一次 timeupdate，供上层做进度记忆与服务端上报
 * - 组件卸载或 ArtPlayer destroy 时销毁 hls 实例，避免内存泄漏
 *
 * 视觉（Qoder 规范）：容器 16:9、圆角 12、播放舞台底色 --q-player-bg；
 * 控制条主色走 --art-theme: var(--q-primary)，深浅主题自动适配。
 *
 * 注意：url 变化时请由父组件用 :key 触发重建（播放器实例不做热切换）。
 */

interface Props {
  /** HLS 播放地址，如 /hls/20/master.m3u8 */
  url: string
  /** 封面地址，无封面传 null */
  poster?: string | null
  /** 续播起始位置（秒），> 0 时在 loadedmetadata 后 seek */
  startPosition?: number
}

const props = withDefaults(defineProps<Props>(), {
  poster: null,
  startPosition: 0,
})

const emit = defineEmits<{
  /** 播放进度（秒），已按 15 秒节流 */
  timeupdate: [seconds: number]
  /** 播放结束 */
  ended: []
}>()

const containerRef = ref<HTMLDivElement | null>(null)

/** ArtPlayer 实例与 hls.js 实例都放组件作用域，卸载时统一销毁 */
let art: Artplayer | null = null
let hls: Hls | null = null
/** 组件是否已卸载：节流尾调用可能在卸载后触发，用它拦截 */
let disposed = false
/** 续播位置是否已应用，避免用户拖动后再次被拉回 */
let startApplied = false
/** 是否已真正开始播放：播放前（如 seek 到续播点）不抛 timeupdate，避免用 0 覆盖服务端进度 */
let playbackStarted = false

/** 上报进度：15 秒节流，卸载后不再 emit */
const emitTimeUpdate = throttle((seconds: number): void => {
  if (disposed) return
  emit('timeupdate', seconds)
}, 15_000)

/** loadedmetadata 后应用续播位置；超出时长时收尾到倒数 1 秒 */
function applyStartPosition(player: Artplayer): void {
  if (startApplied || props.startPosition <= 0) return
  startApplied = true
  const duration = player.duration
  const target =
    Number.isFinite(duration) && duration > 0
      ? Math.min(props.startPosition, Math.max(duration - 1, 0))
      : props.startPosition
  if (target > 0) {
    player.seek = target
  }
}

/** 创建播放器：customType.m3u8 内按浏览器能力选择 hls.js 或原生 HLS */
function createPlayer(container: HTMLDivElement): void {
  art = new Artplayer(
    {
      container,
      url: props.url,
      poster: props.poster ?? '',
      type: 'm3u8',
      customType: {
        m3u8(video, url, player) {
          if (Hls.isSupported()) {
            const hlsInstance = new Hls({ maxBufferLength: 30 })
            hls = hlsInstance
            hlsInstance.loadSource(url)
            hlsInstance.attachMedia(video)
            // 网络/媒体错误尽量自愈，无法恢复时给出提示
            hlsInstance.on(Hls.Events.ERROR, (_event, data) => {
              if (!data.fatal) return
              if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
                hlsInstance.startLoad()
              } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
                hlsInstance.recoverMediaError()
              } else {
                player.notice.show = '视频加载失败，请刷新后重试'
                hlsInstance.destroy()
                hls = null
              }
            })
            // ArtPlayer 销毁时同步销毁 hls.js（文档 7.3.2 写法）
            player.on('destroy', () => {
              hlsInstance.destroy()
              if (hls === hlsInstance) hls = null
            })
          } else if (video.canPlayType('application/vnd.apple.mpegurl') !== '') {
            // Safari / iOS 原生 HLS 回退
            video.src = url
          } else {
            player.notice.show = '当前浏览器不支持 HLS 播放'
          }
        },
      },
      // 控制栏能力：倍速、设置、全屏、移动端自动横屏（文档 7.3.2）
      playbackRate: true,
      setting: true,
      fullscreen: true,
      autoOrientation: true,
      playsInline: true,
      lang: 'zh-cn',
      // 控制条主色交给设计令牌（var(--q-primary) 随深浅主题自动切换）
      theme: 'var(--q-primary)',
      volume: 0.7,
      autoSize: false,
    },
    (player) => {
      // 元数据就绪后再 seek，避免 hls.js 还没拿到时长导致设置无效
      player.on('video:loadedmetadata', () => {
        applyStartPosition(player)
      })

      // 首次播放后才允许上报：续播 seek 阶段 currentTime 可能暂时为 0
      player.on('video:play', () => {
        playbackStarted = true
      })

      player.on('video:timeupdate', () => {
        if (disposed || !playbackStarted) return
        emitTimeUpdate(player.currentTime)
      })

      player.on('video:ended', () => {
        if (disposed) return
        emit('ended')
      })
    },
  )
}

onMounted(() => {
  const container = containerRef.value
  if (container === null) return
  createPlayer(container)
})

onBeforeUnmount(() => {
  disposed = true
  // art.destroy() 会触发 'destroy' 事件销毁 hls（customType 内注册）
  if (art !== null) {
    art.destroy()
    art = null
  }
  // 兜底：若 hls 在 destroy 事件前仍未释放（如异常路径）手动销毁
  if (hls !== null) {
    hls.destroy()
    hls = null
  }
})
</script>

<template>
  <div ref="containerRef" class="hls-player" />
</template>

<style scoped lang="scss">
@use '../styles/variables' as *;

.hls-player {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: var(--q-player-bg);
  border-radius: var(--q-r-xl);

  // ArtPlayer 默认铺满容器：只做圆角裁切 + 设计令牌映射（颜色不写死）
  :deep(.art-video-player) {
    --art-theme: var(--q-primary);
    --art-border-radius: var(--q-r-lg);
    --art-progress-height: 4px;
    --art-control-height: 44px;
    --art-control-icon-size: 32px;
    --art-widget-background: rgba(8, 8, 7, 0.86);
    --art-tip-background: rgba(8, 8, 7, 0.72);
    border-radius: inherit;
  }

  // 全屏 / 网页全屏时取消圆角，避免四角露出舞台底色
  :deep(.art-video-player.art-fullscreen),
  :deep(.art-video-player.art-fullscreen-web) {
    border-radius: 0;
  }

  // 错误/提示浮层：紧凑 12px 文案 + 令牌圆角（视频画面上的深色浮层除外）
  :deep(.art-notice) {
    font-size: var(--q-fs-12);
    line-height: var(--q-lh-18);
  }

  :deep(.art-notice-inner) {
    padding: 6px 10px;
    line-height: var(--q-lh-18);
    border-radius: var(--q-r-md);
  }

  @include q-mobile {
    border-radius: var(--q-r-lg);
  }
}
</style>
