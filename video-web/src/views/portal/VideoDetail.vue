<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import QIcon from '@/components/QIcon.vue'
import { getPublicVideoDetail } from '@/api/play'
import { isBizError } from '@/api/request'
import { formatDateTime, formatDuration } from '@/utils/format'
import type { TranscodeStatus } from '@/types/transcode'
import type { PublicEpisodeItem, PublicVideoDetail } from '@/types/video'

/**
 * 前台视频详情页（/videos/:id）：
 * 展示封面、标题、简介、分类、播放量、发布时间与剧集列表；
 * 只有转码完成（DONE）的剧集可点击进入播放页，其余显示转码状态。
 * 视觉：Qoder 规范——封面 + 信息双栏卡片（20/600 标题）、q-tag 状态、剧集行列表。
 */

const route = useRoute()
const router = useRouter()

const detail = ref<PublicVideoDetail | null>(null)
const loading = ref(true)
const errorMessage = ref('')

/** 剧集转码状态文案（文档 7.2 状态机；排队与转码中统一按「转码中」展示） */
const TRANSCODE_STATUS_TEXT: Record<TranscodeStatus, string> = {
  NONE: '未开始',
  PENDING: '转码中',
  PROCESSING: '转码中',
  FAILED: '转码失败',
  DONE: '可播放',
}

/** 路由参数 id 转数字；非法值返回 0，按「视频不存在」处理 */
function parseRouteId(value: string | string[]): number {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 0
}

const videoId = computed((): number => parseRouteId(route.params.id))

/** 剧集按集数升序展示（后端已升序返回，这里再兜一层） */
const sortedEpisodes = computed((): PublicEpisodeItem[] => {
  if (detail.value === null) return []
  return [...detail.value.episodes].sort((left, right) => left.episodeNo - right.episodeNo)
})

/** 总时长：各集时长之和，仅用于信息行展示 */
const totalDuration = computed((): number => {
  return sortedEpisodes.value.reduce((sum, episode) => sum + (episode.duration ?? 0), 0)
})

/** 第一个可播放剧集：用于「开始播放」主按钮 */
const firstPlayableEpisode = computed((): PublicEpisodeItem | null => {
  return sortedEpisodes.value.find((episode) => episode.transcodeStatus === 'DONE') ?? null
})

/** 可播放剧集数量（仅用于页头与信息区提示，不影响交互） */
const playableCount = computed((): number => {
  return sortedEpisodes.value.filter((episode) => episode.transcodeStatus === 'DONE').length
})

const categoryName = computed((): string => detail.value?.categoryName ?? '未分类')

async function loadDetail(): Promise<void> {
  const id = videoId.value
  if (id <= 0) {
    loading.value = false
    errorMessage.value = '视频不存在或已下架'
    return
  }

  loading.value = true
  errorMessage.value = ''
  detail.value = null

  try {
    detail.value = await getPublicVideoDetail(id)
  } catch (error) {
    errorMessage.value = isBizError(error) ? error.message : '视频详情加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

/** 剧集标题：无标题时用「第 N 集」兜底 */
function episodeTitle(episode: PublicEpisodeItem): string {
  return episode.title !== null && episode.title.length > 0 ? episode.title : `第 ${episode.episodeNo} 集`
}

/** 剧集序号：两位数等宽展示（01、02……），避免与标题兜底重复 */
function episodeIndex(episode: PublicEpisodeItem): string {
  return String(episode.episodeNo).padStart(2, '0')
}

/** 剧集状态标签文案 */
function statusText(status: TranscodeStatus): string {
  return TRANSCODE_STATUS_TEXT[status]
}

/** 剧集状态标签语义色（Qoder q-tag）：完成=绿、失败=红、未开始=蓝、其余=橙 */
function statusTagClass(status: TranscodeStatus): string {
  if (status === 'DONE') return 'q-tag--success'
  if (status === 'FAILED') return 'q-tag--danger'
  if (status === 'NONE') return 'q-tag--info'
  return 'q-tag--warning'
}

/** 仅转码完成的剧集可播放（PENDING/PROCESSING 需等待，FAILED 需管理员重试） */
function isPlayable(episode: PublicEpisodeItem): boolean {
  return episode.transcodeStatus === 'DONE'
}

function playEpisode(episode: PublicEpisodeItem): void {
  if (!isPlayable(episode)) return
  void router.push(`/play/${videoId.value}/${episode.id}`)
}

function startPlay(): void {
  const first = firstPlayableEpisode.value
  if (first === null) return
  void router.push(`/play/${videoId.value}/${first.id}`)
}

watch(videoId, () => {
  void loadDetail()
}, { immediate: true })
</script>

<template>
  <div class="q-page q-fade-in detail">
    <header class="q-page-head">
      <div class="q-page-head__main">
        <RouterLink class="q-btn q-btn--ghost q-btn--sm detail__back" to="/">
          <QIcon name="arrow-left" :size="14" />
          返回首页
        </RouterLink>
      </div>
      <div v-if="!loading && detail !== null" class="q-page-actions">
        <span class="q-caption">{{ sortedEpisodes.length }} 集 · {{ playableCount }} 集可播放</span>
      </div>
    </header>

    <!-- 加载态：封面 + 信息骨架 -->
    <section v-if="loading" class="q-card q-card--pad detail-loading" aria-busy="true" aria-label="视频详情加载中">
      <div class="detail-loading__hero">
        <div class="q-skeleton detail-loading__cover" />
        <div class="detail-loading__info">
          <div class="q-skeleton detail-loading__line detail-loading__line--tag" />
          <div class="q-skeleton detail-loading__line detail-loading__line--title" />
          <div class="q-skeleton detail-loading__line" />
          <div class="q-skeleton detail-loading__line detail-loading__line--short" />
          <div class="q-skeleton detail-loading__button" />
        </div>
      </div>
    </section>

    <!-- 错误态 -->
    <section v-else-if="errorMessage !== '' || detail === null" class="q-card q-card--pad detail-state" role="alert">
      <div class="q-empty">
        <div class="q-empty__stack">
          <span class="q-empty__face"><QIcon name="alert" :size="18" /></span>
        </div>
        <p class="q-empty__title">无法加载视频</p>
        <p class="q-empty__desc">{{ errorMessage !== '' ? errorMessage : '视频不存在或已下架' }}</p>
        <div class="q-empty__actions">
          <button type="button" class="q-btn q-btn--outline q-btn--sm" @click="loadDetail">
            <QIcon name="refresh" :size="13" />
            重新加载
          </button>
          <RouterLink class="q-btn q-btn--ghost q-btn--sm" to="/">返回首页</RouterLink>
        </div>
      </div>
    </section>

    <template v-else>
      <!-- 封面 + 信息 -->
      <section class="q-card detail-hero">
        <div class="detail-hero__cover">
          <img
            v-if="detail.coverUrl !== null && detail.coverUrl !== ''"
            class="detail-hero__img"
            :src="detail.coverUrl"
            :alt="detail.title"
          />
          <div v-else class="detail-hero__placeholder" aria-hidden="true">
            <QIcon name="film" :size="32" />
          </div>
        </div>

        <div class="detail-hero__body">
          <div class="detail-hero__tags">
            <span class="q-tag">
              <QIcon name="tag" :size="12" />
              {{ categoryName }}
            </span>
            <span class="q-tag">{{ sortedEpisodes.length }} 集</span>
          </div>

          <h1 class="detail-hero__title">{{ detail.title }}</h1>

          <p class="detail-hero__meta">
            <span>{{ detail.viewCount.toLocaleString('zh-CN') }} 次播放</span>
            <span class="detail-hero__dot" aria-hidden="true">·</span>
            <span>发布于 {{ formatDateTime(detail.createdAt, 'YYYY-MM-DD') }}</span>
            <template v-if="totalDuration > 0">
              <span class="detail-hero__dot" aria-hidden="true">·</span>
              <span>总时长 {{ formatDuration(totalDuration) }}</span>
            </template>
          </p>

          <p class="detail-hero__desc">
            {{ detail.description !== null && detail.description.length > 0 ? detail.description : '暂无简介' }}
          </p>

          <div class="detail-hero__actions">
            <button
              type="button"
              class="q-btn q-btn--primary q-btn--lg"
              :disabled="firstPlayableEpisode === null"
              @click="startPlay"
            >
              <QIcon name="play" :size="16" />
              {{ firstPlayableEpisode === null ? '暂无可播放剧集' : '开始播放' }}
            </button>
            <button type="button" class="q-btn q-btn--ghost" @click="loadDetail">
              <QIcon name="refresh" :size="14" />
              刷新状态
            </button>
          </div>
        </div>
      </section>

      <!-- 剧集列表 -->
      <section class="q-card q-card--pad detail-episodes">
        <header class="detail-episodes__head">
          <h2 class="q-section-title">剧集</h2>
          <span class="q-caption">{{ sortedEpisodes.length }} 集</span>
        </header>

        <div v-if="sortedEpisodes.length === 0" class="q-empty">
          <div class="q-empty__stack">
            <span class="q-empty__face"><QIcon name="film" :size="18" /></span>
          </div>
          <p class="q-empty__title">剧集尚未上传</p>
          <p class="q-empty__desc">管理员上传并转码完成后，剧集会自动出现在这里。</p>
        </div>

        <div v-else class="episode-list">
          <button
            v-for="episode in sortedEpisodes"
            :key="episode.id"
            type="button"
            class="episode-row"
            :disabled="!isPlayable(episode)"
            :aria-label="`${isPlayable(episode) ? '播放' : '暂不可播放'}：第 ${episode.episodeNo} 集 ${episodeTitle(episode)}`"
            @click="playEpisode(episode)"
          >
            <span class="episode-row__no q-mono">{{ episodeIndex(episode) }}</span>
            <span class="episode-row__title" :title="episodeTitle(episode)">{{ episodeTitle(episode) }}</span>
            <span v-if="episode.duration !== null && episode.duration > 0" class="episode-row__duration">
              {{ formatDuration(episode.duration) }}
            </span>
            <span class="q-tag" :class="statusTagClass(episode.transcodeStatus)">
              {{ statusText(episode.transcodeStatus) }}
            </span>
            <QIcon v-if="isPlayable(episode)" name="chevron-right" :size="14" class="episode-row__chevron" />
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.detail {
  padding-top: var(--q-sp-4);
}

/* ghost 按钮自带 8px 内边距，负左边距让文字对齐内容左缘 */
.detail__back {
  margin-left: -8px;
}

/* ---------- 加载骨架 ---------- */
.detail-loading {
  margin-top: var(--q-sp-4);
}

.detail-loading__hero {
  display: grid;
  grid-template-columns: minmax(0, 380px) minmax(0, 1fr);
  gap: var(--q-sp-6);

  @include q-tablet {
    grid-template-columns: minmax(0, 300px) minmax(0, 1fr);
  }

  @include q-mobile {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--q-sp-4);
  }
}

.detail-loading__cover {
  aspect-ratio: 16 / 9;
  border-radius: var(--q-r-xl);
}

.detail-loading__info {
  display: flex;
  flex-direction: column;
  gap: var(--q-sp-3);
  padding-top: var(--q-sp-1);
}

.detail-loading__line {
  height: 10px;
  border-radius: var(--q-r-sm);
}

.detail-loading__line--tag {
  width: 34%;
  height: 18px;
}

.detail-loading__line--title {
  width: 62%;
  height: 22px;
}

.detail-loading__line--short {
  width: 40%;
}

.detail-loading__button {
  width: 132px;
  height: var(--q-ctl-lg);
  margin-top: var(--q-sp-2);
  border-radius: var(--q-r-control);
}

/* ---------- 错误态 ---------- */
.detail-state {
  margin-top: var(--q-sp-4);
}

/* ---------- 封面 + 信息 ---------- */
.detail-hero {
  display: grid;
  grid-template-columns: minmax(0, 380px) minmax(0, 1fr);
  gap: var(--q-sp-6);
  padding: var(--q-sp-5);
  margin-top: var(--q-sp-4);

  @include q-tablet {
    grid-template-columns: minmax(0, 300px) minmax(0, 1fr);
    gap: var(--q-sp-5);
  }

  @include q-mobile {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--q-sp-4);
    padding: var(--q-sp-4);
  }
}

.detail-hero__cover {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: var(--q-fill-tertiary);
  border-radius: var(--q-r-xl);
  box-shadow: 0 0 0 1px var(--q-border-quaternary) inset;
}

.detail-hero__img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail-hero__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--q-text-quaternary);
  background: var(--q-bg-elevated);
}

.detail-hero__body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
}

.detail-hero__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.detail-hero__title {
  margin-top: 10px;
  font-size: var(--q-fs-20);
  font-weight: 600;
  line-height: var(--q-lh-26);
  color: var(--q-text);
  letter-spacing: -0.02em;
  word-break: break-word;
}

.detail-hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-top: var(--q-sp-2);
  font-size: var(--q-fs-12);
  line-height: var(--q-lh-18);
  color: var(--q-text-tertiary);
}

.detail-hero__dot {
  color: var(--q-text-quaternary);
}

.detail-hero__desc {
  display: -webkit-box;
  overflow: hidden;
  margin-top: var(--q-sp-3);
  font-size: var(--q-fs-13);
  line-height: var(--q-lh-20);
  color: var(--q-text-secondary);
  word-break: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 5;
  line-clamp: 5;
}

.detail-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--q-sp-2);
  align-items: center;
  margin-top: auto;
  padding-top: var(--q-sp-5);
}

/* ---------- 剧集列表 ---------- */
.detail-episodes {
  margin-top: var(--q-sp-4);
}

.detail-episodes__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--q-border-quaternary);
}

.episode-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: var(--q-sp-2);
}

.episode-row {
  display: flex;
  gap: var(--q-sp-3);
  align-items: center;
  width: 100%;
  min-height: 40px;
  padding: 6px 10px;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: var(--q-r-lg);
  transition: background-color var(--q-dur-fast) var(--q-ease);

  &:not(:disabled):hover {
    background: var(--q-fill-tertiary);
  }

  &:not(:disabled):focus-visible {
    outline: none;
    box-shadow: 0 0 0 2px var(--q-primary-border);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: var(--q-disabled-opacity);
  }
}

.episode-row__no {
  flex: none;
  width: 20px;
  font-size: var(--q-fs-12);
  line-height: var(--q-lh-16);
  color: var(--q-text-tertiary);
  text-align: left;
}

.episode-row__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--q-fs-13);
  font-weight: 500;
  line-height: var(--q-lh-18);
  color: var(--q-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.episode-row__duration {
  flex: none;
  font-size: var(--q-fs-11);
  font-variant-numeric: tabular-nums;
  line-height: var(--q-lh-16);
  color: var(--q-text-tertiary);
}

.episode-row__chevron {
  flex: none;
  color: var(--q-text-quaternary);
}

@include q-mobile {
  .detail {
    padding-top: var(--q-sp-2);
  }

  .detail-hero__title {
    font-size: var(--q-fs-18);
    line-height: var(--q-lh-24);
  }

  .detail-hero__actions {
    padding-top: var(--q-sp-4);
  }

  .episode-row {
    gap: var(--q-sp-2);
  }
}
</style>
