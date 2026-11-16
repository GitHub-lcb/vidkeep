<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPublicVideoDetail } from '@/api/play'
import { isBizError } from '@/api/request'
import { formatDateTime, formatDuration } from '@/utils/format'
import type { TranscodeStatus } from '@/types/transcode'
import type { PublicEpisodeItem, PublicVideoDetail } from '@/types/video'

/**
 * 前台视频详情页（/videos/:id）：
 * 展示封面、标题、简介、分类、播放量、发布时间与剧集列表；
 * 只有转码完成（DONE）的剧集可点击进入播放页，其余显示转码状态。
 */

const route = useRoute()
const router = useRouter()

const detail = ref<PublicVideoDetail | null>(null)
const loading = ref(true)
const errorMessage = ref('')

/** 剧集转码状态文案（文档 7.2 状态机） */
const TRANSCODE_STATUS_TEXT: Record<TranscodeStatus, string> = {
  NONE: '待转码',
  PENDING: '排队中',
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

/** 剧集状态标签文案 */
function statusText(status: TranscodeStatus): string {
  return TRANSCODE_STATUS_TEXT[status]
}

/** 仅转码完成的剧集可播放（PENDING/PROCESSING 需等待，FAILED 需管理员重试） */
function isPlayable(episode: PublicEpisodeItem): boolean {
  return episode.transcodeStatus === 'DONE'
}

/** 状态标签样式：转码中/失败用不同色调提示 */
function statusClass(status: TranscodeStatus): string {
  if (status === 'DONE') return 'is-done'
  if (status === 'FAILED') return 'is-failed'
  return 'is-processing'
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
  <div class="detail">
    <div class="detail-nav">
      <RouterLink class="detail-back" to="/">← 返回首页</RouterLink>
    </div>

    <!-- 加载态 -->
    <section v-if="loading" class="detail-loading" aria-busy="true" aria-label="视频详情加载中">
      <div class="detail-loading__hero">
        <div class="detail-loading__cover" />
        <div class="detail-loading__info">
          <div class="detail-loading__line detail-loading__line--title" />
          <div class="detail-loading__line" />
          <div class="detail-loading__line detail-loading__line--short" />
        </div>
      </div>
    </section>

    <!-- 错误态 -->
    <section v-else-if="errorMessage !== '' || detail === null" class="detail-state">
      <p class="detail-state__icon" aria-hidden="true">!</p>
      <h1 class="detail-state__title">无法加载视频</h1>
      <p class="detail-state__desc">{{ errorMessage !== '' ? errorMessage : '视频不存在或已下架' }}</p>
      <div class="detail-state__actions">
        <button type="button" class="detail-state__button" @click="loadDetail">重新加载</button>
        <RouterLink class="detail-state__link" to="/">返回首页</RouterLink>
      </div>
    </section>

    <template v-else>
      <section class="detail-hero">
        <div class="detail-cover">
          <img
            v-if="detail.coverUrl !== null && detail.coverUrl !== ''"
            class="detail-cover__img"
            :src="detail.coverUrl"
            :alt="detail.title"
          />
          <div v-else class="detail-cover__placeholder" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="40" height="40">
              <path d="M9 7l8 5-8 5z" fill="currentColor" />
            </svg>
          </div>
        </div>

        <div class="detail-info">
          <div class="detail-info__top">
            <span class="detail-category">{{ categoryName }}</span>
            <span class="detail-count">共 {{ sortedEpisodes.length }} 集</span>
          </div>

          <h1 class="detail-title">{{ detail.title }}</h1>

          <p class="detail-meta">
            <span>{{ detail.viewCount }} 次播放</span>
            <span class="detail-meta__dot" aria-hidden="true">·</span>
            <span>发布于 {{ formatDateTime(detail.createdAt, 'YYYY-MM-DD') }}</span>
            <template v-if="totalDuration > 0">
              <span class="detail-meta__dot" aria-hidden="true">·</span>
              <span>总时长 {{ formatDuration(totalDuration) }}</span>
            </template>
          </p>

          <p class="detail-desc">{{ detail.description !== null && detail.description.length > 0 ? detail.description : '暂无简介' }}</p>

          <button
            type="button"
            class="detail-play"
            :disabled="firstPlayableEpisode === null"
            @click="startPlay"
          >
            <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
              <path d="M9 7l8 5-8 5z" fill="currentColor" />
            </svg>
            {{ firstPlayableEpisode === null ? '暂无可播放剧集' : '开始播放' }}
          </button>
        </div>
      </section>

      <section class="detail-episodes">
        <div class="detail-episodes__header">
          <h2 class="detail-episodes__title">剧集列表</h2>
          <span class="detail-episodes__count">{{ sortedEpisodes.length }} 集</span>
        </div>

        <div v-if="sortedEpisodes.length === 0" class="detail-episodes__empty">剧集尚未上传</div>

        <div v-else class="detail-episode-grid">
          <button
            v-for="episode in sortedEpisodes"
            :key="episode.id"
            type="button"
            class="episode-card"
            :class="{ 'is-playable': isPlayable(episode), 'is-disabled': !isPlayable(episode) }"
            :disabled="!isPlayable(episode)"
            @click="playEpisode(episode)"
          >
            <span class="episode-card__no">第 {{ episode.episodeNo }} 集</span>
            <span class="episode-card__title" :title="episodeTitle(episode)">{{ episodeTitle(episode) }}</span>
            <span class="episode-card__footer">
              <span
                v-if="episode.transcodeStatus !== 'DONE'"
                class="episode-card__status"
                :class="statusClass(episode.transcodeStatus)"
              >
                {{ statusText(episode.transcodeStatus) }}
              </span>
              <template v-else>
                <span v-if="episode.duration !== null && episode.duration > 0" class="episode-card__duration">
                  {{ formatDuration(episode.duration) }}
                </span>
                <span v-else class="episode-card__duration">可播放</span>
              </template>
              <svg
                v-if="isPlayable(episode)"
                class="episode-card__play-icon"
                viewBox="0 0 24 24"
                width="12"
                height="12"
                aria-hidden="true"
              >
                <path d="M9 7l8 5-8 5z" fill="currentColor" />
              </svg>
            </span>
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.detail {
  max-width: 1120px;
  margin: 0 auto;
  padding: 20px 24px 72px;

  @include vk-mobile {
    padding: 14px 16px 48px;
  }
}

.detail-nav {
  padding: 6px 0 18px;
}

.detail-back {
  font-size: 13px;
  color: $vk-text-muted;
  transition: color 0.18s ease;

  &:hover {
    color: $vk-text;
  }
}

/* ---------- 加载骨架 ---------- */
.detail-loading__hero {
  display: flex;
  gap: 28px;

  @include vk-mobile {
    flex-direction: column;
    gap: 18px;
  }
}

.detail-loading__cover {
  flex-shrink: 0;
  width: 380px;
  aspect-ratio: 16 / 9;
  background: linear-gradient(100deg, #131b28 30%, #1b2536 50%, #131b28 70%);
  background-size: 220% 100%;
  border-radius: 14px;
  animation: detail-shimmer 1.4s ease-in-out infinite;

  @include vk-tablet {
    width: 320px;
  }

  @include vk-mobile {
    width: 100%;
  }
}

.detail-loading__info {
  flex: 1;
  padding-top: 6px;
}

.detail-loading__line {
  height: 12px;
  margin-bottom: 14px;
  background: linear-gradient(100deg, #131b28 30%, #1b2536 50%, #131b28 70%);
  background-size: 220% 100%;
  border-radius: 6px;
  animation: detail-shimmer 1.4s ease-in-out infinite;
}

.detail-loading__line--title {
  width: 52%;
  height: 22px;
}

.detail-loading__line--short {
  width: 34%;
}

@keyframes detail-shimmer {
  0% {
    background-position: 120% 0;
  }

  100% {
    background-position: -120% 0;
  }
}

/* ---------- 错误态 ---------- */
.detail-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 20px 90px;
  text-align: center;
}

.detail-state__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: 10px;
  font-size: 22px;
  color: #7d8ca0;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 16px;
}

.detail-state__title {
  font-size: 17px;
  font-weight: 600;
  color: $vk-text;
}

.detail-state__desc {
  max-width: 26em;
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: $vk-text-muted;
}

.detail-state__actions {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 18px;
}

.detail-state__button {
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

.detail-state__link {
  font-size: 13px;
  color: $vk-text-muted;

  &:hover {
    color: $vk-text;
  }
}

/* ---------- 详情头部 ---------- */
.detail-hero {
  display: flex;
  gap: 30px;
  padding: 20px 0 34px;

  @include vk-tablet {
    gap: 22px;
  }

  @include vk-mobile {
    flex-direction: column;
    gap: 18px;
    padding-bottom: 26px;
  }
}

.detail-cover {
  flex-shrink: 0;
  width: 380px;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: $vk-ink-700;
  border: 1px solid $vk-line;
  border-radius: 14px;

  @include vk-tablet {
    width: 320px;
  }

  @include vk-mobile {
    width: 100%;
  }
}

.detail-cover__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail-cover__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: rgba(232, 237, 245, 0.34);
  background:
    radial-gradient(120% 90% at 30% 0%, rgba(91, 141, 239, 0.24), transparent 62%),
    linear-gradient(160deg, #161f2f 0%, #0d131e 100%);
}

.detail-info {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.detail-info__top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-category {
  padding: 3px 10px;
  font-size: 11.5px;
  color: #b9c8dd;
  background: rgba(91, 141, 239, 0.12);
  border: 1px solid rgba(91, 141, 239, 0.36);
  border-radius: 999px;
}

.detail-count {
  font-size: 12px;
  color: $vk-text-muted;
}

.detail-title {
  margin-top: 12px;
  font-size: clamp(22px, 3.4vw, 32px);
  font-weight: 700;
  line-height: 1.3;
  color: #f3f6fb;
  word-break: break-word;
}

.detail-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 10px;
  font-size: 12.5px;
  color: $vk-text-muted;
}

.detail-meta__dot {
  color: rgba(139, 151, 168, 0.55);
}

.detail-desc {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 14px;
  font-size: 13px;
  line-height: 1.8;
  color: #a6b2c3;
  word-break: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 5;
  line-clamp: 5;
}

.detail-play {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  gap: 8px;
  height: 40px;
  margin-top: 22px;
  padding: 0 22px;
  font-size: 14px;
  font-weight: 600;
  color: #ffffff;
  cursor: pointer;
  background: linear-gradient(180deg, #5f8ff2, #4a76d6);
  border: none;
  border-radius: 9px;
  box-shadow: 0 10px 24px rgba(74, 118, 214, 0.28);
  transition: background 0.18s ease;

  &:hover:not(:disabled) {
    background: linear-gradient(180deg, #6d9af5, #4f7cdd);
  }

  &:disabled {
    color: #97a3b4;
    cursor: not-allowed;
    background: rgba(255, 255, 255, 0.06);
    box-shadow: none;
  }
}

/* ---------- 剧集列表 ---------- */
.detail-episodes {
  padding-top: 26px;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
}

.detail-episodes__header {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.detail-episodes__title {
  font-size: 16px;
  font-weight: 600;
  color: $vk-text;
}

.detail-episodes__count {
  font-size: 12px;
  color: $vk-text-muted;
}

.detail-episodes__empty {
  padding: 40px 0;
  font-size: 13px;
  text-align: center;
  color: $vk-text-muted;
}

.detail-episode-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(158px, 1fr));
  gap: 12px;
  margin-top: 16px;

  @include vk-mobile {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }
}

.episode-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 13px;
  text-align: left;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.025);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    transform 0.18s ease;

  &.is-playable:hover,
  &.is-playable:focus-visible {
    transform: translateY(-2px);
    background: rgba(91, 141, 239, 0.09);
    border-color: rgba(91, 141, 239, 0.5);
    outline: none;
  }

  &.is-disabled {
    cursor: not-allowed;
    opacity: 0.72;
  }
}

.episode-card__no {
  font-size: 11.5px;
  letter-spacing: 0.02em;
  color: #93a3ba;
}

.episode-card__title {
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 600;
  color: $vk-text;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.episode-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 2px;
}

.episode-card__duration {
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
  color: $vk-text-muted;
}

.episode-card__status {
  padding: 1px 7px;
  font-size: 11px;
  border-radius: 999px;

  &.is-processing {
    color: #e6c07a;
    background: rgba(230, 192, 122, 0.10);
    border: 1px solid rgba(230, 192, 122, 0.28);
  }

  &.is-failed {
    color: #f08181;
    background: rgba(240, 129, 129, 0.10);
    border: 1px solid rgba(240, 129, 129, 0.28);
  }

  &.is-done {
    color: #7fd3a0;
    background: rgba(127, 211, 160, 0.10);
    border: 1px solid rgba(127, 211, 160, 0.28);
  }
}

.episode-card__play-icon {
  flex-shrink: 0;
  color: $vk-accent;
}
</style>
