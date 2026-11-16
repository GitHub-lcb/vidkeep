<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { deleteAdminEpisode, getAdminVideo, updateAdminVideo } from '@/api/video'
import { listCategories } from '@/api/category'
import { isBizError } from '@/api/request'
import { formatDateTime, formatDuration, formatFileSize } from '@/utils/format'
import type { CategoryItem } from '@/types/category'
import type { TranscodeStatus } from '@/types/transcode'
import type { EpisodeItem, VideoAdminDetail, VideoSaveRequest } from '@/types/video'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

/** 转码状态 → 文案 / el-tag 类型（与上传组件保持一致） */
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
  PROCESSING: 'primary',
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
  <div class="vk-page video-edit">
    <header class="page-head">
      <div class="page-head__left">
        <el-button text class="page-head__back" @click="goBack">← 返回列表</el-button>
        <div>
          <h1 class="vk-page__title">
            {{ detail?.title ?? '作品详情' }}
            <el-tag
              v-if="detail !== null"
              class="status-tag"
              :type="detail.status === 1 ? 'success' : 'info'"
              effect="plain"
            >
              {{ detail.status === 1 ? '已上线' : '已下线' }}
            </el-tag>
          </h1>
          <p class="vk-page__subtitle">
            作品 ID {{ videoId }}
            <template v-if="detail !== null">
              · {{ detail.categoryName ?? '未分类' }} · {{ detail.episodeCount }} 个剧集 · 创建于
              {{ formatDateTime(detail.createdAt) }}
            </template>
          </p>
        </div>
      </div>
      <div class="page-head__actions">
        <el-button :loading="loading" @click="loadVideoDetail(true)">刷新</el-button>
        <el-button type="primary" @click="goUpload">上传新视频</el-button>
      </div>
    </header>

    <section v-loading="loading" class="edit-card">
      <h2 class="section-title">作品信息</h2>
      <el-form
        ref="formRef"
        class="edit-form"
        :model="form"
        :rules="rules"
        label-width="72px"
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
              <span class="cover-preview__hint">封面预览</span>
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

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存修改</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="episode-card">
      <div class="episode-card__head">
        <div>
          <h2 class="section-title">剧集管理</h2>
          <p class="section-subtitle">
            每个上传的视频文件自动成为一集；转码完成后可预览播放
          </p>
        </div>
        <el-button type="primary" plain @click="goUpload">上传新视频</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="detail?.episodes ?? []"
        row-key="id"
        empty-text="暂无剧集，点击「上传新视频」添加第一集"
      >
        <el-table-column label="集数" width="80" align="center">
          <template #default="{ row }">第 {{ row.episodeNo }} 集</template>
        </el-table-column>

        <el-table-column label="标题" min-width="180">
          <template #default="{ row }">
            <span v-if="row.title">{{ row.title }}</span>
            <span v-else class="vk-muted">第 {{ row.episodeNo }} 集</span>
          </template>
        </el-table-column>

        <el-table-column label="时长" width="100" align="right">
          <template #default="{ row }">
            {{ row.duration !== null ? formatDuration(row.duration) : '--' }}
          </template>
        </el-table-column>

        <el-table-column label="大小" width="100" align="right">
          <template #default="{ row }">
            {{ row.fileSize !== null ? formatFileSize(row.fileSize) : '--' }}
          </template>
        </el-table-column>

        <el-table-column label="分辨率" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.width !== null && row.height !== null">
              {{ row.width }}×{{ row.height }}
            </span>
            <span v-else class="vk-muted">--</span>
          </template>
        </el-table-column>

        <el-table-column label="转码状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="transcodeTagType(row.transcodeStatus)" effect="plain">
              {{ transcodeStatusText(row.transcodeStatus) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">
            <span class="episode-time">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              text
              type="primary"
              size="small"
              :disabled="row.transcodeStatus !== 'DONE'"
              @click="previewEpisode(row)"
            >
              播放预览
            </el-button>
            <el-button
              text
              type="danger"
              size="small"
              :loading="deletingEpisodeId === row.id"
              @click="handleDeleteEpisode(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
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

.page-head__left {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.page-head__back {
  flex: none;
  margin-top: -4px;
  font-size: 13px;
}

.page-head__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.status-tag {
  margin-left: 8px;
  vertical-align: middle;
}

.edit-card,
.episode-card {
  margin-top: 20px;
  padding: 20px;
  background: $vk-light-surface;
  border: 1px solid $vk-light-border;
  border-radius: 12px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #151c28;
}

.section-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: $vk-light-text-muted;
}

.edit-form {
  max-width: 720px;
  margin-top: 18px;

  :deep(.el-form-item__label) {
    font-size: 13px;
    color: $vk-light-text-muted;
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
  align-items: center;
  gap: 10px;
}

.cover-preview__img {
  width: 144px;
  height: 81px;
  overflow: hidden;
  border: 1px solid $vk-light-border;
  border-radius: 8px;
}

.cover-preview__hint {
  font-size: 12px;
  color: $vk-light-text-muted;
}

.episode-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.episode-time {
  font-size: 12px;
  color: #5b6678;
}

:deep(.el-table) {
  --el-table-border-color: #f0f2f6;
  --el-table-header-bg-color: #fafbfc;
  --el-table-header-text-color: #5b6678;
}

:deep(.el-table th.el-table__cell) {
  font-size: 13px;
  font-weight: 600;
}
</style>
