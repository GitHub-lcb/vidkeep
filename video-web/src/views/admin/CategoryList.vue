<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import QIcon from '@/components/QIcon.vue'
import { createCategory, deleteCategory, listCategories, updateCategory } from '@/api/category'
import { isBizError } from '@/api/request'
import { ERROR_CODE } from '@/types/api'
import { formatDateTime } from '@/utils/format'
import type { CategoryItem } from '@/types/category'

const list = ref<CategoryItem[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

interface CategoryFormState {
  name: string
  sort: number
}

const form = reactive<CategoryFormState>({
  name: '',
  sort: 0,
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { max: 20, message: '分类名称不能超过 20 个字符', trigger: 'blur' },
  ],
}

const dialogTitle = computed<string>(() => (editingId.value === null ? '新增分类' : '编辑分类'))

function errorText(error: unknown, fallback: string): string {
  return isBizError(error) ? error.message : fallback
}

async function loadCategories(): Promise<void> {
  loading.value = true
  try {
    list.value = await listCategories()
  } catch (error) {
    ElMessage.error(errorText(error, '加载分类失败'))
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  form.name = ''
  form.sort = 0
  editingId.value = null
  formRef.value?.clearValidate()
}

function openCreate(): void {
  resetForm()
  dialogVisible.value = true
}

function openEdit(category: CategoryItem): void {
  editingId.value = category.id
  form.name = category.name
  form.sort = category.sort
  dialogVisible.value = true
  window.setTimeout(() => formRef.value?.clearValidate(), 0)
}

async function submitForm(): Promise<void> {
  if (saving.value || formRef.value === undefined) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = { name: form.name.trim(), sort: form.sort }
    if (editingId.value === null) {
      await createCategory(payload)
      ElMessage.success('分类已创建')
    } else {
      await updateCategory(editingId.value, payload)
      ElMessage.success('分类已更新')
    }
    dialogVisible.value = false
    await loadCategories()
  } catch (error) {
    ElMessage.error(errorText(error, '保存分类失败'))
  } finally {
    saving.value = false
  }
}

async function handleDelete(category: CategoryItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除分类「${category.name}」吗？分类下存在视频时后端会拒绝删除。`,
      '删除分类',
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

  try {
    await deleteCategory(category.id)
    ElMessage.success('分类已删除')
    await loadCategories()
  } catch (error) {
    // 3002：分类下仍有视频等状态限制，直接展示后端返回的原因
    if (isBizError(error) && error.code === ERROR_CODE.STATUS_NOT_ALLOWED) {
      ElMessage.error(error.message)
      return
    }
    ElMessage.error(errorText(error, '删除分类失败'))
  }
}

onMounted(() => {
  void loadCategories()
})
</script>

<template>
  <div class="q-page q-fade-in">
    <header class="q-page-head">
      <div class="q-page-head__main">
        <h1 class="q-page-title">分类管理</h1>
        <p class="q-page-desc">分类用于前台筛选与作品归类，列表按排序值升序展示</p>
      </div>
      <div class="q-page-actions">
        <button
          class="q-btn q-btn--outline q-btn--sm"
          type="button"
          :disabled="loading"
          @click="loadCategories"
        >
          <QIcon name="refresh" :size="13" />
          刷新
        </button>
        <button class="q-btn q-btn--primary q-btn--sm" type="button" @click="openCreate">
          <QIcon name="plus" :size="14" />
          新增分类
        </button>
      </div>
    </header>

    <div class="q-stack">
      <!-- 删除限制提示 -->
      <div class="list-tip" role="note">
        <span class="list-tip__icon"><QIcon name="alert" :size="14" /></span>
        <span>删除限制：分类下存在视频时无法删除，需先把作品移到其他分类或删除作品。</span>
      </div>

      <!-- 表格卡 -->
      <section class="q-card table-card" aria-label="分类列表">
        <el-table v-loading="loading" :data="list" row-key="id">
          <el-table-column label="分类名称" min-width="240">
            <template #default="{ row }">
              <div class="category-cell">
                <span class="category-cell__icon"><QIcon name="folder" :size="14" /></span>
                <span class="category-cell__name">{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="排序" width="120" align="center">
            <template #default="{ row }">
              <span class="q-tag q-tag--outline category-sort">{{ row.sort }}</span>
            </template>
          </el-table-column>

          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">
              <span class="cell-time">
                {{ row.createdAt ? formatDateTime(row.createdAt) : '--' }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="152" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <button class="q-btn q-btn--text q-btn--sm" type="button" @click="openEdit(row)">
                  <QIcon name="edit" :size="13" />
                  编辑
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
                <span class="q-empty__face"><QIcon name="folder" :size="18" /></span>
              </div>
              <p class="q-empty__title">还没有分类</p>
              <p class="q-empty__desc">分类用于前台筛选与作品归类，先添加一个分类，再把作品归入其中。</p>
              <div class="q-empty__actions">
                <button class="q-btn q-btn--outline q-btn--sm" type="button" @click="openCreate">
                  <QIcon name="plus" :size="13" />
                  新增分类
                </button>
              </div>
            </div>
          </template>
        </el-table>

        <p v-if="!loading && list.length > 0" class="table-card__foot">
          共 {{ list.length }} 个分类 · 按排序值升序
        </p>
      </section>
    </div>

    <!-- 新增 / 编辑分类弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="440px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        class="dialog-form"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submitForm"
      >
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="请输入分类名称，如：电影"
            maxlength="20"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <div class="sort-field">
            <el-input-number v-model="form.sort" :min="0" :max="9999" :step="1" />
            <span class="sort-field__hint">数值越小越靠前</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

/* ---------------- 提示条 ---------------- */
.list-tip {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
  font-size: var(--q-fs-12);
  line-height: var(--q-lh-18);
  color: var(--q-text-secondary);
  background: var(--q-fill-tertiary);
  border-radius: var(--q-r-lg);
}

.list-tip__icon {
  display: inline-flex;
  flex: none;
  color: var(--q-info);
}

/* ---------------- 表格卡 ---------------- */
.table-card {
  padding: 4px 16px 12px;
}

.table-card__foot {
  padding-top: 10px;
  font-size: var(--q-fs-11);
  color: var(--q-text-tertiary);
}

.category-cell {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.category-cell__icon {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: var(--q-primary-text);
  background: var(--q-primary-bg);
  border-radius: var(--q-r-md);
}

.category-cell__name {
  overflow: hidden;
  font-size: var(--q-fs-13);
  font-weight: 500;
  color: var(--q-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-sort {
  min-width: 28px;
  font-variant-numeric: tabular-nums;
  color: var(--q-text-secondary);
}

.cell-time {
  font-size: var(--q-fs-12);
  color: var(--q-text-tertiary);
  font-variant-numeric: tabular-nums;
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
.dialog-form {
  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }
}

.sort-field {
  display: flex;
  gap: 10px;
  align-items: center;
}

.sort-field__hint {
  font-size: var(--q-fs-11);
  color: var(--q-text-tertiary);
}

/* ---------------- 响应式 ---------------- */
@include q-mobile {
  .table-card {
    padding: 4px 12px 12px;
  }
}
</style>
