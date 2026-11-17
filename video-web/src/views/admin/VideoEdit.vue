<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import QIcon from '@/components/QIcon.vue'
import { deleteAdminEpisode, getAdminVideo, updateAdminVideo } from '@/api/video'
import { listCategories } from '@/api/category'
import { isBizError } from '@/api/request'
import { formatDateTime, formatDuration, formatFileSize } from '@/utils/format'
import type { CategoryItem } from '@/types/category'
import type { TranscodeStatus } from '@/types/transcode'
import type { EpisodeItem, VideoAdminDetail, VideoSaveRequest } from '@/types/video'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

/** 转码状态 → 文案 / 标签语义色（与上传组件保持一致，DONE=success、PROCESSING/PENDING=warning） */
const TRANSCODE_STATUS_TEXT: Record<TranscodeStatus, string> = {
  NONE: '未开始',
  PENDING: '排队中',
  PROCESSING: '转码中',
  DONE: '已完成',
  FAILED: '失败',
}
const TRANSCODE_STATUS_TYPE: Record<TranscodeStatus, TagType> = {
  NONE: 'info',
  PENDING: 'warning',
  PROCESSING: 'warning',
  DONE: 'success',
  FAILED: 'danger',
}

/** 模板里 el-table 插槽的 row 为 any，用强类型辅助函数收窄后再索引映射表，避免 TS7053 */
function transcodeTagType(status: TranscodeStatus): TagType {
  return TRANSCODE_STATUS_TYPE[status]
}

function transcodeStatusText(status: TranscodeStatus): string {
  return TRANSCODE_STATUS_TEXT[status]
}

const route = useRoute()
const router = useRouter()

function parseVideoId(): number {
  const raw = route.params.id
  const value = Array.isArray(raw) ? raw[0] : raw
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : Number.NaN
}

const videoId = ref(parseVideoId())
const detail = ref<VideoAdminDetail | null>(null)
const categories = ref<CategoryItem[]>([])
const loading = ref(false)
const saving = ref(false)
const deletingEpisodeId = ref<number | null>(null)

const formRef = ref<FormInstance>()

interface EditFormState {
  title: string
  description: string
  coverUrl: string
  categoryId: number | null
}

const form = reactive<EditFormState>({
  title: '',
  description: '',
  coverUrl: '',
  categoryId: null,
})

const rules: FormRules = {
  title: [
    { required: true, message: '请输入作品标题', trigger: 'blur' },
    { max: 100, message: '标题不能超过 100 个字符', trigger: 'blur' },
  ],
}

/** 封面预览地址（去掉首尾空白，空串表示未设置） */
const coverPreview = computed<string>(() => form.coverUrl.trim())

function errorText(error: unknown, fallback: string): string {
  return isBizError(error) ? error.message : fallback
}

async function loadCategories(): Promise<void> {
  try {
    categories.value = await listCategories()
  } catch (error) {
    ElMessage.error(errorText(error, '加载分类失败'))
  }
}

/**
 * 加载作品详情。
 * syncForm=true 时把详情回填到表单（首次进入 / 切换作品）；
 * 删除剧集后只刷新剧集列表，避免覆盖用户正在编辑的表单内容。
 */
async function loadVideoDetail(syncForm: boolean): Promise<void> {
  if (!Number.isInteger(videoId.value)) {
    ElMessage.error('无效的作品 ID')
    await router.replace('/admin/videos')
    return
  }
  loading.value = true
  try {
    const data = await getAdminVideo(videoId.value)
    detail.value = data
    if (syncForm) {
      form.title = data.title
      form.description = data.description ?? ''
      form.coverUrl = data.coverUrl ?? ''
      form.categoryId = data.categoryId
    }
  } catch (error) {
    ElMessage.error(errorText(error, '加载作品详情失败'))
  } finally {
    loading.value = false
  }
}

async function handleSave(): Promise<void> {
  if (saving.value || formRef.value === undefined) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const description = form.description.trim()
  const coverUrl = form.coverUrl.trim()
  const payload: VideoSaveRequest = {
    title: form.title.trim(),
    description: description.length > 0 ? description : null,
    coverUrl: coverUrl.length > 0 ? coverUrl : null,
    // 清空分类时 el-select 可能给出 ''/undefined，统一转 null，确保后端能真正移除分类
    categoryId: typeof form.categoryId === 'number' ? form.categoryId : null,
  }

  saving.value = true
  try {
    await updateAdminVideo(videoId.value, payload)
    ElMessage.success('作品信息已保存')
    await loadVideoDetail(true)
  } catch (error) {
    ElMessage.error(errorText(error, '保存作品信息失败'))
  } finally {
    saving.value = false
  }
}

async function handleDeleteEpisode(episode: EpisodeItem): Promise<void> {
  const episodeLabel = episode.title ?? `第 ${episode.episodeNo} 集`
  try {
    await ElMessageBox.confirm(
      `删除「${episodeLabel}」会同时删除该集原片与 HLS 产物，且不可恢复。确定删除吗？`,
      '删除剧集',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }

  deletingEpisodeId.value = episode.id
  try {
    await deleteAdminEpisode(episode.id)
    ElMessage.success('剧集已删除')
    await loadVideoDetail(false)
  } catch (error) {
    ElMessage.error(errorText(error, '删除剧集失败'))
  } finally {
    deletingEpisodeId.value = null
  }
}

/** 播放预览：仅转码完成的剧集可打开前台播放页 */
function previewEpisode(episode: EpisodeItem): void {
  if (episode.transcodeStatus !== 'DONE') {
    ElMessage.warning('该剧集尚未转码完成，暂不能播放')
    return
  }
  window.open(`/play/${videoId.value}/${episode.id}`, '_blank', 'noopener,noreferrer')
}

function goUpload(): void {
  void router.push({ path: '/admin/upload', query: { videoId: String(videoId.value) } })
}

function goBack(): void {
  void router.push('/admin/videos')
}

// 同一组件内切换到另一个作品 id 时重新加载
watch(
  () => route.params.id,
  () => {
    videoId.value = parseVideoId()
    void loadVideoDetail(true)
  },
)

onMounted(() => {
  void loadCategories()
  void loadVideoDetail(true)
})
</script>

<template>
  <div class="q-page q-fade-in">
    <header class="q-page-head">
      <div class="q-page-head__main">
        <div class="page-title-row">
          <h1 class="q-page-title">{{ detail?.title ?? '作品详情' }}</h1>
          <span
            v-if="detail !== null"
            class="q-tag"
            :class="detail.status === 1 ? 'q-tag--success' : 'q-tag--info'"
          >
            {{ detail.status === 1 ? '已上线' : '已下线' }}
          </span>
        </div>
        <p class="q-page-desc">
          作品 ID {{ videoId }}
          <template v-if="detail !== null">
            · {{ detail.categoryName ?? '未分类' }} · {{ detail.episodeCount }} 个剧集 · 创建于
            {{ formatDateTime(detail.createdAt) }}
          </template>
        </p>
      </div>
      <div class="q-page-actions">
        <button class="q-btn q-btn--ghost q-btn--sm" type="button" @click="goBack">
          <QIcon name="arrow-left" :size="13" />
          返回列表
        </button>
        <button
          class="q-btn q-btn--outline q-btn--sm"
          type="button"
          :disabled="loading"
          @click="loadVideoDetail(true)"
        >
          <QIcon name="refresh" :size="13" />
          刷新
        </button>
        <button class="q-btn q-btn--primary q-btn--sm" type="button" @click="goUpload">
          <QIcon name="upload" :size="14" />
          上传新视频
        </button>
      </div>
    </header>

    <div class="edit-grid">
      <!-- 左：作品信息表单 -->
      <section class="q-card q-card--pad edit-card" aria-label="作品信息">
        <header class="card-head">
          <h2 class="q-section-title">作品信息</h2>
          <p class="q-caption">标题与分类会展示在前台列表与筛选器</p>
        </header>

        <el-form
          ref="formRef"
          class="edit-form"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="handleSave"
        >
          <el-form-item label="标题" prop="title">
            <el-input
              v-model="form.title"
              placeholder="请输入作品标题"
              maxlength="100"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="分类" prop="categoryId">
            <el-select v-model="form.categoryId" placeholder="请选择分类（可不选）" clearable>
              <el-option
                v-for="category in categories"
                :key="category.id"
                :label="category.name"
                :value="category.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="封面 URL" prop="coverUrl">
            <div class="cover-field">
              <el-input v-model="form.coverUrl" placeholder="选填，封面图片地址，如 /covers/10.jpg" />
              <div v-if="coverPreview.length > 0" class="cover-preview">
                <el-image class="cover-preview__img" :src="coverPreview" fit="cover" />
                <span class="cover-preview__meta">
                  <span class="q-caption">封面预览</span>
                  <span class="q-caption">建议 16:9，前台列表按该比例裁剪</span>
                </span>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="简介" prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="4"
              placeholder="选填，简要介绍作品内容"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>

          <div class="edit-form__actions">
            <button
              class="q-btn q-btn--primary"
              type="button"
              :disabled="saving"
              @click="handleSave"
            >
              {{ saving ? '保存中…' : '保存修改' }}
            </button>
            <button class="q-btn q-btn--outline" type="button" @click="goUpload">
              <QIcon name="upload" :size="14" />
              上传新视频
            </button>
            <button class="q-btn q-btn--ghost" type="button" @click="goBack">
              <QIcon name="arrow-left" :size="13" />
              返回列表
            </button>
          </div>
        </el-form>
      </section>

      <!-- 右：剧集表格 -->
      <section class="q-card episode-card" aria-label="剧集管理">
        <header class="episode-card__head">
          <div class="episode-card__main">
            <h2 class="q-section-title">剧集管理</h2>
            <p class="q-caption">每个上传的视频文件自动成为一集；转码完成后可预览播放</p>
          </div>
          <button class="q-btn q-btn--outline q-btn--sm" type="button" @click="goUpload">
            <QIcon name="upload" :size="13" />
            上传新视频
          </button>
        </header>

        <el-table v-loading="loading" :data="detail?.episodes ?? []" row-key="id">
          <el-table-column label="集数" width="76" align="center">
            <template #default="{ row }">
              <span class="cell-episode">第 {{ row.episodeNo }} 集</span>
            </template>
          </el-table-column>

          <el-table-column label="标题" min-width="150">
            <template #default="{ row }">
              <span v-if="row.title">{{ row.title }}</span>
              <span v-else class="cell-muted">第 {{ row.episodeNo }} 集</span>
            </template>
          </el-table-column>

          <el-table-column label="时长" width="92" align="right">
            <template #default="{ row }">
              <span class="cell-time">
                {{ row.duration !== null ? formatDuration(row.duration) : '--' }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="大小" width="92" align="right">
            <template #default="{ row }">
              <span class="cell-time">
                {{ row.fileSize !== null ? formatFileSize(row.fileSize) : '--' }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="分辨率" width="104" align="center">
            <template #default="{ row }">
              <span v-if="row.width !== null && row.height !== null" class="cell-mono">
                {{ row.width }}×{{ row.height }}
              </span>
              <span v-else class="cell-muted">--</span>
            </template>
          </el-table-column>

          <el-table-column label="转码状态" width="108" align="center">
            <template #default="{ row }">
              <span
                class="q-tag"
                :class="[
                  `q-tag--${transcodeTagType(row.transcodeStatus)}`,
                  { 'q-tag--dot': row.transcodeStatus === 'PENDING' || row.transcodeStatus === 'PROCESSING' },
                ]"
              >
                {{ transcodeStatusText(row.transcodeStatus) }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="创建时间" width="146">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.createdAt) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="148" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <button
                  class="q-btn q-btn--text q-btn--sm"
                  type="button"
                  :disabled="row.transcodeStatus !== 'DONE'"
                  @click="previewEpisode(row)"
                >
                  <QIcon name="eye" :size="13" />
                  预览
                </button>
                <button
                  class="q-btn q-btn--text q-btn--sm row-actions__danger"
                  type="button"
                  :disabled="deletingEpisodeId === row.id"
                  @click="handleDeleteEpisode(row)"
                >
                  <QIcon name="trash" :size="13" />
                  {{ deletingEpisodeId === row.id ? '删除中…' : '删除' }}
                </button>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <div class="q-empty">
              <div class="q-empty__stack">
                <span class="q-empty__face"><QIcon name="film" :size="18" /></span>
              </div>
              <p class="q-empty__title">还没有剧集</p>
              <p class="q-empty__desc">
                点击「上传新视频」添加第一集；上传后系统会自动分片上传、合并并转码为 HLS。
              </p>
              <div class="q-empty__actions">
                <button class="q-btn q-btn--outline q-btn--sm" type="button" @click="goUpload">
                  <QIcon name="upload" :size="13" />
                  上传新视频
                </button>
              </div>
            </div>
          </template>
        </el-table>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.page-title-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

/* ---------------- 两栏布局 ---------------- */
.edit-grid {
  display: grid;
  grid-template-columns: minmax(300px, 0.8fr) minmax(0, 1.55fr);
  gap: 16px;
  align-items: start;
  margin-top: 16px;
}

/* ---------------- 左：信息卡 ---------------- */
.card-head {
  padding-bottom: 12px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--q-border-quaternary);
}

.card-head .q-caption {
  margin-top: 3px;
}

.edit-form {
  :deep(.el-form-item:last-of-type) {
    margin-bottom: 8px;
  }

  :deep(.el-select) {
    width: 100%;
  }
}

.cover-field {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.cover-preview {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.cover-preview__img {
  flex: none;
  width: 144px;
  height: 81px;
  overflow: hidden;
  background: var(--q-fill-tertiary);
  border-radius: var(--q-r-lg);
  box-shadow: 0 0 0 1px var(--q-border-quaternary) inset;
}

.cover-preview__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-top: 2px;
}

.edit-form__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--q-border-quaternary);
}

/* ---------------- 右：剧集卡 ---------------- */
.episode-card {
  padding: 4px 16px 12px;
}

.episode-card__head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 12px 0 10px;
}

.episode-card__main .q-caption {
  margin-top: 3px;
}

.cell-episode {
  font-size: var(--q-fs-12);
  color: var(--q-text-secondary);
  font-variant-numeric: tabular-nums;
}

.cell-muted {
  font-size: var(--q-fs-12);
  color: var(--q-text-tertiary);
}

.cell-time {
  font-size: var(--q-fs-12);
  color: var(--q-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.cell-mono {
  font-family: var(--q-font-mono);
  font-size: var(--q-fs-11);
  color: var(--q-text-secondary);
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.row-actions__danger {
  color: var(--q-error);
}

.row-actions__danger:not(:disabled):hover {
  color: var(--q-error-hover);
}

/* ---------------- 响应式 ---------------- */
@include q-tablet {
  .edit-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@include q-mobile {
  .episode-card {
    padding: 4px 12px 12px;
  }

  .edit-form__actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
