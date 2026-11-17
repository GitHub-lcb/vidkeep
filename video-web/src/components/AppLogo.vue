<script setup lang="ts">
import { computed } from 'vue'

/**
 * 品牌标：vidkeep —— forest 绿底 + 播放三角（Qoder 规范的低调几何风格）
 */
const props = withDefaults(
  defineProps<{
    size?: 'sm' | 'md' | 'lg'
    wordmark?: boolean
    muted?: boolean
  }>(),
  { size: 'md', wordmark: true, muted: false },
)

const markSize = computed<number>(() => {
  if (props.size === 'sm') return 20
  if (props.size === 'lg') return 32
  return 26
})

const nameSize = computed<number>(() => {
  if (props.size === 'sm') return 14
  if (props.size === 'lg') return 20
  return 16
})

const markRadius = computed<number>(() => Math.round(markSize.value * 0.3))
const triangleSize = computed<number>(() => Math.round(markSize.value * 0.52))
</script>

<template>
  <span class="app-logo" :class="{ 'app-logo--muted': props.muted }">
    <span class="app-logo__mark" :style="{ width: `${markSize}px`, height: `${markSize}px`, borderRadius: `${markRadius}px` }">
      <svg
        :width="triangleSize"
        :height="triangleSize"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path d="M8 5.5v13l11-6.5z" fill="currentColor" />
      </svg>
    </span>
    <span v-if="props.wordmark" class="app-logo__word" :style="{ fontSize: `${nameSize}px` }">vidkeep</span>
  </span>
</template>

<style scoped>
.app-logo {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--q-text);
  user-select: none;
}

.app-logo__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  color: var(--q-text-on-primary);
  background: var(--q-primary);
}

.app-logo__word {
  font-family: var(--q-font-brand);
  font-weight: 500;
  letter-spacing: -0.01em;
  line-height: 1;
  color: inherit;
}

.app-logo--muted {
  color: var(--q-text-secondary);
}

.app-logo--muted .app-logo__mark {
  color: var(--q-text-tertiary);
  background: var(--q-fill-secondary);
}
</style>
