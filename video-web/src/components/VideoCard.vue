<script setup lang="ts">
import QIcon from '@/components/QIcon.vue'
import { formatDuration } from '@/utils/format'
import type { PublicVideoItem } from '@/types/video'

/**
 * 前台视频卡片（Qoder 规范：16:9 封面 + 发丝描边 + 悬浮播放；信息层级 13/11）。
 */
const props = defineProps<{ video: PublicVideoItem }>()
</script>

<template>
  <RouterLink class="video-card" :to="`/videos/${props.video.id}`">
    <span class="video-card__cover">
      <img
        v-if="props.video.coverUrl"
        class="video-card__img"
        :src="props.video.coverUrl"
        :alt="props.video.title"
        loading="lazy"
      />
      <span v-else class="video-card__placeholder">
        <QIcon name="film" :size="20" />
      </span>

      <span class="video-card__play" aria-hidden="true">
        <QIcon name="play" :size="15" />
      </span>

      <span v-if="props.video.duration && props.video.duration > 0" class="video-card__duration">
        {{ formatDuration(props.video.duration) }}
      </span>
    </span>

    <span class="video-card__body">
      <span class="video-card__title">{{ props.video.title }}</span>
      <span class="video-card__meta">
        <span>{{ props.video.categoryName || '未分类' }}</span>
        <span class="video-card__dot" aria-hidden="true">·</span>
        <span>{{ props.video.episodeCount }} 集</span>
        <template v-if="props.video.viewCount > 0">
          <span class="video-card__dot" aria-hidden="true">·</span>
          <span>{{ props.video.viewCount.toLocaleString('zh-CN') }} 次播放</span>
        </template>
      </span>
    </span>
  </RouterLink>
</template>

<style scoped lang="scss">
.video-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  cursor: pointer;
}

.video-card__cover {
  position: relative;
  display: block;
  width: 100%;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  background: var(--q-fill-tertiary);
  border-radius: var(--q-r-surface);
  box-shadow: 0 0 0 1px var(--q-border-quaternary) inset;
}

.video-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--q-dur-slow) var(--q-ease);
}

.video-card__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--q-text-quaternary);
}

.video-card__play {
  position: absolute;
  top: 50%;
  left: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  color: #fdfdfd;
  background: rgba(8, 8, 7, 0.55);
  border-radius: 50%;
  opacity: 0;
  transform: translate(-50%, -50%) scale(0.92);
  transition: opacity var(--q-dur-fast) var(--q-ease), transform var(--q-dur-fast) var(--q-ease);
  backdrop-filter: blur(4px);
}

.video-card__duration {
  position: absolute;
  right: 6px;
  bottom: 6px;
  padding: 1px 5px;
  font-size: var(--q-fs-11);
  font-variant-numeric: tabular-nums;
  line-height: 15px;
  color: #fdfdfd;
  background: rgba(8, 8, 7, 0.68);
  border-radius: var(--q-r-sm);
}

.video-card__body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding-top: 10px;
}

.video-card__title {
  display: -webkit-box;
  overflow: hidden;
  font-size: var(--q-fs-13);
  font-weight: 500;
  line-height: var(--q-lh-18);
  color: var(--q-text);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.video-card__meta {
  display: flex;
  gap: 4px;
  align-items: center;
  overflow: hidden;
  font-size: var(--q-fs-11);
  line-height: var(--q-lh-16);
  color: var(--q-text-tertiary);
  white-space: nowrap;
}

.video-card__dot {
  color: var(--q-text-quaternary);
}

.video-card:hover {
  .video-card__img {
    transform: scale(1.03);
  }

  .video-card__play {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
  }

  .video-card__title {
    color: var(--q-primary-text);
  }
}

.video-card:focus-visible {
  outline: none;

  .video-card__cover {
    box-shadow: 0 0 0 2px var(--q-primary-border);
  }
}

@media (prefers-reduced-motion: reduce) {
  .video-card__img,
  .video-card__play {
    transition: none;
  }
}
</style>
