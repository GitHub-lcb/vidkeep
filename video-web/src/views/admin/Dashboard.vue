<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listAdminVideos } from '@/api/video'
import { listCategories } from '@/api/category'
import { isBizError } from '@/api/request'
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
  /** 已实现的模块用强调色状态徽标 */
  done: boolean
}

const userStore = useUserStore()
const displayName = computed<string>(() => userStore.displayName)

const videoTotal = ref<number | null>(null)
const categoryTotal = ref<number | null>(null)
const loading = ref(false)
const loadError = ref('')

/** 统计卡片：视频总数与分类数走真实接口；转码中接口 M2 完成后接入 */
const stats = computed<StatItem[]>(() => [
  {
    label: '视频总数',
    value: videoTotal.value === null ? '--' : String(videoTotal.value),
    hint: '全部作品（含未上线）',
  },
  {
    label: '分类数',
    value: categoryTotal.value === null ? '--' : String(categoryTotal.value),
    hint: '按 sort 升序展示',
  },
  {
    label: '转码中',
    value: '--',
    hint: '接口待 M2 完成后接入',
  },
])

const modules: ModuleItem[] = [
  {
    code: 'M1',
    title: '分片上传与存储',
    desc: '抽样指纹秒传、断点续传、3 并发分片上传与合并',
    status: '已实现',
    done: true,
  },
  {
    code: 'M2',
    title: '转码流水线与播放',
    desc: 'FFmpeg 探测与转码、HLS 输出；上传面板已接入转码进度轮询',
    status: '联调中',
    done: false,
  },
  {
    code: 'M3',
    title: '管理后台',
    desc: '视频列表、作品编辑、上传页与分类管理',
    status: '已实现',
    done: true,
  },
]

async function loadStats(): Promise<void> {
  loading.value = true
  loadError.value = ''

  try {
    // size=1 只为拿 total，避免拉取整页数据
    const page = await listAdminVideos({ page: 1, size: 1 })
    videoTotal.value = page.total
  } catch (error) {
    loadError.value = isBizError(error) ? error.message : '加载视频统计失败'
  }

  try {
    const categories = await listCategories()
    categoryTotal.value = categories.length
  } catch (error) {
    if (loadError.value.length === 0) {
      loadError.value = isBizError(error) ? error.message : '加载分类统计失败'
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadStats()
})
</script>

<template>
  <div class="vk-page dashboard">
    <header class="dashboard-head">
      <h1 class="vk-page__title">你好，{{ displayName }}</h1>
      <p class="vk-page__subtitle">
        欢迎回到 vidkeep 管理后台。M1 分片上传与 M3 管理页已接入，可在视频管理中新建作品、上传剧集并维护分类。
      </p>
    </header>

    <el-alert
      v-if="loadError.length > 0"
      class="dashboard-alert"
      type="error"
      :closable="false"
      show-icon
      :title="loadError"
    >
      <el-button size="small" :loading="loading" @click="loadStats">重新加载</el-button>
    </el-alert>

    <section v-loading="loading" class="vk-grid" aria-label="站点概览">
      <div v-for="item in stats" :key="item.label" class="vk-card stat-card">
        <span class="stat-card__label">{{ item.label }}</span>
        <strong class="stat-card__value">{{ item.value }}</strong>
        <span class="stat-card__hint">{{ item.hint }}</span>
      </div>
    </section>

    <section class="dashboard-section">
      <h2 class="dashboard-section__title">模块进度</h2>
      <div class="vk-grid">
        <article v-for="item in modules" :key="item.code" class="vk-card module-card">
          <div class="module-card__head">
            <span class="module-card__code">{{ item.code }}</span>
            <span
              class="module-card__status"
              :class="{ 'module-card__status--done': item.done }"
            >
              {{ item.status }}
            </span>
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

.dashboard-alert {
  margin-top: 18px;
  border-radius: 10px;
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

  &--done {
    color: #2f7a4d;
    background: #f1f8f3;
    border-color: #cfe8d4;
  }
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
