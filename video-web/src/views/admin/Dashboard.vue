<script setup lang="ts">
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'

interface StatItem {
  label: string
  value: string
  hint: string
}

interface ModuleItem {
  code: string
  title: string
  desc: string
  status: string
}

const userStore = useUserStore()
const displayName = computed<string>(() => userStore.displayName)

const stats: StatItem[] = [
  { label: '视频总数', value: '--', hint: 'M3 接入' },
  { label: '存储占用', value: '--', hint: 'M3 接入' },
  { label: '转码中', value: '--', hint: 'M2 接入' },
]

const modules: ModuleItem[] = [
  {
    code: 'M1',
    title: '分片上传与存储',
    desc: '秒传检查、断点续传、分片合并与 MD5 校验',
    status: '规划中',
  },
  {
    code: 'M2',
    title: '转码流水线与播放',
    desc: 'FFmpeg 探测与转码、HLS 输出、播放器接入',
    status: '规划中',
  },
  {
    code: 'M3',
    title: '管理后台',
    desc: '视频列表、编辑、上传页与分类管理',
    status: '规划中',
  },
]
</script>

<template>
  <div class="vk-page dashboard">
    <header class="dashboard-head">
      <h1 class="vk-page__title">你好，{{ displayName }}</h1>
      <p class="vk-page__subtitle">
        欢迎回到 vidkeep 管理后台。当前处于 M0 阶段，登录流程与管理框架已就绪。
      </p>
    </header>

    <section class="vk-grid" aria-label="站点概览">
      <div v-for="item in stats" :key="item.label" class="vk-card stat-card">
        <span class="stat-card__label">{{ item.label }}</span>
        <strong class="stat-card__value">{{ item.value }}</strong>
        <span class="stat-card__hint">{{ item.hint }}</span>
      </div>
    </section>

    <section class="dashboard-section">
      <h2 class="dashboard-section__title">后续模块</h2>
      <div class="vk-grid">
        <article v-for="item in modules" :key="item.code" class="vk-card module-card">
          <div class="module-card__head">
            <span class="module-card__code">{{ item.code }}</span>
            <span class="module-card__status">{{ item.status }}</span>
          </div>
          <h3 class="module-card__title">{{ item.title }}</h3>
          <p class="module-card__desc">{{ item.desc }}</p>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.dashboard-head {
  max-width: 720px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat-card__label {
  font-size: 13px;
  color: $vk-light-text-muted;
}

.stat-card__value {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.1;
  color: #151c28;
}

.stat-card__hint {
  font-size: 12px;
  color: #a3adbb;
}

.dashboard-section {
  margin-top: 34px;
}

.dashboard-section__title {
  font-size: 15px;
  font-weight: 600;
  color: #151c28;
}

.module-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.module-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.module-card__code {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: $vk-accent;
}

.module-card__status {
  padding: 0 7px;
  font-size: 11px;
  line-height: 18px;
  color: $vk-light-text-muted;
  border: 1px solid $vk-light-border;
  border-radius: 4px;
}

.module-card__title {
  font-size: 15px;
  font-weight: 600;
  color: #151c28;
}

.module-card__desc {
  font-size: 13px;
  line-height: 1.65;
  color: $vk-light-text-muted;
}
</style>
