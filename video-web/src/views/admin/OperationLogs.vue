<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listOperationLogs } from '@/api/admin'
import { isBizError } from '@/api/request'
import { formatDateTime } from '@/utils/format'
import type { OperationLogItem } from '@/types/admin'

/**
 * 操作日志页（F-19）：管理员操作审计。
 * 支持用户名 / 模块筛选与分页；详情、路径过长时单元格省略并通过 tooltip 查看全文。
 */

/** Element Plus 标签类型（method/module 徽标共用） */
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

interface LogFilterState {
  username: string
  module: string
}

/** 模块下拉固定选项：全部 + 契约中的模块枚举（后端 module 字段为中文） */
const MODULE_OPTIONS: ReadonlyArray<{ label: string, value: string }> = [
  { label: '全部模块', value: '' },
  { label: '视频', value: '视频' },
  { label: '分类', value: '分类' },
  { label: '上传', value: '上传' },
  { label: '转码', value: '转码' },
]

const METHOD_TAG_TYPE: Record<string, TagType> = {
  GET: 'info',
  POST: 'success',
  PUT: 'warning',
  PATCH: 'warning',
  DELETE: 'danger',
}

const MODULE_TAG_TYPE: Record<string, TagType> = {
  视频: 'primary',
  分类: 'info',
  上传: 'success',
  转码: 'warning',
}

const filters = reactive<LogFilterState>({ username: '', module: '' })
const list = ref<OperationLogItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

function errorText(error: unknown, fallback: string): string {
  return isBizError(error) ? error.message : fallback
}

/** 加载当前页日志（空字符串筛选由 api/admin.ts 统一裁剪） */
async function loadLogs(): Promise<void> {
  loading.value = true
  try {
    const result = await listOperationLogs({
      page: page.value,
      size: size.value,
      username: filters.username,
      module: filters.module,
    })
    list.value = result.records
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorText(error, '加载操作日志失败'))
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  page.value = 1
  void loadLogs()
}

function handleReset(): void {
  filters.username = ''
  filters.module = ''
  page.value = 1
  void loadLogs()
}

function handlePageChange(nextPage: number): void {
  page.value = nextPage
  void loadLogs()
}

function handleSizeChange(nextSize: number): void {
  size.value = nextSize
  page.value = 1
  void loadLogs()
}

/** HTTP 方法徽标类型 */
function methodTagType(method: string): TagType {
  return METHOD_TAG_TYPE[method.toUpperCase()] ?? 'info'
}

/** 模块徽标类型 */
function moduleTagType(module: string): TagType {
  return MODULE_TAG_TYPE[module] ?? 'info'
}

onMounted(() => {
  void loadLogs()
})
</script>

<template>
  <div class="vk-page operation-logs">
    <header class="page-head">
      <div>
        <h1 class="vk-page__title">操作日志</h1>
        <p class="vk-page__subtitle">
          记录管理员的关键操作（上传、删除、转码、分类维护等），用于问题追溯与安全审计
        </p>
      </div>
    </header>

    <section class="filter-card">
      <el-input
        v-model="filters.username"
        class="filter-card__input"
        placeholder="按用户名筛选"
        clearable
        @keyup.enter="handleSearch"
      />
      <el-select v-model="filters.module" class="filter-card__select" placeholder="全部模块">
        <el-option
          v-for="item in MODULE_OPTIONS"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </section>

    <section class="table-card">
      <el-table
        v-loading="loading"
        :data="list"
        row-key="id"
        empty-text="暂无操作日志"
      >
        <el-table-column label="时间" width="170">
          <template #default="{ row }">
            <span class="log-time">{{ formatDateTime(row.createdAt, 'YYYY-MM-DD HH:mm:ss') }}</span>
          </template>
        </el-table-column>

        <el-table-column label="用户" width="120">
          <template #default="{ row }">
            <span class="log-user">{{ row.username }}</span>
          </template>
        </el-table-column>

        <el-table-column label="模块" width="96" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="moduleTagType(row.module)" effect="light">
              {{ row.module }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="action" label="动作" min-width="140" show-overflow-tooltip />

        <el-table-column label="方法" width="92" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="methodTagType(row.method)" effect="plain">
              {{ row.method }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="path" label="路径" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="log-path">{{ row.path }}</span>
          </template>
        </el-table-column>

        <el-table-column label="耗时" width="100" align="right">
          <template #default="{ row }">
            <span class="log-duration">{{ row.durationMs }} ms</span>
          </template>
        </el-table-column>

        <el-table-column label="详情" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="log-detail">{{ row.detail !== null && row.detail.length > 0 ? row.detail : '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作 IP" width="140">
          <template #default="{ row }">
            <span class="log-ip">{{ row.ip !== null && row.ip.length > 0 ? row.ip : '—' }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="log-pagination">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
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

.filter-card {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 18px;
  padding: 16px 20px;
  background: $vk-light-surface;
  border: 1px solid $vk-light-border;
  border-radius: 12px;
}

.filter-card__input {
  width: 220px;

  @include vk-mobile {
    width: 100%;
  }
}

.filter-card__select {
  width: 160px;

  @include vk-mobile {
    width: 100%;
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

.log-time {
  font-size: 12px;
  color: #5b6678;
  font-variant-numeric: tabular-nums;
}

.log-user {
  font-size: 13px;
  font-weight: 600;
  color: #1f2733;
}

.log-path,
.log-detail {
  font-size: 12.5px;
  color: #4d5868;
  word-break: break-all;
}

.log-duration {
  font-size: 12px;
  color: #7b8698;
  font-variant-numeric: tabular-nums;
}

.log-ip {
  font-size: 12px;
  color: #4d5868;
  font-variant-numeric: tabular-nums;
}

.log-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;

  @include vk-mobile {
    justify-content: center;
  }
}
</style>
