<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createAdminVideo, deleteAdminVideo, listAdminVideos, updateAdminVideoStatus } from '@/api/video'
import { listCategories } from '@/api/category'
import { isBizError } from '@/api/request'
import { formatDateTime } from '@/utils/format'
import type { CategoryItem } from '@/types/category'
import type { VideoAdminItem } from '@/types/video'

/** 下拉筛选的“全部”占位值（分类 id / 状态均从 1 开始，-1 不会与真实值冲突） */
const ALL_OPTION = -1

interface SearchState {
  title: string
  status: number
  categoryId: number
  page: number
  size: number
}

const router = useRouter()

const categories = ref<CategoryItem[]>([])
const list = ref<VideoAdminItem[]>([])
const total = ref(0)
const loading = ref(false)
const statusUpdatingId = ref<number | null>(null)

const search = reactive<SearchState>({
  title: '',
  status: ALL_OPTION,
  categoryId: ALL_OPTION,
  page: 1,
  size: 10,
})

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

async function loadVideos(): Promise<void> {
  loading.value = true
  try {
    const result = await listAdminVideos({
      page: search.page,
      size: search.size,
      title: search.title.trim().length > 0 ? search.title.trim() : undefined,
      status: search.status === ALL_OPTION ? undefined : search.status,
      categoryId: search.categoryId === ALL_OPTION ? undefined : search.categoryId,
    })
    list.value = result.records
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorText(error, '加载视频列表失败'))
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  search.page = 1
  void loadVideos()
}

function handleReset(): void {
  search.title = ''
  search.status = ALL_OPTION
  search.categoryId = ALL_OPTION
  search.page = 1
  void loadVideos()
}

function handlePageChange(page: number): void {
  search.page = page
  void loadVideos()
}

function handleSizeChange(size: number): void {
  search.size = size
  search.page = 1
  void loadVideos()
}

/** 上下架：switch 的 model-value 绑定 row.status，切换时按当前值取反 */
async function handleToggleStatus(row: VideoAdminItem): Promise<void> {
  if (statusUpdatingId.value !== null) return
  const nextStatus: 0 | 1 = row.status === 1 ? 0 : 1
  statusUpdatingId.value = row.id
  try {
    await updateAdminVideoStatus(row.id, { status: nextStatus })
    row.status = nextStatus
    ElMessage.success(nextStatus === 1 ? '作品已上线' : '作品已下线')
  } catch (error) {
    ElMessage.error(errorText(error, '更新上下架状态失败'))
  } finally {
    statusUpdatingId.value = null
  }
}

async function handleDelete(row: VideoAdminItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除会同时移除该作品的 ${row.episodeCount} 个剧集、原片与 HLS 产物，且不可恢复。确定删除「${row.title}」吗？`,
      '删除作品',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    // 用户取消或关闭弹窗，无需提示
    return
  }

  try {
    await deleteAdminVideo(row.id)
    ElMessage.success('作品已删除')
    // 删除当前页最后一条时回退一页，避免出现空页
    if (list.value.length === 1 && search.page > 1) search.page -= 1
    await loadVideos()
  } catch (error) {
    ElMessage.error(errorText(error, '删除作品失败'))
  }
}

function goEdit(row: VideoAdminItem): void {
  void router.push(`/admin/videos/${row.id}`)
}

function goUpload(row: VideoAdminItem): void {
  void router.push({ path: '/admin/upload', query: { videoId: String(row.id) } })
}

/* ---------- 新建作品弹窗：先建作品壳，再进编辑页上传视频（文档 7.4.2 两步式） ---------- */

const createDialogVisible = ref(false)
const saving = ref(false)
const createFormRef = ref<FormInstance>()

interface CreateFormState {
  title: string
  description: string
  categoryId: number | null
}

const createForm = reactive<CreateFormState>({
  title: '',
  description: '',
  categoryId: null,
})

const createRules: FormRules = {
  title: [
    { required: true, message: '请输入作品标题', trigger: 'blur' },
    { max: 100, message: '标题不能超过 100 个字符', trigger: 'blur' },
  ],
}

function openCreateDialog(): void {
  createForm.title = ''
  createForm.description = ''
  createForm.categoryId = null
  createDialogVisible.value = true
  // 弹窗动画结束后再清理校验态，避免打开瞬间闪现红色提示
  window.setTimeout(() => createFormRef.value?.clearValidate(), 0)
}

async function submitCreate(): Promise<void> {
  if (saving.value || createFormRef.value === undefined) return
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const description = createForm.description.trim()
    const newVideoId = await createAdminVideo({
      title: createForm.title.trim(),
      description: description.length > 0 ? description : null,
      // 清空分类时 el-select 可能给出 ''/undefined，统一转 null
      categoryId: typeof createForm.categoryId === 'number' ? createForm.categoryId : null,
    })
    createDialogVisible.value = false
    ElMessage.success('作品已创建，请继续上传视频文件')
    await router.push(`/admin/videos/${newVideoId}`)
  } catch (error) {
    ElMessage.error(errorText(error, '创建作品失败'))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadCategories()
  void loadVideos()
})
</script>

<template>
  <div class="vk-page video-list">
    <header class="page-head">
      <div>
        <h1 class="vk-page__title">视频管理</h1>
        <p class="vk-page__subtitle">管理作品基础信息、上下架状态，并为作品上传剧集文件</p>
      </div>
      <el-button type="primary" @click="openCreateDialog">新建作品</el-button>
    </header>

    <section class="filter-card">
      <el-form class="filter-form" @submit.prevent="handleSearch">
        <el-form-item label="标题">
          <el-input
            v-model="search.title"
            class="filter-input"
            placeholder="按标题模糊搜索"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="search.status" class="filter-select">
            <el-option label="全部" :value="ALL_OPTION" />
            <el-option label="已上线" :value="1" />
            <el-option label="已下线" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="search.categoryId" class="filter-select">
            <el-option label="全部分类" :value="ALL_OPTION" />
            <el-option
              v-for="category in categories"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="table-card">
      <el-table
        v-loading="loading"
        :data="list"
        row-key="id"
        empty-text="暂无视频作品，点击右上角「新建作品」开始"
      >
        <el-table-column label="封面" width="96">
          <template #default="{ row }">
            <el-image
              v-if="row.coverUrl"
              class="video-cover"
              :src="row.coverUrl"
              fit="cover"
              :preview-src-list="[row.coverUrl]"
              preview-teleported
            />
            <div v-else class="video-cover video-cover--empty">
              <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
                <path
                  d="M4 6h16v12H4zM4 10h16M9 6v4M15 6v4"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.4"
                  stroke-linejoin="round"
                />
              </svg>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="标题" min-width="220">
          <template #default="{ row }">
            <div class="video-title">
              <span class="video-title__main">{{ row.title }}</span>
              <span v-if="row.description" class="video-title__desc">{{ row.description }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="分类" width="110">
          <template #default="{ row }">
            <span v-if="row.categoryName">{{ row.categoryName }}</span>
            <span v-else class="vk-muted">未分类</span>
          </template>
        </el-table-column>

        <el-table-column prop="episodeCount" label="集数" width="80" align="center" />
        <el-table-column prop="viewCount" label="播放量" width="96" align="right" />

        <el-table-column label="状态" width="96" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              inline-prompt
              active-text="上线"
              inactive-text="下线"
              :width="56"
              :loading="statusUpdatingId === row.id"
              :disabled="statusUpdatingId !== null"
              @change="handleToggleStatus(row)"
            />
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">
            <span class="video-time">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="208" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button text type="primary" size="small" @click="goEdit(row)">编辑</el-button>
              <el-button text type="primary" size="small" @click="goUpload(row)">上传</el-button>
              <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          :current-page="search.page"
          :page-size="search.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog
      v-model="createDialogVisible"
      title="新建作品"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="64px"
        @submit.prevent="submitCreate"
      >
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="createForm.title"
            placeholder="请输入作品标题"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="简介" prop="description">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="选填，简要介绍作品内容"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="createForm.categoryId" placeholder="选填，选择作品分类" clearable>
            <el-option
              v-for="category in categories"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建并去上传</el-button>
      </template>
    </el-dialog>
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

.filter-card {
  margin-top: 20px;
  padding: 18px 20px 2px;
  background: $vk-light-surface;
  border: 1px solid $vk-light-border;
  border-radius: 12px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0 8px;

  :deep(.el-form-item) {
    margin-bottom: 16px;
  }

  :deep(.el-form-item__label) {
    font-size: 13px;
    color: $vk-light-text-muted;
  }

  @include vk-mobile {
    flex-direction: column;

    :deep(.el-form-item) {
      margin-right: 0;
    }
  }
}

.filter-input {
  width: 220px;

  @include vk-mobile {
    width: 100%;
  }
}

.filter-select {
  width: 140px;

  @include vk-mobile {
    width: 100%;
  }
}

.filter-actions {
  margin-left: auto;

  @include vk-mobile {
    margin-left: 0;
  }
}

.table-card {
  margin-top: 16px;
  padding: 4px 20px 16px;
  background: $vk-light-surface;
  border: 1px solid $vk-light-border;
  border-radius: 12px;

  @include vk-mobile {
    padding: 4px 12px 14px;
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
}

.video-cover {
  display: block;
  width: 72px;
  height: 42px;
  overflow: hidden;
  border-radius: 6px;
}

.video-cover--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a3adbb;
  background: #f0f2f6;
}

.video-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.video-title__main {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  color: #1f2733;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.video-title__desc {
  display: -webkit-box;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.4;
  color: $vk-light-text-muted;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.video-time {
  font-size: 12px;
  color: #5b6678;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}
</style>
