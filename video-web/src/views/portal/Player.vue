<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPlayInfo, getPublicVideoDetail, reportPlayHistory } from '@/api/play'
import { isBizError } from '@/api/request'
import HlsPlayer from '@/components/HlsPlayer.vue'
import { usePlayerStore } from '@/stores/player'
import { ERROR_CODE } from '@/types/api'
import { formatDuration } from '@/utils/format'
import type { PlayInfo } from '@/types/play'
import type { TranscodeStatus } from '@/types/transcode'
import type { PublicEpisodeItem, PublicVideoDetail } from '@/types/video'

/**
 * 前台播放页（/play/:videoId/:episodeId）：
 * - 先取详情判断转码状态，DONE 才请求播放地址（文档 6.3.7）
 * - 续播位置 = max(服务端 lastPosition, 本地缓存)，服务端由后端保证 ≥95% 归零
 * - 播放中每 15 秒上报进度（文档 7.3.3），播完上报完整时长并清空本地进度
 * - 支持剧集切换与返回详情
 */

const route = useRoute()
const router = useRouter()
const playerStore = usePlayerStore()

const detail = ref<PublicVideoDetail | null>(null)
const playInfo = ref<PlayInfo | null>(null)
const currentEpisode = ref<PublicEpisodeItem | null>(null)
const loading = ref(true)
const errorMessage = ref('')
/** 播放地址签名无效或过期（code 2005，F-17）：提示重新获取播放地址 */
const playUrlExpired = ref(false)
/** 续播起始位置（秒），0 表示从头播放 */
const startPosition = ref(0)

/**
 * 已成功开始播放的剧集信息。
 * 路由可能已切到下一集，进度上报必须用这里的 id，不能用当前路由参数。
 */
const loadedVideoId = ref(0)
const loadedEpisodeId = ref(0)
const loadedDuration = ref<number | null>(null)

/** 最近一次 timeupdate 的位置，用于切换剧集/离开页面时补报 */
let lastKnownPosition = 0

const TRANSCODE_STATUS_TEXT: Record<TranscodeStatus, string> = {
  NONE: '待转码',
  PENDING: '排队中',
  PROCESSING: '转码中',
  FAILED: '转码失败',
  DONE: '可播放',
}

/** 路由参数转数字；非法值返回 0 */
function parseRouteId(value: string | string[]): number {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 0
}

const videoId = computed((): number => parseRouteId(route.params.videoId))
const episodeId = computed((): number => parseRouteId(route.params.episodeId))

/** 剧集按集数升序（详情接口返回的剧集列表） */
const sortedEpisodes = computed((): PublicEpisodeItem[] => {
  if (detail.value === null) return []
  return [...detail.value.episodes].sort((left, right) => left.episodeNo - right.episodeNo)
})

/** 当前集标题：无标题时用「第 N 集」兜底 */
const currentEpisodeTitle = computed((): string => {
  const episode = currentEpisode.value
  if (episode === null) return ''
  return episode.title !== null && episode.title.length > 0 ? episode.title : `第 ${episode.episodeNo} 集`
})

/** 当前集转码状态；详情尚未返回时按 NONE 处理 */
const transcodeStatus = computed((): TranscodeStatus => currentEpisode.value?.transcodeStatus ?? 'NONE')

/** 转码未完成：显示提示而不是播放器（任务要求） */
const showTranscodeNotice = computed((): boolean => {
  return loading.value === false && playInfo.value === null && currentEpisode.value !== null && currentEpisode.value.transcodeStatus !== 'DONE'
})

/** 转码提示文案 */
const transcodeNoticeText = computed((): string => {
  switch (transcodeStatus.value) {
    case 'FAILED':
      return '该集转码失败，请联系管理员重新转码后再试。'
    case 'PENDING':
      return '该集正在排队等待转码，稍后再来看看吧。'
    case 'PROCESSING':
      return '该集正在转码中，完成后即可播放。'
    default:
      return '该集尚未转码，请稍后再来看看。'
  }
})

const transcodeStatusText = computed((): string => TRANSCODE_STATUS_TEXT[transcodeStatus.value])

/** 路由是否已切走（异步请求返回后用于丢弃过期结果） */
function isStale(requestVideoId: number, requestEpisodeId: number): boolean {
  return videoId.value !== requestVideoId || episodeId.value !== requestEpisodeId
}

/** 重置一次加载的播放状态 */
function resetPlayerState(): void {
  playInfo.value = null
  detail.value = null
  currentEpisode.value = null
  errorMessage.value = ''
  playUrlExpired.value = false
  startPosition.value = 0
  loadedVideoId.value = 0
  loadedEpisodeId.value = 0
  loadedDuration.value = null
  lastKnownPosition = 0
}

/**
 * 计算续播位置：服务端 lastPosition 与本地缓存取较大值（服务端权威、本地兜底）；
 * 超过 95% 视为已看完，从头播放（后端已处理，这里兜底本地缓存）。
 */
function resolveStartPosition(
  serverLastPosition: number,
  localLastPosition: number,
  duration: number | null,
): number {
  const candidate = Math.max(serverLastPosition, localLastPosition)
  if (!Number.isFinite(candidate) || candidate <= 0) return 0
  if (duration !== null && duration > 0 && candidate >= duration * 0.95) return 0
  return Math.floor(candidate)
}

/** 上报播放进度：失败静默，不打断播放（下一次 timeupdate 会再次上报） */
async function reportPosition(
  targetVideoId: number,
  targetEpisodeId: number,
  position: number,
  duration: number | null,
): Promise<void> {
  if (targetVideoId <= 0 || targetEpisodeId <= 0 || !Number.isFinite(position)) return
  try {
    await reportPlayHistory({
      deviceId: playerStore.deviceId,
      videoId: targetVideoId,
      episodeId: targetEpisodeId,
      position: Math.max(0, Math.round(position)),
      duration,
    })
  } catch {
    // 无网络/后端异常时不打扰用户
  }
}

/** 加载详情 + 播放地址；切换剧集时会重新执行 */
async function load(): Promise<void> {
  const requestVideoId = videoId.value
  const requestEpisodeId = episodeId.value

  // 切集或离开前，把上一集最后进度补报一次，减少最近 15 秒进度丢失
  if (loadedEpisodeId.value > 0 && lastKnownPosition > 0) {
    void reportPosition(loadedVideoId.value, loadedEpisodeId.value, lastKnownPosition, loadedDuration.value)
  }

  resetPlayerState()

  if (requestVideoId <= 0 || requestEpisodeId <= 0) {
    loading.value = false
    errorMessage.value = '播放地址无效'
    return
  }

  loading.value = true

  try {
    // 1. 详情：判断剧集是否存在、是否转码完成（未完成不请求播放地址）
    const loadedDetail = await getPublicVideoDetail(requestVideoId)
    if (isStale(requestVideoId, requestEpisodeId)) return
    detail.value = loadedDetail

    const episode = loadedDetail.episodes.find((item) => item.id === requestEpisodeId) ?? null
    currentEpisode.value = episode
    if (episode === null) {
      errorMessage.value = '剧集不存在或已被删除'
      return
    }
    if (episode.transcodeStatus !== 'DONE') return

    // 2. 播放地址：带 deviceId 取上次播放位置（文档 6.3.7）
    const info = await getPlayInfo(requestVideoId, requestEpisodeId, playerStore.deviceId)
    if (isStale(requestVideoId, requestEpisodeId)) return

    playInfo.value = info
    loadedVideoId.value = requestVideoId
    loadedEpisodeId.value = requestEpisodeId
    loadedDuration.value = info.duration ?? episode.duration ?? null
    startPosition.value = resolveStartPosition(
      info.lastPosition,
      playerStore.readLocalProgress(requestEpisodeId),
      loadedDuration.value,
    )
  } catch (error) {
    if (isStale(requestVideoId, requestEpisodeId)) return
    // 2005：播放地址签名无效/过期（F-17），单独提示并允许重新获取
    if (isBizError(error) && error.code === ERROR_CODE.PLAY_TOKEN_INVALID) {
      playUrlExpired.value = true
      errorMessage.value = '播放地址已过期，需要重新获取后继续播放。'
    } else {
      errorMessage.value = isBizError(error) ? error.message : '播放信息加载失败，请稍后重试'
    }
  } finally {
    if (!isStale(requestVideoId, requestEpisodeId)) loading.value = false
  }
}

/** 播放中进度回调：每 15 秒一次（HlsPlayer 内已节流） */
function handleTimeUpdate(seconds: number): void {
  if (!Number.isFinite(seconds) || seconds < 0) return
  lastKnownPosition = seconds

  const targetEpisodeId = loadedEpisodeId.value
  if (targetEpisodeId <= 0) return

  // 本地缓存 + 服务端上报（文档 7.3.3）
  playerStore.saveLocalProgress(targetEpisodeId, seconds)
  void reportPosition(loadedVideoId.value, targetEpisodeId, seconds, loadedDuration.value)
}

/** 播放结束：上报完整时长，本地进度归零，下次从头播放 */
function handleEnded(): void {
  const targetVideoId = loadedVideoId.value
  const targetEpisodeId = loadedEpisodeId.value
  if (targetEpisodeId <= 0) return

  const duration = loadedDuration.value ?? 0
  void reportPosition(targetVideoId, targetEpisodeId, duration, duration > 0 ? duration : null)
  playerStore.saveLocalProgress(targetEpisodeId, 0)
  lastKnownPosition = 0
  ElMessage.success('本集已播完')
}

/** 剧集切换：仅改路由，页面状态由 watch 统一重载 */
function switchEpisode(episode: PublicEpisodeItem): void {
  if (episode.id === episodeId.value) return
  void router.push(`/play/${videoId.value}/${episode.id}`)
}

/** 剧集标题：无标题时用「第 N 集」兜底 */
function episodeTitle(episode: PublicEpisodeItem): string {
  return episode.title !== null && episode.title.length > 0 ? episode.title : `第 ${episode.episodeNo} 集`
}

/** 转码状态标签样式 */
function statusClass(status: TranscodeStatus): string {
  if (status === 'FAILED') return 'is-failed'
  return 'is-processing'
}

watch([videoId, episodeId], () => {
  void load()
}, { immediate: true })

onBeforeUnmount(() => {
  // 离开页面时补报最后一次进度（best effort，不等待）
  if (loadedEpisodeId.value > 0 && lastKnownPosition > 0) {
    void reportPosition(loadedVideoId.value, loadedEpisodeId.value, lastKnownPosition, loadedDuration.value)
  }
})
</script>

<template>
  <div class="player-page">
    <div class="player-page__bar">
      <RouterLink class="player-page__back" :to="`/videos/${videoId}`">← 返回详情</RouterLink>
      <span class="player-page__crumb">
        {{ detail !== null ? detail.title : '播放' }}
        <template v-if="currentEpisode !== null"> · {{ currentEpisodeTitle }}</template>
      </span>
    </div>

    <!-- 加载态 -->
    <section v-if="loading" class="player-loading" aria-busy="true" aria-label="播放信息加载中">
      <div class="player-loading__screen" />
      <div class="player-loading__line" />
      <div class="player-loading__line player-loading__line--short" />
    </section>

    <!-- 错误态（含播放地址过期 2005） -->
    <section v-else-if="errorMessage !== ''" class="player-state">
      <p class="player-state__icon" aria-hidden="true">!</p>
      <h1 class="player-state__title">{{ playUrlExpired ? '播放地址已过期' : '无法播放' }}</h1>
      <p class="player-state__desc">{{ errorMessage }}</p>
      <div class="player-state__actions">
        <button type="button" class="player-state__button" @click="load">
          {{ playUrlExpired ? '重新获取播放地址' : '重新加载' }}
        </button>
        <RouterLink class="player-state__link" :to="`/videos/${videoId}`">返回详情</RouterLink>
        <RouterLink class="player-state__link" to="/">返回首页</RouterLink>
      </div>
    </section>

    <!-- 转码未完成：显示提示而不是播放器 -->
    <section v-else-if="showTranscodeNotice" class="player-state">
      <p class="player-state__icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26">
          <circle cx="12" cy="12" r="8.2" fill="none" stroke="currentColor" stroke-width="1.4" />
          <path d="M12 7.6V12l3 1.8" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
        </svg>
      </p>
      <h1 class="player-state__title">{{ transcodeStatusText }}</h1>
      <p class="player-state__desc">{{ transcodeNoticeText }}</p>
      <div class="player-state__actions">
        <button type="button" class="player-state__button" @click="load">刷新状态</button>
        <RouterLink class="player-state__link" :to="`/videos/${videoId}`">返回详情</RouterLink>
      </div>
    </section>

    <!-- 播放器 + 剧集侧栏 -->
    <div v-else-if="playInfo !== null" class="player-layout">
      <section class="player-main">
        <HlsPlayer
          :key="playInfo.url"
          :url="playInfo.url"
          :poster="playInfo.poster"
          :start-position="startPosition"
          @timeupdate="handleTimeUpdate"
          @ended="handleEnded"
        />

        <div class="player-info">
          <h1 class="player-info__title">{{ detail?.title }}</h1>
          <p class="player-info__meta">
            <span v-if="detail !== null && detail.categoryName !== null" class="player-info__tag">
              {{ detail.categoryName }}
            </span>
            <span>{{ currentEpisodeTitle }}</span>
            <span v-if="detail !== null" class="player-info__dot" aria-hidden="true">·</span>
            <span v-if="detail !== null">共 {{ sortedEpisodes.length }} 集</span>
            <span class="player-info__dot" aria-hidden="true">·</span>
            <span>{{ detail?.viewCount ?? 0 }} 次播放</span>
          </p>
          <p v-if="detail !== null && detail.description !== null && detail.description.length > 0" class="player-info__desc">
            {{ detail.description }}
          </p>
        </div>
      </section>

      <aside class="player-aside">
        <div class="player-aside__header">
          <h2 class="player-aside__title">剧集</h2>
          <span class="player-aside__count">{{ sortedEpisodes.length }} 集</span>
        </div>

        <div class="player-episode-list">
          <button
            v-for="episode in sortedEpisodes"
            :key="episode.id"
            type="button"
            class="player-episode"
            :class="{ 'is-active': episode.id === episodeId }"
            @click="switchEpisode(episode)"
          >
            <span class="player-episode__no">第 {{ episode.episodeNo }} 集</span>
            <span class="player-episode__title" :title="episodeTitle(episode)">{{ episodeTitle(episode) }}</span>
            <span
              v-if="episode.transcodeStatus !== 'DONE'"
              class="player-episode__status"
              :class="statusClass(episode.transcodeStatus)"
            >
              {{ TRANSCODE_STATUS_TEXT[episode.transcodeStatus] }}
            </span>
            <span v-else-if="episode.duration !== null && episode.duration > 0" class="player-episode__duration">
              {{ formatDuration(episode.duration) }}
            </span>
          </button>
        </div>

        <RouterLink class="player-aside__detail" :to="`/videos/${videoId}`">返回详情页</RouterLink>
      </aside>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.player-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px 24px 64px;

  @include vk-mobile {
    padding: 12px 14px 40px;
  }
}

.player-page__bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0 16px;
}

.player-page__back {
  flex-shrink: 0;
  font-size: 13px;
  color: $vk-text-muted;
  transition: color 0.18s ease;

  &:hover {
    color: $vk-text;
  }
}

.player-page__crumb {
  overflow: hidden;
  font-size: 12.5px;
  color: #5f6b7d;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 加载骨架 ---------- */
.player-loading__screen {
  width: 100%;
  aspect-ratio: 16 / 9;
  background: linear-gradient(100deg, #0f1621 30%, #182131 50%, #0f1621 70%);
  background-size: 220% 100%;
  border-radius: 14px;
  animation: player-shimmer 1.4s ease-in-out infinite;
}

.player-loading__line {
  width: 42%;
  height: 14px;
  margin-top: 18px;
  background: linear-gradient(100deg, #131b28 30%, #1b2536 50%, #131b28 70%);
  background-size: 220% 100%;
  border-radius: 7px;
  animation: player-shimmer 1.4s ease-in-out infinite;
}

.player-loading__line--short {
  width: 26%;
  height: 10px;
  margin-top: 10px;
}

@keyframes player-shimmer {
  0% {
    background-position: 120% 0;
  }

  100% {
    background-position: -120% 0;
  }
}

/* ---------- 状态页（错误/转码提示） ---------- */
.player-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 76px 20px 82px;
  text-align: center;
}

.player-state__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 58px;
  height: 58px;
  margin-bottom: 12px;
  font-size: 22px;
  color: #7d8ca0;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 16px;
}

.player-state__title {
  font-size: 17px;
  font-weight: 600;
  color: $vk-text;
}

.player-state__desc {
  max-width: 28em;
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: $vk-text-muted;
}

.player-state__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: center;
  gap: 14px;
  margin-top: 20px;
}

.player-state__button {
  padding: 8px 18px;
  font-size: 13px;
  color: $vk-text;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 8px;

  &:hover {
    background: rgba(255, 255, 255, 0.09);
  }
}

.player-state__link {
  font-size: 13px;
  color: $vk-text-muted;

  &:hover {
    color: $vk-text;
  }
}

/* ---------- 播放器 + 侧栏布局 ---------- */
.player-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 296px;
  align-items: start;
  gap: 20px;

  @include vk-tablet {
    grid-template-columns: minmax(0, 1fr);
  }
}

.player-main {
  min-width: 0;
}

.player-info {
  padding: 18px 2px 0;
}

.player-info__title {
  font-size: clamp(18px, 2.6vw, 24px);
  font-weight: 700;
  line-height: 1.35;
  color: #f3f6fb;
  word-break: break-word;
}

.player-info__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 8px;
  margin-top: 10px;
  font-size: 12.5px;
  color: $vk-text-muted;
}

.player-info__tag {
  margin-right: 2px;
  padding: 2px 8px;
  font-size: 11.5px;
  color: #b9c8dd;
  background: rgba(91, 141, 239, 0.12);
  border: 1px solid rgba(91, 141, 239, 0.36);
  border-radius: 999px;
}

.player-info__dot {
  color: rgba(139, 151, 168, 0.55);
}

.player-info__desc {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.75;
  color: #a6b2c3;
  word-break: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  line-clamp: 3;
}

/* ---------- 剧集侧栏 ---------- */
.player-aside {
  padding: 16px 14px 14px;
  background: rgba(255, 255, 255, 0.025);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 12px;

  @include vk-tablet {
    margin-top: 8px;
  }
}

.player-aside__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 4px 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
}

.player-aside__title {
  font-size: 14px;
  font-weight: 600;
  color: $vk-text;
}

.player-aside__count {
  font-size: 12px;
  color: $vk-text-muted;
}

.player-episode-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 520px;
  margin-top: 8px;
  overflow-y: auto;

  @include vk-tablet {
    max-height: 260px;
  }

  @include vk-mobile {
    max-height: 220px;
  }

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.14);
    border-radius: 2px;
  }
}

.player-episode {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 8px;
  transition:
    background-color 0.16s ease,
    border-color 0.16s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.04);
    border-color: rgba(255, 255, 255, 0.08);
  }

  &.is-active {
    background: rgba(91, 141, 239, 0.13);
    border-color: rgba(91, 141, 239, 0.5);
  }
}

.player-episode__no {
  flex-shrink: 0;
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
  color: #93a3ba;
}

.player-episode__title {
  flex: 1;
  overflow: hidden;
  font-size: 12.5px;
  color: $vk-text;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player-episode__duration {
  flex-shrink: 0;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: $vk-text-muted;
}

.player-episode__status {
  flex-shrink: 0;
  padding: 1px 6px;
  font-size: 10.5px;
  border-radius: 999px;

  &.is-processing {
    color: #e6c07a;
    background: rgba(230, 192, 122, 0.1);
    border: 1px solid rgba(230, 192, 122, 0.28);
  }

  &.is-failed {
    color: #f08181;
    background: rgba(240, 129, 129, 0.1);
    border: 1px solid rgba(240, 129, 129, 0.28);
  }
}

.player-aside__detail {
  display: block;
  margin-top: 12px;
  padding: 9px 0;
  font-size: 12.5px;
  text-align: center;
  color: $vk-text-muted;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 8px;
  transition:
    color 0.16s ease,
    border-color 0.16s ease;

  &:hover {
    color: $vk-text;
    border-color: rgba(255, 255, 255, 0.2);
  }
}
</style>
