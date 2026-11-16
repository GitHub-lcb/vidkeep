<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import UploadPanel from '@/components/UploadPanel.vue'
import { getAdminVideo, listAdminVideos } from '@/api/video'
import { isBizError } from '@/api/request'
import { formatDateTime } from '@/utils/format'
import type { VideoAdminDetail, VideoAdminItem } from '@/types/video'

const route = useRoute()
const router = useRouter()

/** 作品下拉上限：文档要求一次取 200 条，足够覆盖个人站点作品量 */
const WORK_OPTION_SIZE = 200

function parseQueryVideoId(): number | null {
  const raw = route.query.videoId
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value !== 'string' || value.length === 0) return null
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

const works = ref<VideoAdminItem[]>([])
const worksLoading = ref(false)
const selectedVideoId = ref<number | null>(parseQueryVideoId())
const currentVideo = ref<VideoAdminDetail | null>(null)
const detailLoading = ref(false)

/** 本次上传产生的最新剧集，用于完成后跳转播放 */
const latestEpisodeId = ref<number | null>(null)
const transcodeDone = ref(false)

/** 下拉选项：若 query 指定的作品不在前 200 条中，用详情补一个选项，避免 el-select 只显示数字 */
const videoOptions = computed<VideoAdminItem[]>(() => {
  const current = currentVideo.value
  if (current === null) return works.value
  if (works.value.some((item) => item.id === current.id)) return works.value
  return [current, ...works.value]
})

function errorText(error: unknown, fallback: string): string {
  return isBizError(error) ? error.message : fallback
}

async function loadWorks(): Promise<void> {
  worksLoading.value = true
  try {
    const page = await listAdminVideos({ page: 1, size: WORK_OPTION_SIZE })
    works.value = page.records
  } catch (error) {
    ElMessage.error(errorText(error, '加载作品列表失败'))
  } finally {
    worksLoading.value = false
  }
}

async function loadCurrentVideo(): Promise<void> {
  const id = selectedVideoId.value
  if (id === null) {
    currentVideo.value = null
    return
  }
  detailLoading.value = true
  try {
    currentVideo.value = await getAdminVideo(id)
  } catch (error) {
    currentVideo.value = null
    ElMessage.error(errorText(error, '加载作品信息失败'))
  } finally {
    detailLoading.value = false
  }
}

/** 上传/转码完成后静默刷新作品信息（集数、转码状态），失败不打断当前流程 */
async function refreshCurrentVideo(): Promise<void> {
  const id = selectedVideoId.value
  if (id === null) return
  try {
    currentVideo.value = await getAdminVideo(id)
  } catch {
    // 静默失败：下方上传面板仍会展示真实进度
  }
}

function handleCompleted(episodeId: number): void {
  latestEpisodeId.value = episodeId
  transcodeDone.value = false
  void refreshCurrentVideo()
}

function handleTranscodeDone(episodeId: number): void {
  latestEpisodeId.value = episodeId
  transcodeDone.value = true
  void refreshCurrentVideo()
}

function openPlayer(): void {
  const videoId = selectedVideoId.value
  const episodeId = latestEpisodeId.value
  if (videoId === null || episodeId === null) return
  window.open(`/play/${videoId}/${episodeId}`, '_blank', 'noopener,noreferrer')
}

function goVideoDetail(): void {
  const videoId = selectedVideoId.value
  if (videoId === null) return
  void router.push(`/admin/videos/${videoId}`)
}

function goVideoList(): void {
  void router.push('/admin/videos')
}

// 切换作品时同步地址栏并加载详情；UploadPanel 以 selectedVideoId 为 key 重新挂载
watch(selectedVideoId, (next) => {
  latestEpisodeId.value = null
  transcodeDone.value = false
  if (next === null) {
    currentVideo.value = null
    void router.replace({ path: '/admin/upload' })
    return
  }
  void router.replace({ path: '/admin/upload', query: { videoId: String(next) } })
  void loadCurrentVideo()
})

onMounted(async () => {
  await loadWorks()
  if (selectedVideoId.value !== null) await loadCurrentVideo()
})
</script>

<template>
  <div class="vk-page video-upload">
    <header class="page-head">
      <div>
        <h1 class="vk-page__title">视频上传</h1>
        <p class="vk-page__subtitle">
          选择作品后上传视频文件，系统自动分片上传、合并并转码为 HLS；支持秒传与断点续传
        </p>
      </div>
      <el-button @click="goVideoList">返回视频管理</el-button>
    </header>

    <!-- 选择作品 -->
    <section class="select-card">
      <div class="select-card__head">
        <h2 class="section-title">选择作品</h2>
        <span class="section-hint">一个作品可上传多个视频文件，自动成为第 1、2、3… 集</span>
      </div>
      <div class="select-card__body">
        <el-select
          v-model="selectedVideoId"
          class="work-select"
          filterable
          :loading="worksLoading"
          placeholder="请选择要上传视频的作品"
        >
          <el-option
            v-for="item in videoOptions"
            :key="item.id"
            :label="`${item.title}（#${item.id}）`"
            :value="item.id"
          />
        </el-select>

        <template v-if="works.length === 0 && !worksLoading">
          <span class="select-card__empty">还没有作品，先创建一个作品壳再上传视频</span>
          <el-button type="primary" plain size="small" @click="goVideoList">去新建作品</el-button>
        </template>
      </div>
    </section>

    <!-- 当前作品信息 -->
    <section v-if="currentVideo !== null" v-loading="detailLoading" class="info-card">
      <div class="info-card__main">
        <h2 class="info-card__title">{{ currentVideo.title }}</h2>
        <div class="info-card__meta">
          <el-tag :type="currentVideo.status === 1 ? 'success' : 'info'" effect="plain">
            {{ currentVideo.status === 1 ? '已上线' : '已下线' }}
          </el-tag>
          <span>{{ currentVideo.categoryName ?? '未分类' }}</span>
          <span>{{ currentVideo.episodeCount }} 个剧集</span>
          <span>创建于 {{ formatDateTime(currentVideo.createdAt) }}</span>
        </div>
      </div>
      <div class="info-card__actions">
        <el-button size="small" @click="refreshCurrentVideo">刷新集数</el-button>
        <el-button size="small" type="primary" plain @click="goVideoDetail">编辑作品</el-button>
      </div>
    </section>

    <!-- 上传面板：切换作品时通过 key 重新挂载，避免旧任务串状态 -->
    <section v-if="selectedVideoId !== null" class="panel-card">
      <UploadPanel
        :key="selectedVideoId"
        :video-id="selectedVideoId"
        @completed="handleCompleted"
        @transcode-done="handleTranscodeDone"
      />
    </section>

    <!-- 转码完成后的后续动作 -->
    <section v-if="transcodeDone" class="done-card">
      <div class="done-card__text">
        <strong>转码完成</strong>
        <span>剧集已可播放，可前往视频详情继续上传下一集，或直接打开前台播放页预览。</span>
      </div>
      <div class="done-card__actions">
        <el-button @click="goVideoDetail">前往视频详情</el-button>
        <el-button type="primary" @click="openPlayer">前台播放</el-button>
      </div>
    </section>

    <!-- 未选择作品时的空态 -->
    <section v-else-if="selectedVideoId === null && works.length === 0 && !worksLoading" class="empty-card">
      <p class="empty-card__title">还没有可上传的作品</p>
      <p class="empty-card__desc">按「先建作品，再传文件」两步式操作：先创建作品壳，再回到本页选择作品上传视频。</p>
      <el-button type="primary" @click="goVideoList">去视频管理新建作品</el-button>
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #151c28;
}

.section-hint {
  font-size: 12px;
  color: $vk-light-text-muted;
}

.select-card,
.info-card,
.panel-card,
.done-card,
.empty-card {
  margin-top: 20px;
  padding: 20px;
  background: $vk-light-surface;
  border: 1px solid $vk-light-border;
  border-radius: 12px;
}

.select-card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.select-card__body {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 14px;
}

.select-card__empty {
  font-size: 12px;
  color: $vk-light-text-muted;
}

.work-select {
  width: 360px;

  @include vk-mobile {
    width: 100%;
  }
}

.info-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.info-card__title {
  font-size: 16px;
  font-weight: 600;
  color: #151c28;
}

.info-card__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
  font-size: 12px;
  color: $vk-light-text-muted;
}

.info-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.panel-card {
  padding: 20px;
}

.done-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  background: #f4faf5;
  border-color: #cfe8d4;
}

.done-card__text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: #3c5a45;

  strong {
    font-size: 14px;
    color: #1f7a3f;
  }
}

.done-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.empty-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.empty-card__title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2733;
}

.empty-card__desc {
  font-size: 13px;
  line-height: 1.7;
  color: $vk-light-text-muted;
}
</style>
