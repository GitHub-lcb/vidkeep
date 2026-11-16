<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { isBizError } from '@/api/request'
import {
  checkUpload,
  completeUpload,
  getUploadStatus,
  initUpload,
  uploadChunk,
} from '@/api/upload'
import { cancelTranscode, getTranscodeProgress, retryTranscode } from '@/api/transcode'
import { ERROR_CODE } from '@/types/api'
import type { TranscodeMode, TranscodeStatus } from '@/types/transcode'
import { formatFileSize } from '@/utils/format'
import { sampleFingerprint } from '@/utils/hash'

const props = defineProps<{ videoId: number }>()

const emit = defineEmits<{
  /** 上传完成（含秒传命中）：已创建或被复用 episode，开始转码 */
  (event: 'completed', episodeId: number): void
  /** 转码完成，剧集可播放 */
  (event: 'transcode-done', episodeId: number): void
}>()

/** 上传阶段：idle 待选文件 → hashing 指纹 → uploading 分片 → merging 合并 → transcoding 转码 */
type UploadPhase =
  | 'idle'
  | 'hashing'
  | 'uploading'
  | 'merging'
  | 'transcoding'
  | 'done'
  | 'failed'
  | 'canceled'

/** 失败所处阶段：上传失败可“重新上传（自动续传）”，转码失败可“重试转码” */
type FailureStage = 'upload' | 'transcode'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

/** 后端允许的视频扩展名（AGENTS.md 上传白名单） */
const ALLOWED_EXTENSIONS: readonly string[] = ['mp4', 'mov', 'mkv', 'avi', 'webm', 'flv']
/** 单文件上限 10GB（文档 7.1.5 边界情况） */
const MAX_FILE_SIZE = 10 * 1024 * 1024 * 1024
/** 分片并发数（文档 7.1.1：默认 3 并发） */
const UPLOAD_CONCURRENCY = 3
/** 单个分片失败后的自动重试次数 */
const CHUNK_MAX_RETRY = 2
/** 转码进度轮询间隔（文档 4.5 / 7.4.2：每 2s 轮询） */
const TRANSCODE_POLL_INTERVAL = 2000

/** 转码状态 → 文案 / el-tag 类型 */
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

const phase = ref<UploadPhase>('idle')
const failureStage = ref<FailureStage>('upload')

const fileInputRef = ref<HTMLInputElement>()
const file = ref<File | null>(null)
const fileName = ref('')
const fileSize = ref(0)
const isDragging = ref(false)

const fingerprint = ref('')
const uploadId = ref('')
const totalChunks = ref(0)
const uploadedCount = ref(0)
const uploadSpeed = ref(0)
const uploadError = ref('')

const episodeId = ref<number | null>(null)
const transcodeStatus = ref<TranscodeStatus>('NONE')
const transcodeProgress = ref(0)
const transcodeMode = ref<TranscodeMode | null>(null)
const transcodeError = ref('')
const transcodeActionLoading = ref(false)

/** 任务代号：开始/重置/切换作品时自增，异步流程发现代号变化即静默退出，避免旧任务污染新状态 */
let runId = 0
let pollTimer: number | null = null
let speedTimer: number | null = null
let pollFailCount = 0
let uploadStartedAt = 0
let uploadedBytesAtStart = 0
let disposed = false

/** 是否正在处理任务：处理中不允许重新选择文件 */
const busy = computed<boolean>(
  () =>
    phase.value === 'hashing' ||
    phase.value === 'uploading' ||
    phase.value === 'merging' ||
    phase.value === 'transcoding',
)

/** 指纹 / 上传 / 合并进度条是否显示 */
const showUploadProgress = computed<boolean>(
  () =>
    phase.value === 'hashing' ||
    phase.value === 'uploading' ||
    phase.value === 'merging' ||
    (phase.value === 'failed' && failureStage.value === 'upload'),
)

/** 转码卡片是否显示（上传失败时不显示） */
const showTranscode = computed<boolean>(() => {
  if (phase.value === 'failed') return failureStage.value === 'transcode'
  return (
    phase.value === 'transcoding' || phase.value === 'done' || phase.value === 'canceled'
  )
})

/** 已完成分片 / 总分片 = 上传进度；转码、合并阶段视为上传 100% */
const uploadPercent = computed<number>(() => {
  if (phase.value === 'hashing') return 0
  if (totalChunks.value <= 0) return 0
  const percent = Math.floor((uploadedCount.value / totalChunks.value) * 100)
  if (phase.value === 'uploading') return Math.min(99, percent)
  if (phase.value === 'merging') return 100
  return Math.min(100, percent)
})

/** 进度条状态：完成绿 / 失败红 / 取消黄 */
const progressStatus = computed<'success' | 'exception' | 'warning' | undefined>(() => {
  if (phase.value === 'done') return 'success'
  if (phase.value === 'failed') return 'exception'
  if (phase.value === 'canceled') return 'warning'
  return undefined
})

const phaseText = computed<string>(() => {
  switch (phase.value) {
    case 'idle':
      return '待选择文件'
    case 'hashing':
      return '正在计算抽样指纹'
    case 'uploading':
      return '分片上传中'
    case 'merging':
      return '正在合并分片并创建剧集'
    case 'transcoding':
      return '已进入转码队列'
    case 'done':
      return '转码完成'
    case 'failed':
      return failureStage.value === 'transcode' ? '转码失败' : '上传失败'
    case 'canceled':
      return '转码已取消'
    default:
      return '处理中'
  }
})

const speedText = computed<string>(() =>
  uploadSpeed.value > 0 ? `${formatFileSize(uploadSpeed.value)}/s` : '--',
)

const transcodeTagType = computed<TagType>(() => TRANSCODE_STATUS_TYPE[transcodeStatus.value])
const transcodeStatusText = computed<string>(() => TRANSCODE_STATUS_TEXT[transcodeStatus.value])
const modeText = computed<string>(() => {
  if (transcodeMode.value === null) return '尚未开始'
  return transcodeMode.value === 'REMUX' ? '快速封装 REMUX' : '重新编码 TRANSCODE'
})

/** 业务错误优先展示后端 message，其余兜底 */
function errorText(error: unknown, fallback: string): string {
  if (isBizError(error)) return error.message
  if (error instanceof Error && error.message.length > 0) return error.message
  return fallback
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

/** 前端本地校验：扩展名白名单 + 非空 + 10GB 上限 */
function validateFile(candidate: File): string | null {
  const dotIndex = candidate.name.lastIndexOf('.')
  const extension = dotIndex >= 0 ? candidate.name.slice(dotIndex + 1).toLowerCase() : ''
  if (!ALLOWED_EXTENSIONS.includes(extension)) {
    return `不支持的文件类型，仅支持 ${ALLOWED_EXTENSIONS.join(' / ')}`
  }
  if (candidate.size <= 0) return '文件为空，请重新选择'
  if (candidate.size > MAX_FILE_SIZE) {
    return `文件超过 10GB 上限（当前 ${formatFileSize(candidate.size)}）`
  }
  return null
}

function stopPolling(): void {
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer)
    pollTimer = null
  }
}

function stopSpeedTimer(): void {
  if (speedTimer !== null) {
    window.clearInterval(speedTimer)
    speedTimer = null
  }
}

/** 每秒按“已上传字节差 / 耗时”估算上传速度（续传时扣除已上传部分） */
function startSpeedTimer(fileTotalSize: number, chunkSize: number, currentRun: number): void {
  uploadStartedAt = Date.now()
  uploadedBytesAtStart = Math.min(fileTotalSize, uploadedCount.value * chunkSize)
  speedTimer = window.setInterval(() => {
    if (currentRun !== runId) return
    const elapsedSeconds = (Date.now() - uploadStartedAt) / 1000
    if (elapsedSeconds <= 0) return
    const uploadedBytes = Math.min(fileTotalSize, uploadedCount.value * chunkSize)
    uploadSpeed.value = Math.max(0, (uploadedBytes - uploadedBytesAtStart) / elapsedSeconds)
  }, 1000)
}

function applyFile(candidate: File): void {
  if (busy.value) {
    ElMessage.warning('当前有任务进行中，请等待完成后再选择文件')
    return
  }
  const invalidReason = validateFile(candidate)
  if (invalidReason !== null) {
    ElMessage.error(invalidReason)
    return
  }
  resetTask()
  file.value = candidate
  fileName.value = candidate.name
  fileSize.value = candidate.size
}

function handleFileChange(event: Event): void {
  const input = event.target
  if (!(input instanceof HTMLInputElement)) return
  const selected = input.files?.[0]
  // 立即清空 input，保证再次选择同一个文件也能触发 change
  input.value = ''
  if (selected === undefined) return
  applyFile(selected)
}

function handleDrop(event: DragEvent): void {
  isDragging.value = false
  const dropped = event.dataTransfer?.files?.[0]
  if (dropped === undefined) {
    ElMessage.warning('未检测到拖入的文件')
    return
  }
  applyFile(dropped)
}

function triggerSelect(): void {
  if (busy.value) {
    ElMessage.warning('当前有任务进行中，请等待完成后再选择文件')
    return
  }
  fileInputRef.value?.click()
}

/** 上传单个分片，失败自动重试 CHUNK_MAX_RETRY 次（会话失效不重试） */
async function uploadSingleChunk(
  currentFile: File,
  chunkSize: number,
  index: number,
  currentRun: number,
): Promise<void> {
  const start = index * chunkSize
  const blob = currentFile.slice(start, Math.min(start + chunkSize, currentFile.size))
  let lastError: unknown = null
  for (let attempt = 0; attempt <= CHUNK_MAX_RETRY; attempt += 1) {
    if (currentRun !== runId) throw new Error('任务已取消')
    try {
      await uploadChunk(uploadId.value, index, blob)
      return
    } catch (error) {
      lastError = error
      // 上传会话已失效，重试无意义，直接失败并提示重新上传
      if (isBizError(error) && error.code === ERROR_CODE.UPLOAD_SESSION_NOT_FOUND) break
      if (attempt < CHUNK_MAX_RETRY) await delay(400 * (attempt + 1))
    }
  }
  throw lastError instanceof Error ? lastError : new Error(`分片 ${index} 上传失败`)
}

/** 并发上传待传分片：跳过已上传分片（断点续传），任一分片永久失败则中止整批 */
async function uploadPendingChunks(
  currentFile: File,
  chunkSize: number,
  total: number,
  alreadyUploaded: number[],
  currentRun: number,
): Promise<void> {
  const uploadedSet = new Set(alreadyUploaded)
  const pendingIndexes: number[] = []
  for (let index = 0; index < total; index += 1) {
    if (!uploadedSet.has(index)) pendingIndexes.push(index)
  }

  let cursor = 0
  let aborted = false
  const worker = async (): Promise<void> => {
    while (!aborted) {
      if (currentRun !== runId) return
      const position = cursor
      cursor += 1
      if (position >= pendingIndexes.length) return
      try {
        await uploadSingleChunk(currentFile, chunkSize, pendingIndexes[position], currentRun)
      } catch (error) {
        aborted = true
        throw error
      }
      uploadedCount.value = Math.min(total, uploadedCount.value + 1)
    }
  }

  const workerCount = Math.min(UPLOAD_CONCURRENCY, pendingIndexes.length)
  await Promise.all(Array.from({ length: workerCount }, () => worker()))
}

/** 轮询转码进度：每 2s 一次，DONE 停止并 emit，FAILED 停止等待用户重试/取消 */
async function pollTranscode(currentEpisodeId: number, currentRun: number): Promise<void> {
  pollTimer = null
  if (disposed || currentRun !== runId) return
  try {
    const progress = await getTranscodeProgress(currentEpisodeId)
    if (disposed || currentRun !== runId) return
    pollFailCount = 0
    transcodeStatus.value = progress.status
    transcodeProgress.value = Math.min(100, Math.max(0, progress.progress))
    transcodeMode.value = progress.mode
    transcodeError.value = progress.errorMsg ?? ''

    if (progress.status === 'DONE') {
      transcodeProgress.value = 100
      phase.value = 'done'
      ElMessage.success('转码完成，可以预览播放了')
      emit('transcode-done', currentEpisodeId)
      return
    }
    if (progress.status === 'FAILED') {
      phase.value = 'failed'
      failureStage.value = 'transcode'
      return
    }
  } catch (error) {
    if (disposed || currentRun !== runId) return
    pollFailCount += 1
    // 网络抖动允许短暂失败；连续 5 次失败才终止轮询，避免误判
    if (pollFailCount >= 5) {
      stopPolling()
      phase.value = 'failed'
      failureStage.value = 'transcode'
      transcodeError.value = errorText(error, '查询转码进度失败，请稍后重试')
      return
    }
  }
  if (disposed || currentRun !== runId) return
  pollTimer = window.setTimeout(() => {
    void pollTranscode(currentEpisodeId, currentRun)
  }, TRANSCODE_POLL_INTERVAL)
}

function beginTranscodePolling(currentEpisodeId: number, currentRun: number): void {
  stopPolling()
  phase.value = 'transcoding'
  transcodeStatus.value = 'PENDING'
  transcodeProgress.value = 0
  transcodeMode.value = null
  transcodeError.value = ''
  pollFailCount = 0
  pollTimer = window.setTimeout(() => {
    void pollTranscode(currentEpisodeId, currentRun)
  }, TRANSCODE_POLL_INTERVAL)
}

/** 完整上传流水线：抽样指纹 → 秒传检查 → 续传分片 → 合并 → 转码轮询 */
async function startUpload(): Promise<void> {
  const currentFile = file.value
  if (currentFile === null) {
    ElMessage.warning('请先选择视频文件')
    return
  }
  if (busy.value) return

  const currentRun = ++runId
  stopPolling()
  stopSpeedTimer()
  fingerprint.value = ''
  uploadId.value = ''
  totalChunks.value = 0
  uploadedCount.value = 0
  uploadSpeed.value = 0
  uploadError.value = ''
  episodeId.value = null
  transcodeStatus.value = 'NONE'
  transcodeProgress.value = 0
  transcodeMode.value = null
  transcodeError.value = ''
  phase.value = 'hashing'

  try {
    // 1. 抽样指纹（前 2MB + 中 2MB + 尾 2MB + 文件大小 → SHA-256）
    const sampledFingerprint = await sampleFingerprint(currentFile)
    if (currentRun !== runId) return
    fingerprint.value = sampledFingerprint

    // 2. 秒传检查：命中则直接复用已存在的 episode，进入转码轮询
    const checked = await checkUpload({
      fingerprint: sampledFingerprint,
      fileName: currentFile.name,
      fileSize: currentFile.size,
    })
    if (currentRun !== runId) return
    if (checked.exists && checked.episodeId !== undefined) {
      ElMessage.success('秒传成功，已复用服务器上的视频')
      episodeId.value = checked.episodeId
      emit('completed', checked.episodeId)
      beginTranscodePolling(checked.episodeId, currentRun)
      return
    }

    // 3. 未命中：优先使用 check 返回的续传会话；缺失时兜底 init
    let sessionUploadId = checked.uploadId
    let sessionChunkSize = checked.chunkSize
    let sessionTotalChunks = checked.totalChunks
    let uploadedChunks = checked.uploadedChunks ?? []
    if (
      sessionUploadId === undefined ||
      sessionChunkSize === undefined ||
      sessionTotalChunks === undefined
    ) {
      const initialized = await initUpload({
        fileName: currentFile.name,
        fileSize: currentFile.size,
        fingerprint: sampledFingerprint,
      })
      if (currentRun !== runId) return
      sessionUploadId = initialized.uploadId
      sessionChunkSize = initialized.chunkSize
      sessionTotalChunks = initialized.totalChunks
      uploadedChunks = []
    }

    // 4. 以 status 接口为准同步已传分片；会话过期则重新 init
    try {
      const status = await getUploadStatus(sessionUploadId)
      if (currentRun !== runId) return
      sessionChunkSize = status.chunkSize
      sessionTotalChunks = status.totalChunks
      uploadedChunks = status.uploadedChunks
    } catch (error) {
      if (isBizError(error) && error.code === ERROR_CODE.UPLOAD_SESSION_NOT_FOUND) {
        const initialized = await initUpload({
          fileName: currentFile.name,
          fileSize: currentFile.size,
          fingerprint: sampledFingerprint,
        })
        if (currentRun !== runId) return
        sessionUploadId = initialized.uploadId
        sessionChunkSize = initialized.chunkSize
        sessionTotalChunks = initialized.totalChunks
        uploadedChunks = []
      } else {
        throw error
      }
    }

    uploadId.value = sessionUploadId
    totalChunks.value = sessionTotalChunks
    uploadedCount.value = uploadedChunks.length
    phase.value = 'uploading'
    startSpeedTimer(currentFile.size, sessionChunkSize, currentRun)

    // 5. 并发 3 上传分片，跳过已上传分片
    await uploadPendingChunks(
      currentFile,
      sessionChunkSize,
      sessionTotalChunks,
      uploadedChunks,
      currentRun,
    )
    if (currentRun !== runId) return

    // 6. 合并分片 → 创建 episode → 入队转码
    phase.value = 'merging'
    stopSpeedTimer()
    const completed = await completeUpload(sessionUploadId, {
      videoId: props.videoId,
      coverMode: 'AUTO',
    })
    if (currentRun !== runId) return
    episodeId.value = completed.episodeId
    emit('completed', completed.episodeId)
    ElMessage.success('上传完成，已开始转码')
    beginTranscodePolling(completed.episodeId, currentRun)
  } catch (error) {
    if (currentRun !== runId) return
    stopSpeedTimer()
    stopPolling()
    phase.value = 'failed'
    failureStage.value = 'upload'
    uploadError.value = errorText(error, '上传失败，请稍后重试')
    ElMessage.error(uploadError.value)
  }
}

/** 上传失败后重试：重新走 check（自动命中已有分片会话，实现续传） */
function handleRetryUpload(): void {
  void startUpload()
}

async function handleRetryTranscode(): Promise<void> {
  const currentEpisodeId = episodeId.value
  if (currentEpisodeId === null) return
  transcodeActionLoading.value = true
  const currentRun = ++runId
  try {
    await retryTranscode(currentEpisodeId)
    if (currentRun !== runId) return
    ElMessage.success('已重新提交转码任务')
    beginTranscodePolling(currentEpisodeId, currentRun)
  } catch (error) {
    ElMessage.error(errorText(error, '重试转码失败'))
  } finally {
    transcodeActionLoading.value = false
  }
}

async function handleCancelTranscode(): Promise<void> {
  const currentEpisodeId = episodeId.value
  if (currentEpisodeId === null) return
  transcodeActionLoading.value = true
  const canceledRun = ++runId
  stopPolling()
  try {
    await cancelTranscode(currentEpisodeId)
    if (canceledRun !== runId) return
    phase.value = 'canceled'
    ElMessage.warning('已取消转码')
  } catch (error) {
    if (canceledRun !== runId) return
    ElMessage.error(errorText(error, '取消转码失败'))
    // 取消失败则恢复轮询，避免状态卡死
    beginTranscodePolling(currentEpisodeId, runId)
  } finally {
    transcodeActionLoading.value = false
  }
}

/** 重置为待选文件状态（上传中的任务会因 runId 变化静默中止，已传分片保留可续传） */
function resetTask(): void {
  runId += 1
  stopPolling()
  stopSpeedTimer()
  phase.value = 'idle'
  failureStage.value = 'upload'
  file.value = null
  fileName.value = ''
  fileSize.value = 0
  fingerprint.value = ''
  uploadId.value = ''
  totalChunks.value = 0
  uploadedCount.value = 0
  uploadSpeed.value = 0
  uploadError.value = ''
  episodeId.value = null
  transcodeStatus.value = 'NONE'
  transcodeProgress.value = 0
  transcodeMode.value = null
  transcodeError.value = ''
  transcodeActionLoading.value = false
  pollFailCount = 0
  const input = fileInputRef.value
  if (input !== undefined) input.value = ''
}

// 切换作品时重置：旧任务的定时器与异步回调全部失效，避免把 A 的进度显示到 B 上
watch(
  () => props.videoId,
  (next, previous) => {
    if (next === previous) return
    const wasActive = phase.value !== 'idle'
    resetTask()
    if (wasActive) {
      ElMessage.warning('已切换作品，当前进度显示已重置（已上传分片可由下次上传续传）')
    }
  },
)

onBeforeUnmount(() => {
  disposed = true
  runId += 1
  stopPolling()
  stopSpeedTimer()
})
</script>

<template>
  <section class="upload-panel">
    <header class="upload-panel__head">
      <div>
        <h3 class="upload-panel__title">上传视频文件</h3>
        <p class="upload-panel__hint">
          支持 mp4 / mov / mkv / avi / webm / flv，单文件不超过 10GB；自动分片并支持断点续传与秒传
        </p>
      </div>
      <el-tag v-if="busy" type="warning" effect="plain">任务进行中</el-tag>
      <el-tag v-else-if="phase === 'done'" type="success" effect="plain">已完成</el-tag>
    </header>

    <!-- 隐藏的文件输入框：常驻 DOM，保证「重新选择」按钮也能触发 -->
    <input
      ref="fileInputRef"
      class="upload-panel__file-input"
      type="file"
      accept=".mp4,.mov,.mkv,.avi,.webm,.flv"
      @change="handleFileChange"
    />

    <!-- 文件选择区（拖拽 / 点击） -->
    <div
      v-if="phase === 'idle' && file === null"
      class="upload-drop"
      :class="{ 'upload-drop--active': isDragging }"
      role="button"
      tabindex="0"
      @click="triggerSelect"
      @keydown.enter="triggerSelect"
      @dragover.prevent="isDragging = true"
      @dragleave.prevent="isDragging = false"
      @drop.prevent="handleDrop"
    >
      <span class="upload-drop__icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26">
          <path
            d="M12 16V5m0 0L7.5 9.5M12 5l4.5 4.5M5 16.5V19h14v-2.5"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </span>
      <span class="upload-drop__title">点击选择或拖拽视频文件到此处</span>
      <span class="upload-drop__sub">扩展名与大小会在本地校验，不会上传无效文件</span>
    </div>

    <!-- 已选文件（待开始） -->
    <div v-if="phase === 'idle' && file !== null" class="upload-file">
      <div class="upload-file__meta">
        <span class="upload-file__name">{{ fileName }}</span>
        <span class="upload-file__size">{{ formatFileSize(fileSize) }}</span>
      </div>
      <div class="upload-file__actions">
        <el-button type="primary" @click="startUpload">开始上传</el-button>
        <el-button @click="triggerSelect">重新选择</el-button>
        <el-button text @click="resetTask">清除</el-button>
      </div>
    </div>

    <!-- 指纹 / 分片 / 合并进度 -->
    <div v-if="showUploadProgress" class="upload-progress">
      <div class="upload-progress__head">
        <span class="upload-progress__phase">{{ phaseText }}</span>
        <span class="upload-progress__percent">{{ uploadPercent }}%</span>
      </div>
      <el-progress
        :percentage="uploadPercent"
        :status="progressStatus"
        :stroke-width="10"
        :show-text="false"
      />
      <div class="upload-progress__meta">
        <span v-if="phase === 'hashing'">正在读取抽样块并计算 SHA-256 指纹…</span>
        <span v-else-if="phase === 'uploading'">
          分片 {{ uploadedCount }} / {{ totalChunks }} · 速度 {{ speedText }}
        </span>
        <span v-else-if="phase === 'merging'">正在合并分片并创建剧集，请稍候…</span>
        <span v-else>已上传 {{ uploadedCount }} / {{ totalChunks }} 个分片，重新上传可继续续传</span>
      </div>
    </div>

    <!-- 上传失败 -->
    <el-alert
      v-if="phase === 'failed' && failureStage === 'upload'"
      class="upload-alert"
      type="error"
      :closable="false"
      show-icon
      :title="uploadError || '上传失败'"
    />
    <div v-if="phase === 'failed' && failureStage === 'upload'" class="upload-actions">
      <el-button type="primary" @click="handleRetryUpload">重新上传（自动续传）</el-button>
      <el-button @click="resetTask">重新选择文件</el-button>
    </div>

    <!-- 转码进度 -->
    <div v-if="showTranscode" class="transcode">
      <div class="transcode__head">
        <div class="transcode__tags">
          <el-tag v-if="phase === 'canceled'" type="info" effect="plain">已取消</el-tag>
          <el-tag v-else :type="transcodeTagType" effect="plain">{{ transcodeStatusText }}</el-tag>
          <el-tag
            v-if="transcodeMode !== null && phase !== 'canceled'"
            type="info"
            effect="plain"
          >
            {{ modeText }}
          </el-tag>
          <span v-if="episodeId !== null" class="transcode__episode">剧集 #{{ episodeId }}</span>
        </div>
        <span v-if="phase === 'transcoding'" class="transcode__percent">
          {{ transcodeProgress }}%
        </span>
      </div>
      <el-progress
        :percentage="transcodeProgress"
        :status="progressStatus"
        :stroke-width="10"
        :show-text="false"
      />
      <p class="transcode__desc">
        <template v-if="phase === 'transcoding'">
          后台正在转码为 HLS，每 2 秒自动刷新进度；可离开本页稍后回到视频编辑页查看。
        </template>
        <template v-else-if="phase === 'done'">转码完成，剧集已可预览播放。</template>
        <template v-else-if="phase === 'canceled'">转码已取消，可在视频编辑页重新上传或稍后处理。</template>
        <template v-else>{{ transcodeError || '转码失败，请根据失败原因排查后重试。' }}</template>
      </p>
      <div class="transcode__actions">
        <el-button
          v-if="phase === 'transcoding'"
          :loading="transcodeActionLoading"
          @click="handleCancelTranscode"
        >
          取消转码
        </el-button>
        <el-button
          v-if="phase === 'failed'"
          type="primary"
          :loading="transcodeActionLoading"
          @click="handleRetryTranscode"
        >
          重试转码
        </el-button>
        <el-button
          v-if="phase === 'done' || phase === 'canceled'"
          type="primary"
          plain
          @click="resetTask"
        >
          继续上传下一集
        </el-button>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '../styles/variables' as *;

.upload-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.upload-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.upload-panel__title {
  font-size: 15px;
  font-weight: 600;
  color: #151c28;
}

.upload-panel__hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: $vk-light-text-muted;
}

/* 拖拽 / 点击选择区 */
.upload-drop {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 36px 20px;
  background: #fafbfc;
  border: 1px dashed #d4dae3;
  border-radius: 12px;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease;

  &:hover,
  &--active {
    background: #f5f8ff;
    border-color: $vk-accent;
  }

  &:focus-visible {
    outline: 2px solid rgba(91, 141, 239, 0.5);
    outline-offset: 2px;
  }
}

.upload-panel__file-input {
  display: none;
}

.upload-drop__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  color: $vk-accent;
  background: rgba(91, 141, 239, 0.1);
  border-radius: 50%;
}

.upload-drop__title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2733;
}

.upload-drop__sub {
  font-size: 12px;
  color: $vk-light-text-muted;
}

/* 已选文件 */
.upload-file {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
  background: #f7f9fc;
  border: 1px solid $vk-light-border;
  border-radius: 10px;
}

.upload-file__meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.upload-file__name {
  max-width: 460px;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  color: #1f2733;
  white-space: nowrap;
  text-overflow: ellipsis;

  @include vk-mobile {
    max-width: 100%;
  }
}

.upload-file__size {
  font-size: 12px;
  color: $vk-light-text-muted;
}

.upload-file__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* 上传进度 */
.upload-progress {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  background: #ffffff;
  border: 1px solid $vk-light-border;
  border-radius: 12px;

  :deep(.el-progress-bar__outer) {
    background: #eef1f6;
  }
}

.upload-progress__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.upload-progress__phase {
  font-size: 13px;
  font-weight: 600;
  color: #1f2733;
}

.upload-progress__percent {
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  color: $vk-accent;
}

.upload-progress__meta {
  font-size: 12px;
  color: $vk-light-text-muted;
}

.upload-alert {
  border-radius: 10px;
}

.upload-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

/* 转码卡片 */
.transcode {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: #ffffff;
  border: 1px solid $vk-light-border;
  border-radius: 12px;

  :deep(.el-progress-bar__outer) {
    background: #eef1f6;
  }
}

.transcode__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.transcode__tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.transcode__episode {
  font-size: 12px;
  color: $vk-light-text-muted;
}

.transcode__percent {
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  color: $vk-accent;
}

.transcode__desc {
  font-size: 12px;
  line-height: 1.65;
  color: $vk-light-text-muted;
}

.transcode__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
</style>
