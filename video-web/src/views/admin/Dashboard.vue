<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getAdminStats } from '@/api/admin'
import { isBizError } from '@/api/request'
import { useUserStore } from '@/stores/user'
import { formatDuration } from '@/utils/format'
import type { AdminStats } from '@/types/admin'

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

/** P1 站点统计（GET /api/admin/stats，F-15）；null 表示尚未加载成功 */
const statsData = ref<AdminStats | null>(null)
const loading = ref(false)
const loadError = ref('')

/** 大数字加千分位，避免播放次数过长难以辨认 */
function formatCount(value: number): string {
  return Number.isFinite(value) ? value.toLocaleString('zh-CN') : '--'
}

/** 统计卡片：视频/分类/转码中/播放次数/累计观看时长（P1 起统一走统计接口） */
const stats = computed<StatItem[]>(() => {
  const data = statsData.value
  return [
    {
      label: '视频总数',
      value: data === null ? '--' : formatCount(data.videoCount),
      hint: '全部作品（含未上线）',
    },
    {
      label: '分类数',
      value: data === null ? '--' : formatCount(data.categoryCount),
      hint: '分类管理维护',
    },
    {
      label: '转码中',
      value: data === null ? '--' : formatCount(data.transcodingCount),
      hint: '排队与处理中的任务',
    },
    {
      label: '播放次数',
      value: data === null ? '--' : formatCount(data.totalViewCount),
      hint: '前台累计播放',
    },
    {
      label: '累计观看时长',
      value: data === null ? '--' : formatDuration(data.totalWatchSeconds),
      hint: '按播放进度累计',
    },
  ]
})

/** 里程碑进度：M0–M3 已完成，P1 增强功能已接入 */
const modules: ModuleItem[] = [
  {
    code: 'M0',
    title: '环境与登录',
    desc: 'Spring Boot 骨架、JWT 登录鉴权与前后端工程化',
    status: '已实现',
    done: true,
  },
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
    desc: 'FFmpeg 探测与转码、HLS 输出与播放进度记忆',
    status: '已实现',
    done: true,
  },
  {
    code: 'M3',
    title: '管理后台',
    desc: '视频列表、作品编辑、上传页与分类管理',
    status: '已实现',
    done: true,
  },
  {
    code: 'P1',
    title: '增强与安全',
    desc: '播放统计、播放地址签名、站点访问口令、操作日志',
    status: '已接入',
    done: true,
  },
]

/** 加载站点统计；失败进入可重试的错误态 */
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
  <div class="vk-page dashboard">
    <header class="dashboard-head">
      <h1 class="vk-page__title">你好，{{ displayName }}</h1>
      <p class="vk-page__subtitle">
        欢迎回到 vidkeep 管理后台。M0–M3 已完成，P1 的播放统计、播放地址签名、站点访问口令与操作日志已接入，
        可在「操作日志」中追溯管理员操作。
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
      <template #default>
        <el-button size="small" :loading="loading" @click="loadStats">重新加载</el-button>
      </template>
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
