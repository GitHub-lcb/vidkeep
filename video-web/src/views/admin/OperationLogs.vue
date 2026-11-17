<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import QIcon from '@/components/QIcon.vue'
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
  <div class="q-page q-fade-in">
    <header class="q-page-head">
      <div class="q-page-head__main">
        <h1 class="q-page-title">操作日志</h1>
        <p class="q-page-desc">
          记录管理员的关键操作（上传、删除、转码、分类维护等），用于问题追溯与安全审计
        </p>
      </div>
      <div class="q-page-actions">
        <button
          class="q-btn q-btn--outline q-btn--sm"
          type="button"
          :disabled="loading"
          @click="loadLogs"
        >
          <QIcon name="refresh" :size="13" />
          刷新
        </button>
      </div>
    </header>

    <div class="q-stack">
      <!-- 筛选卡：单行 28px 紧凑控件 -->
      <section class="q-card q-card--pad" aria-label="筛选条件">
        <el-form class="filter-form" @submit.prevent="handleSearch">
          <el-form-item label="用户名" class="filter-item">
            <el-input
              v-model="filters.username"
              class="filter-input"
              placeholder="按用户名筛选"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>

          <el-form-item label="模块" class="filter-item">
            <el-select v-model="filters.module" class="filter-select" placeholder="全部模块">
              <el-option
                v-for="item in MODULE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item class="filter-item filter-actions">
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </section>

      <!-- 日志表格卡 -->
      <section class="q-card table-card" aria-label="操作日志列表">
        <el-table v-loading="loading" :data="list" row-key="id">
          <el-table-column label="时间" width="170">
            <template #default="{ row }">
              <span class="cell-time">{{ formatDateTime(row.createdAt, 'YYYY-MM-DD HH:mm:ss') }}</span>
            </template>
          </el-table-column>

          <el-table-column label="用户" width="116">
            <template #default="{ row }">
              <span class="log-user">{{ row.username }}</span>
            </template>
          </el-table-column>

          <el-table-column label="模块" width="96" align="center">
            <template #default="{ row }">
              <span class="q-tag" :class="`q-tag--${moduleTagType(row.module)}`">
                {{ row.module }}
              </span>
            </template>
          </el-table-column>

          <el-table-column prop="action" label="动作" min-width="140" show-overflow-tooltip />

          <el-table-column label="方法" width="96" align="center">
            <template #default="{ row }">
              <span class="q-tag method-tag" :class="`q-tag--${methodTagType(row.method)}`">
                {{ row.method }}
              </span>
            </template>
          </el-table-column>

          <el-table-column prop="path" label="路径" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="q-mono log-path">{{ row.path }}</span>
            </template>
          </el-table-column>

          <el-table-column label="耗时" width="100" align="right">
            <template #default="{ row }">
              <span class="log-duration">{{ row.durationMs }} ms</span>
            </template>
          </el-table-column>

          <el-table-column label="详情" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="log-detail">{{ row.detail !== null && row.detail.length > 0 ? row.detail : '—' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作 IP" width="136">
            <template #default="{ row }">
              <span class="q-mono log-ip">{{ row.ip !== null && row.ip.length > 0 ? row.ip : '—' }}</span>
            </template>
          </el-table-column>

          <template #empty>
            <div class="q-empty">
              <div class="q-empty__stack">
                <span class="q-empty__face"><QIcon name="logs" :size="18" /></span>
              </div>
              <p class="q-empty__title">暂无操作日志</p>
              <p class="q-empty__desc">
                管理员上传、删除、转码与分类维护等关键操作会自动记录在这里，用于问题追溯。
              </p>
            </div>
          </template>
        </el-table>

        <div class="table-card__pager">
          <el-pagination
            :current-page="page"
            :page-size="size"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            background
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </section>
    </div>
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
  width: 160px;
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

.cell-time {
  font-size: var(--q-fs-12);
  color: var(--q-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.log-user {
  font-size: var(--q-fs-13);
  font-weight: 500;
  color: var(--q-text);
}

.method-tag {
  font-family: var(--q-font-mono);
  letter-spacing: 0.02em;
}

.log-path {
  font-size: var(--q-fs-11);
  color: var(--q-text-tertiary);
  word-break: break-all;
}

.log-duration {
  font-size: var(--q-fs-12);
  color: var(--q-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.log-detail {
  font-size: var(--q-fs-12);
  color: var(--q-text-secondary);
  word-break: break-all;
}

.log-ip {
  font-size: var(--q-fs-11);
  color: var(--q-text-tertiary);
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
