<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
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
  <div class="vk-page category-list">
    <header class="page-head">
      <div>
        <h1 class="vk-page__title">分类管理</h1>
        <p class="vk-page__subtitle">分类用于前台筛选与作品归类，列表按排序值升序展示</p>
      </div>
      <el-button type="primary" @click="openCreate">新增分类</el-button>
    </header>

    <el-alert
      class="category-tip"
      type="info"
      :closable="false"
      show-icon
      title="删除限制：分类下存在视频时无法删除，需先把作品移到其他分类或删除作品。"
    />

    <section class="table-card">
      <el-table
        v-loading="loading"
        :data="list"
        row-key="id"
        empty-text="暂无分类，点击右上角「新增分类」添加"
      >
        <el-table-column prop="name" label="分类名称" min-width="200">
          <template #default="{ row }">
            <span class="category-name">{{ row.name }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="sort" label="排序" width="100" align="center" />

        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            <span class="category-time">
              {{ row.createdAt ? formatDateTime(row.createdAt) : '--' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="440px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="72px"
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
          <el-input-number v-model="form.sort" :min="0" :max="9999" :step="1" />
          <span class="sort-hint">数值越小越靠前</span>
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

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.category-tip {
  margin-top: 18px;
  border-radius: 10px;
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

.category-name {
  font-size: 13px;
  font-weight: 600;
  color: #1f2733;
}

.category-time {
  font-size: 12px;
  color: #5b6678;
}

.sort-hint {
  margin-left: 10px;
  font-size: 12px;
  color: $vk-light-text-muted;
}
</style>
