<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import QIcon from '@/components/QIcon.vue'
import { getTheme, onThemeChange, toggleTheme, type ThemeName } from '@/utils/theme'

const theme = ref<ThemeName>('light')
let unsubscribe: (() => void) | null = null

function handleToggle(): void {
  theme.value = toggleTheme()
}

onMounted(() => {
  theme.value = getTheme()
  unsubscribe = onThemeChange((value) => {
    theme.value = value
  })
})

onBeforeUnmount(() => {
  unsubscribe?.()
})
</script>

<template>
  <button
    type="button"
    class="q-icon-btn"
    :aria-label="theme === 'dark' ? '切换到浅色主题' : '切换到深色主题'"
    :title="theme === 'dark' ? '浅色主题' : '深色主题'"
    @click="handleToggle"
  >
    <QIcon :name="theme === 'dark' ? 'sun' : 'moon'" :size="16" />
  </button>
</template>
