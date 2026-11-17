<script setup lang="ts">
import { computed } from 'vue'

/** 线性图标（stroke 1.5，24 viewBox）；播放三角用填充 */
type IconName =
  | 'home'
  | 'search'
  | 'upload'
  | 'film'
  | 'folder'
  | 'logs'
  | 'sun'
  | 'moon'
  | 'play'
  | 'chevron-down'
  | 'chevron-right'
  | 'chevron-left'
  | 'more'
  | 'user'
  | 'logout'
  | 'settings'
  | 'close'
  | 'plus'
  | 'refresh'
  | 'trash'
  | 'edit'
  | 'eye'
  | 'arrow-left'
  | 'check'
  | 'alert'
  | 'clock'
  | 'external'
  | 'menu'
  | 'panel-left'
  | 'sparkle'
  | 'image'
  | 'tag'

const props = withDefaults(
  defineProps<{
    name: IconName
    size?: number
    strokeWidth?: number
  }>(),
  { size: 16, strokeWidth: 1.5 },
)

const paths: Record<IconName, { d: string[]; fill?: boolean }> = {
  home: { d: ['m3 10 9-7 9 7v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z', 'M9 21v-9h6v9'] },
  search: { d: ['M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z', 'm21 21-4.3-4.3'] },
  upload: { d: ['M12 3v12', 'm7 8 5-5 5 5', 'M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2'] },
  film: {
    d: [
      'M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z',
      'M7 4v16',
      'M17 4v16',
      'M3 9h4',
      'M3 15h4',
      'M17 9h4',
      'M17 15h4',
    ],
  },
  folder: { d: ['M3 7.5 4.8 5.3A2 2 0 0 1 6.4 4.5h4.1l2 2.2H18a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'] },
  logs: { d: ['M8 6h13', 'M8 12h13', 'M8 18h13', 'M3.5 6h.01', 'M3.5 12h.01', 'M3.5 18h.01'] },
  sun: {
    d: [
      'M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
      'M12 2v2',
      'M12 20v2',
      'm4.9 4.9 1.4 1.4',
      'm17.7 17.7 1.4 1.4',
      'M2 12h2',
      'M20 12h2',
      'm4.9 19.1 1.4-1.4',
      'm17.7 6.3 1.4-1.4',
    ],
  },
  moon: { d: ['M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z'] },
  play: { d: ['M8 5.5v13l11-6.5z'], fill: true },
  'chevron-down': { d: ['m6 9 6 6 6-6'] },
  'chevron-right': { d: ['m9 6 6 6-6 6'] },
  'chevron-left': { d: ['m15 6-6 6 6 6'] },
  more: { d: ['M5 12h.01', 'M12 12h.01', 'M19 12h.01'] },
  user: { d: ['M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z', 'M4 21a8 8 0 0 1 16 0'] },
  logout: { d: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'm16 17 5-5-5-5', 'M21 12H9'] },
  settings: {
    d: [
      'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
      'M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H10a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9V10a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z',
    ],
  },
  close: { d: ['M18 6 6 18', 'M6 6l12 12'] },
  plus: { d: ['M12 5v14', 'M5 12h14'] },
  refresh: { d: ['M21 12a9 9 0 1 1-2.6-6.4', 'M21 3v6h-6'] },
  trash: { d: ['M3 6h18', 'M8 6V4h8v2', 'M19 6l-1 14H6L5 6', 'M10 11v6', 'M14 11v6'] },
  edit: { d: ['M12 20h9', 'M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z'] },
  eye: { d: ['M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z', 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z'] },
  'arrow-left': { d: ['M19 12H5', 'm12 19-7-7 7-7'] },
  check: { d: ['m20 6-11 11-5-5'] },
  alert: {
    d: [
      'M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z',
      'M12 9v4',
      'M12 17h.01',
    ],
  },
  clock: { d: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M12 7v5l3 2'] },
  external: { d: ['M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6', 'M15 3h6v6', 'M10 14 21 3'] },
  menu: { d: ['M4 6h16', 'M4 12h16', 'M4 18h16'] },
  'panel-left': { d: ['M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z', 'M9 3v18'] },
  sparkle: { d: ['M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z'] },
  image: { d: ['M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z', 'M8.5 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z', 'm21 15-5-5L5 21'] },
  tag: {
    d: [
      'M20.6 13.4 13.4 20.6a2 2 0 0 1-2.8 0l-7.2-7.2a2 2 0 0 1-.6-1.4V4a1 1 0 0 1 1-1h8a2 2 0 0 1 1.4.6l7.2 7.2a2 2 0 0 1 0 2.8z',
      'M7 7h.01',
    ],
  },
}

const icon = computed(() => paths[props.name])
</script>

<template>
  <svg
    class="q-icon"
    :width="props.size"
    :height="props.size"
    viewBox="0 0 24 24"
    :fill="icon.fill ? 'currentColor' : 'none'"
    :stroke="icon.fill ? 'none' : 'currentColor'"
    :stroke-width="props.strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="(d, index) in icon.d" :key="index" :d="d" />
  </svg>
</template>

<style scoped>
.q-icon {
  display: block;
  flex: none;
}
</style>
