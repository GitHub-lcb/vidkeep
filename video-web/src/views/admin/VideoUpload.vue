<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import QIcon from '@/components/QIcon.vue'
import UploadPanel from '@/components/UploadPanel.vue'
import { createAdminVideo, getAdminVideo, listAdminVideos } from '@/api/video'
import { listCategories } from '@/api/category'
import { isBizError } from '@/api/request'
import { formatDateTime } from '@/utils/format'
import type { CategoryItem } from '@/types/category'
import type { VideoAdminDetail, VideoAdminItem, VideoSaveRequest } from '@/types/video'

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

/* ---------- 在线上传页直接新建作品（避免「先去别处建壳」的割裂） ---------- */
const createDialogVisible = ref(false)
const createSubmitting = ref(false)
const createError = ref('')
const createForm = reactive<VideoSaveRequest>({
  title: '',
  description: '',
  categoryId: null,
})
const categories = ref<CategoryItem[]>([])

async function openCreateDialog(): Promise<void> {
  createForm.title = ''
  createForm.description = ''
  createForm.categoryId = null
  createError.value = ''
  createDialogVisible.value = true
  if (categories.value.length === 0) {
    try {
      categories.value = await listCategories()
    } catch {
      categories.value = []
    }
  }
}

async function submitCreateWork(): Promise<void> {
  const title = (createForm.title ?? '').trim()
  if (title.length === 0) {
    createError.value = '请输入作品标题'
    return
  }
  if (createSubmitting.value) return
  createSubmitting.value = true
  createError.value = ''
  try {
    const newVideoId = await createAdminVideo({
      title,
      description: (createForm.description ?? '').trim() || null,
      categoryId: createForm.categoryId ?? null,
    })
    createDialogVisible.value = false
    ElMessage.success('作品已创建，接下来选择视频文件上传')
    await loadWorks()
    // watch(selectedVideoId) 会同步地址栏并加载作品详情
    selectedVideoId.value = newVideoId
  } catch (error) {
    createError.value = errorText(error, '创建作品失败')
  } finally {
    createSubmitting.value = false
  }
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
  <div class="q-page q-fade-in">
    <header class="q-page-head">
      <div class="q-page-head__main">
        <h1 class="q-page-title">视频上传</h1>
        <p class="q-page-desc">
          选择作品后上传视频文件，系统自动分片上传、合并并转码为 HLS；支持秒传与断点续传
        </p>
      </div>
      <div class="q-page-actions">
        <button class="q-btn q-btn--ghost q-btn--sm" type="button" @click="goVideoList">
          <QIcon name="folder" :size="13" />
          作品管理
        </button>
        <button class="q-btn q-btn--primary q-btn--sm" type="button" @click="openCreateDialog">
          <QIcon name="plus" :size="13" />
          新建作品
        </button>
      </div>
    </header>

    <div class="q-stack">
      <!-- 作品选择卡：下拉 200 条 + 当前作品信息 -->
      <section class="q-card q-card--pad work-card" aria-label="选择作品">
        <header class="work-card__head">
          <h2 class="q-section-title">选择作品</h2>
          <span class="q-caption">作品是视频的容器：一个作品可上传多个视频文件，自动成为第 1、2、3… 集</span>
        </header>

        <div class="work-card__body">
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
              :label="`${item.title}（#${item.id}）· ${item.episodeCount} 集`"
              :value="item.id"
            />
          </el-select>

          <button class="q-btn q-btn--outline q-btn--sm" type="button" @click="openCreateDialog">
            <QIcon name="plus" :size="13" />
            新建作品
          </button>
          <button class="q-btn q-btn--ghost q-btn--sm" type="button" @click="goVideoList">
            管理作品
          </button>

          <template v-if="works.length === 0 && !worksLoading">
            <span class="work-card__empty">还没有作品：作品创建后即可为它上传视频</span>
          </template>
        </div>

        <!-- 当前作品信息 -->
        <div v-if="currentVideo !== null" v-loading="detailLoading" class="work-meta">
          <div class="work-meta__main">
            <div class="work-meta__title-row">
              <h3 class="work-meta__title">{{ currentVideo.title }}</h3>
              <span
                class="q-tag"
                :class="currentVideo.status === 1 ? 'q-tag--success' : 'q-tag--info'"
              >
                {{ currentVideo.status === 1 ? '已上线' : '已下线' }}
              </span>
            </div>
            <div class="work-meta__row">
              <span>
                <QIcon name="folder" :size="13" />
                {{ currentVideo.categoryName ?? '未分类' }}
              </span>
              <span>
                <QIcon name="film" :size="13" />
                {{ currentVideo.episodeCount }} 个剧集
              </span>
              <span>
                <QIcon name="clock" :size="13" />
                创建于 {{ formatDateTime(currentVideo.createdAt) }}
              </span>
            </div>
          </div>
          <div class="work-meta__actions">
            <button
              class="q-btn q-btn--outline q-btn--sm"
              type="button"
              :disabled="detailLoading"
              @click="refreshCurrentVideo"
            >
              <QIcon name="refresh" :size="13" />
              刷新集数
            </button>
            <button class="q-btn q-btn--outline q-btn--sm" type="button" @click="goVideoDetail">
              <QIcon name="edit" :size="13" />
              编辑作品
            </button>
          </div>
        </div>
      </section>

      <!-- 上传面板卡：切换作品时通过 key 重新挂载，避免旧任务串状态 -->
      <section v-if="selectedVideoId !== null" class="q-card q-card--pad panel-card" aria-label="上传面板">
        <UploadPanel
          :key="selectedVideoId"
          :video-id="selectedVideoId"
          @completed="handleCompleted"
          @transcode-done="handleTranscodeDone"
        />
      </section>

      <!-- 转码完成后的后续动作 -->
      <section v-if="transcodeDone" class="done-card" role="status">
        <div class="done-card__main">
          <span class="done-card__icon"><QIcon name="check" :size="15" /></span>
          <div class="done-card__text">
            <strong>转码完成</strong>
            <span>剧集已可播放，可前往视频详情继续上传下一集，或直接打开前台播放页预览。</span>
          </div>
        </div>
        <div class="done-card__actions">
          <button class="q-btn q-btn--outline q-btn--sm" type="button" @click="goVideoDetail">
            前往视频详情
          </button>
          <button class="q-btn q-btn--primary q-btn--sm" type="button" @click="openPlayer">
            <QIcon name="play" :size="13" />
            前台播放
          </button>
        </div>
      </section>

      <!-- 未选择作品时的空态 -->
      <section
        v-else-if="selectedVideoId === null && works.length === 0 && !worksLoading"
        class="q-card empty-card"
      >
        <div class="q-empty">
          <div class="q-empty__stack">
            <span class="q-empty__face"><QIcon name="upload" :size="18" /></span>
          </div>
          <p class="q-empty__title">还没有可上传的作品</p>
          <p class="q-empty__desc">
            按「先建作品，再传文件」两步式操作：先创建作品壳，再回到本页选择作品上传视频。
          </p>
          <div class="q-empty__actions">
            <button class="q-btn q-btn--primary q-btn--sm" type="button" @click="openCreateDialog">
              <QIcon name="plus" :size="13" />
              新建作品
            </button>
            <button class="q-btn q-btn--outline q-btn--sm" type="button" @click="goVideoList">
              <QIcon name="folder" :size="13" />
              作品管理
            </button>
          </div>
        </div>
      </section>
    </div>

    <!-- 新建作品弹窗：上传页内直接建壳，创建后自动选中 -->
    <el-dialog
      v-model="createDialogVisible"
      title="新建作品"
      width="480px"
      :close-on-click-modal="false"
    >
      <div class="create-form">
        <div class="q-field">
          <label class="q-field__label" for="create-work-title">作品标题</label>
          <el-input
            id="create-work-title"
            v-model="createForm.title"
            maxlength="255"
            placeholder="例如：我的旅行记录"
            @keyup.enter="submitCreateWork"
          />
        </div>
        <div class="q-field">
          <label class="q-field__label" for="create-work-desc">简介（选填）</label>
          <el-input
            id="create-work-desc"
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="简要介绍作品内容"
          />
        </div>
        <div class="q-field">
          <label class="q-field__label">分类（选填）</label>
          <el-select
            v-model="createForm.categoryId"
            placeholder="选择作品分类"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="category in categories"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </div>
        <p v-if="createError.length > 0" class="create-form__error" role="alert">{{ createError }}</p>
      </div>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreateWork">
          创建并选择
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

/* ---------------- 作品选择卡 ---------------- */
.work-card__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: baseline;
  justify-content: space-between;
}

.work-card__body {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-top: 14px;
}

.work-select {
  width: 360px;
}

.work-card__empty {
  font-size: var(--q-fs-12);
  color: var(--q-text-tertiary);
}

.work-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px solid var(--q-border-quaternary);
}

.work-meta__main {
  min-width: 0;
}

.work-meta__title-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.work-meta__title {
  font-size: var(--q-fs-16);
  font-weight: 600;
  line-height: var(--q-lh-22);
  color: var(--q-text);
}

.work-meta__row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-top: 6px;
  font-size: var(--q-fs-12);
  color: var(--q-text-tertiary);

  > span {
    display: inline-flex;
    gap: 4px;
    align-items: center;
  }

  svg {
    color: var(--q-text-quaternary);
  }
}

.work-meta__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* ---------------- 上传面板卡 ---------------- */
.panel-card {
  padding: 16px;
}

/* ---------------- 转码完成提示 ---------------- */
.done-card {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--q-success-bg);
  border-radius: var(--q-r-surface);
  box-shadow: 0 0 0 1px var(--q-success-border) inset;
}

.done-card__main {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.done-card__icon {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--q-success);
  background: var(--q-bg-container);
  border-radius: 50%;
  box-shadow: 0 0 0 1px var(--q-success-border) inset;
}

.done-card__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: var(--q-fs-12);
  line-height: var(--q-lh-18);
  color: var(--q-text-secondary);

  strong {
    font-size: var(--q-fs-13);
    font-weight: 600;
    color: var(--q-success);
  }
}

.done-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* ---------------- 空态 ---------------- */
.empty-card {
  overflow: hidden;
}

/* ---------------- 新建作品弹窗 ---------------- */
.create-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.create-form__error {
  font-size: var(--q-fs-12);
  color: var(--q-error);
}

/* ---------------- 响应式 ---------------- */
@include q-mobile {
  .work-select {
    width: 100%;
  }

  .work-meta__actions,
  .done-card__actions {
    width: 100%;
  }

  .panel-card {
    padding: 12px;
  }
}
</style>
