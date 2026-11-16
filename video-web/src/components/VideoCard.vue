<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { formatDuration } from '@/utils/format'
import type { PublicVideoItem } from '@/types/video'

/**
 * 前台视频卡片：封面 + 标题 + 时长/集数/播放量。
 * 无封面时用深色渐变占位；整卡可点击进入详情页。
 */

const props = defineProps<{
  video: PublicVideoItem
}>()

const router = useRouter()
/** 封面加载失败时回退到渐变占位，避免出现破图 */
const coverFailed = ref(false)

const hasCover = computed(() => props.video.coverUrl !== null && props.video.coverUrl !== '' && !coverFailed.value)

/** 播放量文案：过万折算为「万」，过亿折算为「亿」，避免长数字撑破卡片 */
const viewCountText = computed((): string => {
  const count = props.video.viewCount
  if (!Number.isFinite(count) || count <= 0) return '0 播放'
  if (count >= 100_000_000) return `${(count / 100_000_000).toFixed(1)} 亿播放`
  if (count >= 10_000) return `${(count / 10_000).toFixed(1)} 万播放`
  return `${count} 播放`
})

/** 集数文案：单集作品显示「单集」，多集显示「共 N 集」 */
const episodeCountText = computed((): string => {
  const count = props.video.episodeCount
  if (!Number.isFinite(count) || count <= 1) return '单集'
  return `共 ${count} 集`
})

function goDetail(): void {
  void router.push(`/videos/${props.video.id}`)
}
</script>

<template>
  <article
    class="video-card"
    role="link"
    tabindex="0"
    :aria-label="`查看视频：${video.title}`"
    @click="goDetail"
    @keydown.enter="goDetail"
    @keydown.space.prevent="goDetail"
  >
    <div class="video-card__cover">
      <img
        v-if="hasCover"
        class="video-card__img"
        :src="video.coverUrl ?? ''"
        :alt="video.title"
        loading="lazy"
        @error="coverFailed = true"
      />
      <div v-else class="video-card__placeholder" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="30" height="30">
          <path d="M9 7l8 5-8 5z" fill="currentColor" />
        </svg>
      </div>

      <span v-if="video.duration !== null && video.duration > 0" class="video-card__duration">
        {{ formatDuration(video.duration) }}
      </span>
      <span v-if="video.categoryName" class="video-card__category">{{ video.categoryName }}</span>

      <span class="video-card__hover-play" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="22" height="22">
          <path d="M9 7l8 5-8 5z" fill="currentColor" />
        </svg>
      </span>
    </div>

    <div class="video-card__body">
      <h3 class="video-card__title" :title="video.title">{{ video.title }}</h3>
      <p class="video-card__meta">
        <span>{{ episodeCountText }}</span>
        <span class="video-card__dot" aria-hidden="true">·</span>
        <span>{{ viewCountText }}</span>
      </p>
    </div>
  </article>
</template>

<style scoped lang="scss">
@use '../styles/variables' as *;

.video-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.025);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 12px;
  transition:
    transform 0.22s ease,
    border-color 0.22s ease,
    box-shadow 0.22s ease;

  &:hover,
  &:focus-visible {
    transform: translateY(-3px);
    border-color: rgba(91, 141, 239, 0.45);
    box-shadow: 0 14px 34px rgba(0, 0, 0, 0.38);
    outline: none;

    .video-card__img {
      transform: scale(1.045);
    }

    .video-card__hover-play {
      opacity: 1;
      transform: scale(1);
    }
  }

  @media (prefers-reduced-motion: reduce) {
    transition: none;

    &:hover,
    &:focus-visible {
      transform: none;
    }
  }
}

.video-card__cover {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: $vk-ink-700;
}

.video-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s ease;
}

.video-card__placeholder {
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

.video-card__duration {
  position: absolute;
  right: 8px;
  bottom: 8px;
  padding: 2px 6px;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: #f2f5fa;
  background: rgba(4, 7, 12, 0.78);
  border-radius: 5px;
}

.video-card__category {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 2px 8px;
  font-size: 11px;
  color: #dfe7f3;
  background: rgba(4, 7, 12, 0.62);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  backdrop-filter: blur(4px);
}

.video-card__hover-play {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  background: rgba(5, 9, 15, 0.34);
  opacity: 0;
  transform: scale(1.06);
  transition:
    opacity 0.22s ease,
    transform 0.22s ease;

  svg {
    filter: drop-shadow(0 2px 8px rgba(0, 0, 0, 0.55));
  }
}

.video-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  padding: 12px 13px 14px;
}

.video-card__title {
  display: -webkit-box;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  color: $vk-text;
  word-break: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.video-card__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: auto;
  font-size: 12px;
  color: $vk-text-muted;
}

.video-card__dot {
  color: rgba(139, 151, 168, 0.6);
}
</style>
