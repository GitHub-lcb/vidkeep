<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import QIcon from '@/components/QIcon.vue'
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
  <div class="q-page q-fade-in">
    <header class="q-page-head">
      <div class="q-page-head__main">
        <h1 class="q-page-title">作品管理</h1>
        <p class="q-page-desc">作品是视频的容器：一个作品可挂多个剧集文件，支持上下架与分类</p>
      </div>
      <div class="q-page-actions">
        <button
          class="q-btn q-btn--outline q-btn--sm"
          type="button"
          :disabled="loading"
          @click="loadVideos"
        >
          <QIcon name="refresh" :size="13" />
          刷新
        </button>
        <button class="q-btn q-btn--primary q-btn--sm" type="button" @click="openCreateDialog">
          <QIcon name="plus" :size="14" />
          新建作品
        </button>
      </div>
    </header>

    <div class="q-stack">
      <!-- 筛选卡：单行 28px 紧凑控件 -->
      <section class="q-card q-card--pad" aria-label="筛选条件">
        <el-form class="filter-form" @submit.prevent="handleSearch">
          <el-form-item label="标题" class="filter-item">
            <el-input
              v-model="search.title"
              class="filter-input"
              placeholder="按标题模糊搜索"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>

          <el-form-item label="状态" class="filter-item">
            <el-select v-model="search.status" class="filter-select">
              <el-option label="全部" :value="ALL_OPTION" />
              <el-option label="已上线" :value="1" />
              <el-option label="已下线" :value="0" />
            </el-select>
          </el-form-item>

          <el-form-item label="分类" class="filter-item">
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

          <el-form-item class="filter-item filter-actions">
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </section>

      <!-- 表格卡 -->
      <section class="q-card table-card" aria-label="视频列表">
        <el-table v-loading="loading" :data="list" row-key="id">
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
                <QIcon name="film" :size="14" />
              </div>
            </template>
          </el-table-column>

          <el-table-column label="标题" min-width="220">
            <template #default="{ row }">
              <div class="video-cell">
                <span class="video-cell__title">{{ row.title }}</span>
                <span v-if="row.description" class="video-cell__desc">{{ row.description }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="分类" width="110">
            <template #default="{ row }">
              <span v-if="row.categoryName">{{ row.categoryName }}</span>
              <span v-else class="cell-muted">未分类</span>
            </template>
          </el-table-column>

          <el-table-column prop="episodeCount" label="集数" width="80" align="center" />

          <el-table-column prop="viewCount" label="播放量" width="96" align="right">
            <template #default="{ row }">
              <span class="cell-time">{{ row.viewCount }}</span>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="132" align="center">
            <template #default="{ row }">
              <div class="status-cell">
                <span class="q-tag" :class="row.status === 1 ? 'q-tag--success' : 'q-tag--info'">
                  {{ row.status === 1 ? '已上线' : '已下线' }}
                </span>
                <el-switch
                  :model-value="row.status === 1"
                  :loading="statusUpdatingId === row.id"
                  :disabled="statusUpdatingId !== null"
                  @change="handleToggleStatus(row)"
                />
              </div>
            </template>
          </el-table-column>

          <el-table-column label="创建时间" width="150">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.createdAt) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="184" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <button class="q-btn q-btn--text q-btn--sm" type="button" @click="goEdit(row)">
                  <QIcon name="edit" :size="13" />
                  编辑
                </button>
                <button class="q-btn q-btn--text q-btn--sm" type="button" @click="goUpload(row)">
                  <QIcon name="upload" :size="13" />
                  上传
                </button>
                <button
                  class="q-btn q-btn--text q-btn--sm row-actions__danger"
                  type="button"
                  @click="handleDelete(row)"
                >
                  <QIcon name="trash" :size="13" />
                  删除
                </button>
              </div>
            </template>
          </el-table-column>

          <template #empty>
            <div class="q-empty">
              <div class="q-empty__stack">
                <span class="q-empty__face"><QIcon name="film" :size="18" /></span>
              </div>
              <p class="q-empty__title">还没有视频作品</p>
              <p class="q-empty__desc">
                点击右上角「新建作品」创建作品壳，再为它上传视频文件；上传后会自动转码为 HLS。
              </p>
              <div class="q-empty__actions">
                <button class="q-btn q-btn--outline q-btn--sm" type="button" @click="openCreateDialog">
                  <QIcon name="plus" :size="13" />
                  新建作品
                </button>
              </div>
            </div>
          </template>
        </el-table>

        <div class="table-card__pager">
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
    </div>

    <!-- 新建作品弹窗 -->
    <el-dialog
      v-model="createDialogVisible"
      title="新建作品"
      width="480px"
      :close-on-click-modal="false"
    >
      <p class="dialog-hint">先创建作品壳，随后进入编辑页上传视频文件并自动转码。</p>
      <el-form
        ref="createFormRef"
        class="dialog-form"
        :model="createForm"
        :rules="createRules"
        label-position="top"
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

/* ---------------- 筛选卡 ---------------- */
.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  align-items: center;

  :deep(.el-form-item) {
    display: flex;
    align-items: center;
    margin: 0;
  }

  :deep(.el-form-item__label) {
    padding-right: 6px;
    font-size: var(--q-fs-12);
    line-height: var(--q-ctl-md);
    color: var(--q-text-secondary);
  }
}

.filter-input {
  width: 220px;
}

.filter-select {
  width: 140px;
}

.filter-actions {
  margin-left: auto;
}

/* ---------------- 表格卡 ---------------- */
.table-card {
  padding: 4px 16px 12px;
}

.table-card__pager {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}

.video-cover {
  display: block;
  width: 72px;
  height: 40px;
  overflow: hidden;
  background: var(--q-fill-tertiary);
  border-radius: var(--q-r-md);
  box-shadow: 0 0 0 1px var(--q-border-quaternary) inset;
}

.video-cover--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--q-text-quaternary);
}

.video-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.video-cell__title {
  overflow: hidden;
  font-size: var(--q-fs-13);
  font-weight: 500;
  color: var(--q-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.video-cell__desc {
  display: -webkit-box;
  overflow: hidden;
  font-size: var(--q-fs-11);
  line-height: var(--q-lh-16);
  color: var(--q-text-tertiary);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
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

.status-cell {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
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

/* ---------------- 弹窗 ---------------- */
.dialog-hint {
  margin-bottom: 12px;
  font-size: var(--q-fs-12);
  line-height: var(--q-lh-18);
  color: var(--q-text-tertiary);
}

.dialog-form {
  :deep(.el-select) {
    width: 100%;
  }

  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }
}

/* ---------------- 响应式 ---------------- */
@include q-mobile {
  .filter-form {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-item {
    align-items: flex-start;
  }

  .filter-input,
  .filter-select {
    width: 100%;
  }

  .filter-actions {
    margin-left: 0;
  }

  .table-card {
    padding: 4px 12px 12px;
  }

  .table-card__pager {
    justify-content: center;
  }
}
</style>
