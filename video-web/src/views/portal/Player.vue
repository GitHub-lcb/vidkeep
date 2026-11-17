<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import QIcon from '@/components/QIcon.vue'
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
 * 视觉：Qoder 规范——深色播放舞台（16:9、圆角 12）+ 右侧剧集侧栏卡片，当前集主色浅底高亮。
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

/** 剧集序号：两位数等宽展示（01、02……） */
function episodeIndex(episode: PublicEpisodeItem): string {
  return String(episode.episodeNo).padStart(2, '0')
}

/** 剧集状态标签语义色（q-tag）：失败红、其余橙 */
function statusTagClass(status: TranscodeStatus): string {
  if (status === 'FAILED') return 'q-tag--danger'
  return 'q-tag--warning'
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
  <div class="q-page q-fade-in player-page">
    <header class="q-page-head">
      <div class="q-page-head__main">
        <RouterLink class="q-btn q-btn--ghost q-btn--sm player-page__back" :to="`/videos/${videoId}`">
          <QIcon name="arrow-left" :size="14" />
          返回详情
        </RouterLink>
        <h1 class="q-page-title player-page__title">{{ detail !== null ? detail.title : '播放' }}</h1>
        <p class="q-page-desc">
          <template v-if="currentEpisode !== null">
            第 {{ currentEpisode.episodeNo }} 集 · {{ currentEpisodeTitle }}
          </template>
          <template v-else>正在准备播放信息</template>
        </p>
      </div>
      <div class="q-page-actions">
        <RouterLink class="q-btn q-btn--ghost q-btn--sm" to="/">
          <QIcon name="home" :size="14" />
          返回首页
        </RouterLink>
      </div>
    </header>

    <!-- 加载态 -->
    <section v-if="loading" class="q-card q-card--pad player-loading" aria-busy="true" aria-label="播放信息加载中">
      <div class="q-skeleton player-loading__screen" />
      <div class="q-skeleton player-loading__line" />
      <div class="q-skeleton player-loading__line player-loading__line--short" />
    </section>

    <!-- 错误态（含播放地址过期 2005） -->
    <section v-else-if="errorMessage !== ''" class="q-card q-card--pad player-state" role="alert">
      <div class="q-empty">
        <div class="q-empty__stack">
          <span class="q-empty__face"><QIcon name="alert" :size="18" /></span>
        </div>
        <p class="q-empty__title">{{ playUrlExpired ? '播放地址已过期' : '无法播放' }}</p>
        <p class="q-empty__desc">{{ errorMessage }}</p>
        <div class="q-empty__actions">
          <button type="button" class="q-btn q-btn--primary" @click="load">
            <QIcon name="refresh" :size="14" />
            {{ playUrlExpired ? '重新获取播放地址' : '重新加载' }}
          </button>
          <RouterLink class="q-btn q-btn--ghost q-btn--sm" :to="`/videos/${videoId}`">返回详情</RouterLink>
          <RouterLink class="q-btn q-btn--ghost q-btn--sm" to="/">返回首页</RouterLink>
        </div>
      </div>
    </section>

    <!-- 转码未完成：显示提示而不是播放器 -->
    <section v-else-if="showTranscodeNotice" class="q-card q-card--pad player-state">
      <div class="q-empty">
        <div class="q-empty__stack">
          <span class="q-empty__face"><QIcon name="clock" :size="18" /></span>
        </div>
        <p class="q-empty__title">{{ transcodeStatusText }}</p>
        <p class="q-empty__desc">{{ transcodeNoticeText }}</p>
        <div class="q-empty__actions">
          <button type="button" class="q-btn q-btn--primary" @click="load">
            <QIcon name="refresh" :size="14" />
            刷新状态
          </button>
          <RouterLink class="q-btn q-btn--ghost q-btn--sm" :to="`/videos/${videoId}`">返回详情</RouterLink>
        </div>
      </div>
    </section>

    <!-- 播放器 + 剧集侧栏 -->
    <div v-else-if="playInfo !== null" class="player-layout">
      <section class="player-main">
        <div class="player-stage">
          <HlsPlayer
            :key="playInfo.url"
            :url="playInfo.url"
            :poster="playInfo.poster"
            :start-position="startPosition"
            @timeupdate="handleTimeUpdate"
            @ended="handleEnded"
          />
        </div>

        <div class="player-info">
          <p class="player-info__meta">
            <span v-if="detail !== null && detail.categoryName !== null" class="q-tag q-tag--primary">
              {{ detail.categoryName }}
            </span>
            <span>{{ currentEpisodeTitle }}</span>
            <template v-if="currentEpisode !== null && currentEpisode.duration !== null && currentEpisode.duration > 0">
              <span class="player-info__dot" aria-hidden="true">·</span>
              <span>{{ formatDuration(currentEpisode.duration) }}</span>
            </template>
            <template v-if="detail !== null">
              <span class="player-info__dot" aria-hidden="true">·</span>
              <span>共 {{ sortedEpisodes.length }} 集</span>
            </template>
            <span class="player-info__dot" aria-hidden="true">·</span>
            <span>{{ detail?.viewCount ?? 0 }} 次播放</span>
          </p>
          <p v-if="detail !== null && detail.description !== null && detail.description.length > 0" class="player-info__desc">
            {{ detail.description }}
          </p>
        </div>
      </section>

      <aside class="q-card q-card--pad player-aside">
        <header class="player-aside__head">
          <h2 class="player-aside__title">剧集</h2>
          <span class="q-caption">{{ sortedEpisodes.length }} 集</span>
        </header>

        <div class="player-episode-list">
          <button
            v-for="episode in sortedEpisodes"
            :key="episode.id"
            type="button"
            class="player-episode"
            :class="{ 'is-active': episode.id === episodeId }"
            :aria-current="episode.id === episodeId ? 'page' : undefined"
            :aria-label="`第 ${episode.episodeNo} 集 ${episodeTitle(episode)}`"
            @click="switchEpisode(episode)"
          >
            <span class="player-episode__no q-mono">{{ episodeIndex(episode) }}</span>
            <span class="player-episode__title" :title="episodeTitle(episode)">{{ episodeTitle(episode) }}</span>
            <span
              v-if="episode.transcodeStatus !== 'DONE'"
              class="q-tag"
              :class="statusTagClass(episode.transcodeStatus)"
            >
              {{ TRANSCODE_STATUS_TEXT[episode.transcodeStatus] }}
            </span>
            <span v-else-if="episode.duration !== null && episode.duration > 0" class="player-episode__duration">
              {{ formatDuration(episode.duration) }}
            </span>
          </button>
        </div>

        <RouterLink class="q-btn q-btn--outline q-btn--sm q-btn--block player-aside__detail" :to="`/videos/${videoId}`">
          返回详情页
        </RouterLink>
      </aside>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.player-page {
  padding-top: var(--q-sp-4);
}

/* ghost 按钮自带 8px 内边距，负左边距让文字对齐内容左缘 */
.player-page__back {
  margin-left: -8px;
}

.player-page__title {
  margin-top: 6px;
  word-break: break-word;
}

/* ---------- 加载骨架 ---------- */
.player-loading {
  margin-top: var(--q-sp-4);
}

.player-loading__screen {
  width: 100%;
  aspect-ratio: 16 / 9;
  background: var(--q-player-bg);
  border-radius: var(--q-r-xl);
}

.player-loading__line {
  width: 42%;
  height: 12px;
  margin-top: var(--q-sp-4);
  border-radius: var(--q-r-sm);
}

.player-loading__line--short {
  width: 26%;
  height: 10px;
  margin-top: var(--q-sp-2);
}

/* ---------- 状态块（错误 / 转码提示） ---------- */
.player-state {
  margin-top: var(--q-sp-4);
}

/* ---------- 播放区 + 剧集侧栏 ---------- */
.player-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: var(--q-sp-5);
  align-items: start;
  margin-top: var(--q-sp-4);

  @include q-tablet {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--q-sp-4);
  }
}

.player-main {
  min-width: 0;
}

.player-stage {
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: var(--q-player-bg);
  border-radius: var(--q-r-xl);
}

.player-info {
  padding-top: var(--q-sp-4);
}

.player-info__title {
  font-size: var(--q-fs-16);
  font-weight: 600;
  line-height: var(--q-lh-24);
  color: var(--q-text);
  letter-spacing: -0.01em;
  word-break: break-word;
}

.player-info__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 8px;
  align-items: center;
  margin-top: var(--q-sp-2);
  font-size: var(--q-fs-12);
  line-height: var(--q-lh-18);
  color: var(--q-text-tertiary);
}

.player-info__dot {
  color: var(--q-text-quaternary);
}

.player-info__desc {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 10px;
  font-size: var(--q-fs-13);
  line-height: var(--q-lh-20);
  color: var(--q-text-secondary);
  word-break: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  line-clamp: 3;
}

/* ---------- 剧集侧栏 ---------- */
.player-aside {
  display: flex;
  flex-direction: column;
}

.player-aside__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--q-border-quaternary);
}

.player-aside__title {
  font-size: var(--q-fs-13);
  font-weight: 600;
  line-height: var(--q-lh-18);
  color: var(--q-text);
}

.player-episode-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 480px;
  margin-top: var(--q-sp-2);
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--q-border-secondary) transparent;

  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background: var(--q-border-secondary);
    border-radius: var(--q-r-full);
  }

  @include q-tablet {
    max-height: 300px;
  }

  @include q-mobile {
    max-height: 260px;
  }
}

.player-episode {
  display: flex;
  gap: var(--q-sp-2);
  align-items: center;
  width: 100%;
  min-height: 34px;
  padding: 6px 8px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: var(--q-r-lg);
  transition: background-color var(--q-dur-fast) var(--q-ease), color var(--q-dur-fast) var(--q-ease);

  &:hover {
    background: var(--q-fill-tertiary);
  }

  &:focus-visible {
    outline: none;
    box-shadow: 0 0 0 2px var(--q-primary-border);
  }

  &.is-active {
    background: var(--q-primary-bg);

    .player-episode__no,
    .player-episode__title {
      color: var(--q-primary-text);
    }
  }
}

.player-episode__no {
  flex: none;
  width: 18px;
  font-size: var(--q-fs-12);
  line-height: var(--q-lh-16);
  color: var(--q-text-tertiary);
}

.player-episode__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--q-fs-13);
  line-height: var(--q-lh-18);
  color: var(--q-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player-episode__duration {
  flex: none;
  font-size: var(--q-fs-11);
  font-variant-numeric: tabular-nums;
  line-height: var(--q-lh-16);
  color: var(--q-text-tertiary);
}

.player-aside__detail {
  margin-top: var(--q-sp-3);
}

@include q-mobile {
  .player-page {
    padding-top: var(--q-sp-2);
  }

  .player-info__title {
    font-size: var(--q-fs-14);
    line-height: var(--q-lh-22);
  }
}
</style>
