<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import QIcon from '@/components/QIcon.vue'
import { getAdminStats } from '@/api/admin'
import { isBizError } from '@/api/request'
import { useUserStore } from '@/stores/user'
import { formatDuration } from '@/utils/format'
import type { AdminStats } from '@/types/admin'

/**
 * 后台概览（Qoder 规范）：统计卡片（发丝描边 + 24px 数值）+ 流程与里程碑面板。
 */
interface StatItem {
  label: string
  value: string
  hint: string
}

interface ModuleItem {
  code: string
  title: string
  desc: string
}

interface FlowStep {
  icon: 'upload' | 'refresh' | 'play'
  title: string
  desc: string
}

const userStore = useUserStore()
const displayName = computed<string>(() => userStore.displayName)

const statsData = ref<AdminStats | null>(null)
const loading = ref(false)
const loadError = ref('')

const FLOW_STEPS: FlowStep[] = [
  { icon: 'upload', title: '上传', desc: '分片断点续传，秒传去重' },
  { icon: 'refresh', title: '转码', desc: 'FFmpeg 探测，REMUX 优先' },
  { icon: 'play', title: '播放', desc: 'HLS 分发，进度记忆' },
]

const MODULES: ModuleItem[] = [
  { code: 'M0', title: '工程与登录', desc: 'Spring Boot 骨架、JWT 鉴权与前后端工程化' },
  { code: 'M1', title: '分片上传与存储', desc: '抽样指纹秒传、断点续传、3 并发分片合并' },
  { code: 'M2', title: '转码与播放', desc: 'FFmpeg 探测与转码、HLS 输出、进度记忆' },
  { code: 'M3', title: '管理后台', desc: '视频管理、作品编辑、上传页与分类管理' },
  { code: 'P1', title: '增强与安全', desc: '播放统计、地址签名、站点口令、操作日志' },
]

function formatCount(value: number): string {
  return Number.isFinite(value) ? value.toLocaleString('zh-CN') : '--'
}

const stats = computed<StatItem[]>(() => {
  const data = statsData.value
  return [
    { label: '视频总数', value: data === null ? '--' : formatCount(data.videoCount), hint: '全部作品（含未上线）' },
    { label: '分类数', value: data === null ? '--' : formatCount(data.categoryCount), hint: '分类管理维护' },
    { label: '转码中', value: data === null ? '--' : formatCount(data.transcodingCount), hint: '排队与处理中的任务' },
    { label: '播放次数', value: data === null ? '--' : formatCount(data.totalViewCount), hint: '前台累计播放' },
    { label: '累计观看时长', value: data === null ? '--' : formatDuration(data.totalWatchSeconds), hint: '按播放进度累计' },
  ]
})

async function loadStats(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    statsData.value = await getAdminStats()
  } catch (error) {
    loadError.value = isBizError(error) ? error.message : '加载站点统计失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadStats()
})
</script>

<template>
  <div class="q-page q-fade-in">
    <header class="q-page-head">
      <div class="q-page-head__main">
        <h1 class="q-page-title">概览</h1>
        <p class="q-page-desc">欢迎回来，{{ displayName }}。这里是 vidkeep 的运行状态。</p>
      </div>
      <div class="q-page-actions">
        <button class="q-btn q-btn--outline q-btn--sm" type="button" :disabled="loading" @click="loadStats">
          <QIcon name="refresh" :size="13" />
          刷新
        </button>
        <RouterLink class="q-btn q-btn--primary q-btn--sm" to="/admin/upload">
          <QIcon name="upload" :size="14" />
          上传视频
        </RouterLink>
      </div>
    </header>

    <div v-if="loadError" class="dash-error" role="alert">
      <QIcon name="alert" :size="16" />
      <span>{{ loadError }}</span>
      <button class="q-btn q-btn--outline q-btn--sm" type="button" @click="loadStats">重试</button>
    </div>

    <section class="dash-stats" aria-label="站点统计">
      <article v-for="item in stats" :key="item.label" class="q-card q-card--pad dash-stat">
        <p class="dash-stat__label">{{ item.label }}</p>
        <p class="dash-stat__value">{{ item.value }}</p>
        <p class="dash-stat__hint">{{ item.hint }}</p>
      </article>
    </section>

    <section class="dash-grid">
      <article class="q-card q-card--pad dash-panel">
        <header class="dash-panel__head">
          <h2 class="q-section-title">处理流程</h2>
          <span class="q-caption">上传 → 转码 → 播放</span>
        </header>
        <ol class="dash-flow">
          <li v-for="(step, index) in FLOW_STEPS" :key="step.title" class="dash-flow__item">
            <span class="dash-flow__index">{{ index + 1 }}</span>
            <span class="dash-flow__icon"><QIcon :name="step.icon" :size="16" /></span>
            <span class="dash-flow__body">
              <span class="dash-flow__title">{{ step.title }}</span>
              <span class="dash-flow__desc">{{ step.desc }}</span>
            </span>
          </li>
        </ol>
        <div class="dash-panel__foot">
          <RouterLink class="q-btn q-btn--ghost q-btn--sm" to="/admin/videos">
            管理视频
            <QIcon name="chevron-right" :size="13" />
          </RouterLink>
          <RouterLink class="q-btn q-btn--ghost q-btn--sm" to="/admin/categories">
            分类管理
            <QIcon name="chevron-right" :size="13" />
          </RouterLink>
        </div>
      </article>

      <article class="q-card q-card--pad dash-panel">
        <header class="dash-panel__head">
          <h2 class="q-section-title">已交付能力</h2>
          <span class="q-caption">M0 → P1</span>
        </header>
        <ul class="dash-modules">
          <li v-for="module in MODULES" :key="module.code" class="dash-module">
            <span class="dash-module__code">{{ module.code }}</span>
            <span class="dash-module__body">
              <span class="dash-module__title">{{ module.title }}</span>
              <span class="dash-module__desc">{{ module.desc }}</span>
            </span>
            <span class="q-tag q-tag--success">已实现</span>
          </li>
        </ul>
      </article>
    </section>
  </div>
</template>

<style scoped lang="scss">
.dash-error {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  margin-top: 16px;
  font-size: var(--q-fs-12);
  color: var(--q-error);
  background: var(--q-error-bg);
  border-radius: var(--q-r-lg);
}

.dash-stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-top: 20px;
}

.dash-stat__label {
  font-size: var(--q-fs-12);
  color: var(--q-text-tertiary);
}

.dash-stat__value {
  margin-top: 6px;
  font-size: var(--q-fs-24);
  font-weight: 600;
  line-height: 1.1;
  color: var(--q-text);
  font-variant-numeric: tabular-nums;
}

.dash-stat__hint {
  margin-top: 4px;
  font-size: var(--q-fs-11);
  color: var(--q-text-quaternary);
}

.dash-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.25fr);
  gap: 16px;
  margin-top: 16px;
}

.dash-panel__head {
  display: flex;
  gap: 12px;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--q-border-quaternary);
}

.dash-flow {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 0;
}

.dash-flow__item {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px;
  border-radius: var(--q-r-lg);

  &:hover {
    background: var(--q-fill-tertiary);
  }
}

.dash-flow__index {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: var(--q-fs-10);
  font-weight: 500;
  color: var(--q-text-tertiary);
  background: var(--q-fill-tertiary);
  border-radius: 50%;
}

.dash-flow__icon {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--q-primary-text);
  background: var(--q-primary-bg);
  border-radius: var(--q-r-lg);
}

.dash-flow__body {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.dash-flow__title {
  font-size: var(--q-fs-13);
  font-weight: 500;
  color: var(--q-text);
}

.dash-flow__desc {
  font-size: var(--q-fs-11);
  color: var(--q-text-tertiary);
}

.dash-panel__foot {
  display: flex;
  gap: 4px;
  padding-top: 8px;
  border-top: 1px solid var(--q-border-quaternary);
}

.dash-modules {
  display: flex;
  flex-direction: column;
  padding: 8px 0;
}

.dash-module {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px;
  border-radius: var(--q-r-lg);

  &:hover {
    background: var(--q-fill-tertiary);
  }
}

.dash-module__code {
  flex: none;
  width: 34px;
  font-family: var(--q-font-mono);
  font-size: var(--q-fs-11);
  color: var(--q-text-quaternary);
}

.dash-module__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.dash-module__title {
  font-size: var(--q-fs-13);
  font-weight: 500;
  color: var(--q-text);
}

.dash-module__desc {
  font-size: var(--q-fs-11);
  color: var(--q-text-tertiary);
}

@media (max-width: 1280px) {
  .dash-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .dash-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 720px) {
  .dash-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dash-module__desc {
    display: none;
  }
}
</style>
