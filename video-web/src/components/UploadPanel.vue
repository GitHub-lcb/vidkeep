<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import QIcon from '@/components/QIcon.vue'
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

/** 转码状态 → 文案 / 标签语义色（DONE=success、PROCESSING/PENDING=warning、FAILED=danger、NONE=info） */
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
      <div class="upload-panel__intro">
        <h3 class="upload-panel__title">上传视频文件</h3>
        <p class="upload-panel__hint">
          支持 mp4 / mov / mkv / avi / webm / flv，单文件不超过 10GB；自动分片并支持断点续传与秒传
        </p>
      </div>
      <div class="upload-panel__state">
        <span v-if="busy" class="q-tag q-tag--warning q-tag--dot">任务进行中</span>
        <span v-else-if="phase === 'done'" class="q-tag q-tag--success q-tag--dot">已完成</span>
      </div>
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
      aria-label="选择或拖拽视频文件"
      @click="triggerSelect"
      @keydown.enter="triggerSelect"
      @dragover.prevent="isDragging = true"
      @dragleave.prevent="isDragging = false"
      @drop.prevent="handleDrop"
    >
      <span class="upload-drop__icon" aria-hidden="true">
        <QIcon name="upload" :size="22" />
      </span>
      <span class="upload-drop__title">点击选择或拖拽视频文件到此处</span>
      <span class="upload-drop__sub">扩展名与大小会在本地校验，不会上传无效文件</span>
      <span class="upload-drop__formats">mp4 · mov · mkv · avi · webm · flv</span>
    </div>

    <!-- 已选文件（待开始） -->
    <div v-if="phase === 'idle' && file !== null" class="file-card">
      <div class="file-card__main">
        <span class="file-card__icon" aria-hidden="true"><QIcon name="film" :size="18" /></span>
        <div class="file-card__meta">
          <span class="file-card__name" :title="fileName">{{ fileName }}</span>
          <span class="file-card__size">{{ formatFileSize(fileSize) }} · 待上传</span>
        </div>
      </div>
      <div class="file-card__actions">
        <button class="q-btn q-btn--primary q-btn--sm" type="button" @click="startUpload">
          <QIcon name="upload" :size="13" />
          开始上传
        </button>
        <button class="q-btn q-btn--outline q-btn--sm" type="button" @click="triggerSelect">
          重新选择
        </button>
        <button class="q-btn q-btn--ghost q-btn--sm" type="button" @click="resetTask">清除</button>
      </div>
    </div>

    <!-- 指纹 / 分片 / 合并进度 -->
    <div v-if="showUploadProgress" class="progress-card">
      <div class="progress-card__head">
        <span class="progress-card__label">{{ phaseText }}</span>
        <span class="progress-card__percent">{{ uploadPercent }}%</span>
      </div>
      <div
        class="q-progress"
        role="progressbar"
        aria-valuemin="0"
        aria-valuemax="100"
        :aria-valuenow="uploadPercent"
      >
        <div
          class="q-progress__bar"
          :class="{
            'q-progress__bar--success': progressStatus === 'success',
            'q-progress__bar--exception': progressStatus === 'exception',
            'q-progress__bar--warning': progressStatus === 'warning',
          }"
          :style="{ width: `${uploadPercent}%` }"
        />
      </div>
      <div class="progress-card__meta">
        <span v-if="phase === 'hashing'">正在读取抽样块并计算 SHA-256 指纹…</span>
        <span v-else-if="phase === 'uploading'">
          分片 {{ uploadedCount }} / {{ totalChunks }} · 速度 {{ speedText }}
        </span>
        <span v-else-if="phase === 'merging'">正在合并分片并创建剧集，请稍候…</span>
        <span v-else>已上传 {{ uploadedCount }} / {{ totalChunks }} 个分片，重新上传可继续续传</span>
      </div>
    </div>

    <!-- 上传失败 -->
    <div v-if="phase === 'failed' && failureStage === 'upload'" class="error-box" role="alert">
      <QIcon name="alert" :size="14" />
      <span>{{ uploadError || '上传失败' }}</span>
    </div>
    <div v-if="phase === 'failed' && failureStage === 'upload'" class="panel-actions">
      <button class="q-btn q-btn--primary" type="button" @click="handleRetryUpload">
        <QIcon name="refresh" :size="14" />
        重新上传（自动续传）
      </button>
      <button class="q-btn q-btn--outline" type="button" @click="resetTask">重新选择文件</button>
    </div>

    <!-- 转码进度 -->
    <div v-if="showTranscode" class="transcode-card">
      <div class="transcode-card__head">
        <div class="transcode-card__tags">
          <span v-if="phase === 'canceled'" class="q-tag q-tag--info">已取消</span>
          <span
            v-else
            class="q-tag"
            :class="[
              `q-tag--${transcodeTagType}`,
              { 'q-tag--dot': transcodeStatus === 'PENDING' || transcodeStatus === 'PROCESSING' },
            ]"
          >
            {{ transcodeStatusText }}
          </span>
          <span v-if="transcodeMode !== null && phase !== 'canceled'" class="q-tag q-tag--info">
            {{ modeText }}
          </span>
          <span v-if="episodeId !== null" class="transcode-card__episode">剧集 #{{ episodeId }}</span>
        </div>
        <span v-if="phase === 'transcoding'" class="transcode-card__percent">
          {{ transcodeProgress }}%
        </span>
      </div>

      <div
        class="q-progress"
        role="progressbar"
        aria-valuemin="0"
        aria-valuemax="100"
        :aria-valuenow="transcodeProgress"
      >
        <div
          class="q-progress__bar"
          :class="{
            'q-progress__bar--success': progressStatus === 'success',
            'q-progress__bar--exception': progressStatus === 'exception',
            'q-progress__bar--warning': progressStatus === 'warning',
          }"
          :style="{ width: `${transcodeProgress}%` }"
        />
      </div>

      <p class="transcode-card__desc">
        <template v-if="phase === 'transcoding'">
          后台正在转码为 HLS，每 2 秒自动刷新进度；可离开本页稍后回到视频编辑页查看。
        </template>
        <template v-else-if="phase === 'done'">转码完成，剧集已可预览播放。</template>
        <template v-else-if="phase === 'canceled'">转码已取消，可在视频编辑页重新上传或稍后处理。</template>
        <template v-else>{{ transcodeError || '转码失败，请根据失败原因排查后重试。' }}</template>
      </p>

      <div class="transcode-card__actions">
        <button
          v-if="phase === 'transcoding'"
          class="q-btn q-btn--outline"
          type="button"
          :disabled="transcodeActionLoading"
          @click="handleCancelTranscode"
        >
          {{ transcodeActionLoading ? '取消中…' : '取消转码' }}
        </button>
        <button
          v-if="phase === 'failed'"
          class="q-btn q-btn--primary"
          type="button"
          :disabled="transcodeActionLoading"
          @click="handleRetryTranscode"
        >
          <QIcon name="refresh" :size="14" />
          {{ transcodeActionLoading ? '重试中…' : '重试转码' }}
        </button>
        <button
          v-if="phase === 'done' || phase === 'canceled'"
          class="q-btn q-btn--primary"
          type="button"
          @click="resetTask"
        >
          <QIcon name="plus" :size="14" />
          继续上传下一集
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '../styles/variables' as *;

.upload-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ---------------- 面板头 ---------------- */
.upload-panel__head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.upload-panel__intro {
  min-width: 0;
}

.upload-panel__title {
  font-size: var(--q-fs-14);
  font-weight: 600;
  line-height: var(--q-lh-20);
  color: var(--q-text);
}

.upload-panel__hint {
  margin-top: 3px;
  font-size: var(--q-fs-12);
  line-height: var(--q-lh-18);
  color: var(--q-text-tertiary);
}

.upload-panel__state {
  display: flex;
  flex: none;
  gap: 8px;
  align-items: center;
}

.upload-panel__file-input {
  display: none;
}

/* ---------------- 拖拽 / 点击选择区 ---------------- */
.upload-drop {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  justify-content: center;
  padding: 34px 20px;
  cursor: pointer;
  background: var(--q-fill-quaternary);
  border: 1px dashed var(--q-border-tertiary);
  border-radius: var(--q-r-xl);
  transition: background-color var(--q-dur-fast) var(--q-ease),
    border-color var(--q-dur-fast) var(--q-ease);

  &:hover,
  &--active {
    background: var(--q-primary-bg);
    border-color: var(--q-primary-border);
  }

  &:focus-visible {
    outline: none;
    box-shadow: 0 0 0 2px var(--q-primary-border);
  }
}

.upload-drop__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin-bottom: 4px;
  color: var(--q-primary-text);
  background: var(--q-bg-container);
  border-radius: var(--q-r-xl);
  box-shadow: 0 0 0 1px var(--q-border-tertiary) inset;
}

.upload-drop__title {
  font-size: var(--q-fs-13);
  font-weight: 500;
  color: var(--q-text);
}

.upload-drop__sub {
  font-size: var(--q-fs-12);
  color: var(--q-text-tertiary);
}

.upload-drop__formats {
  margin-top: 4px;
  font-family: var(--q-font-mono);
  font-size: var(--q-fs-11);
  letter-spacing: 0.02em;
  color: var(--q-text-quaternary);
}

/* ---------------- 已选文件卡 ---------------- */
.file-card {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: var(--q-fill-tertiary);
  border-radius: var(--q-r-xl);
}

.file-card__main {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.file-card__icon {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  color: var(--q-primary-text);
  background: var(--q-bg-container);
  border-radius: var(--q-r-lg);
  box-shadow: 0 0 0 1px var(--q-border-quaternary) inset;
}

.file-card__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.file-card__name {
  max-width: 460px;
  overflow: hidden;
  font-size: var(--q-fs-13);
  font-weight: 500;
  color: var(--q-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-card__size {
  font-size: var(--q-fs-12);
  color: var(--q-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.file-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* ---------------- 进度卡 ---------------- */
.progress-card,
.transcode-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  background: var(--q-fill-quaternary);
  border-radius: var(--q-r-xl);
  box-shadow: 0 0 0 1px var(--q-border-quaternary) inset;
}

.progress-card__head,
.transcode-card__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  justify-content: space-between;
}

.progress-card__label {
  font-size: var(--q-fs-13);
  font-weight: 500;
  color: var(--q-text);
}

.progress-card__percent,
.transcode-card__percent {
  font-size: var(--q-fs-13);
  font-weight: 600;
  color: var(--q-primary-text);
  font-variant-numeric: tabular-nums;
}

.progress-card__meta {
  font-size: var(--q-fs-12);
  line-height: var(--q-lh-18);
  color: var(--q-text-tertiary);
}

/* 4px 自绘进度条：默认主色，按状态切换语义色 */
.q-progress__bar {
  background: var(--q-primary);

  &--success {
    background: var(--q-success);
  }

  &--exception {
    background: var(--q-error);
  }

  &--warning {
    background: var(--q-warning);
  }
}

/* ---------------- 上传失败 ---------------- */
.error-box {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
  font-size: var(--q-fs-12);
  line-height: var(--q-lh-18);
  color: var(--q-error);
  background: var(--q-error-bg);
  border-radius: var(--q-r-lg);
}

.panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* ---------------- 转码卡 ---------------- */
.transcode-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.transcode-card__episode {
  font-size: var(--q-fs-12);
  color: var(--q-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.transcode-card__desc {
  font-size: var(--q-fs-12);
  line-height: var(--q-lh-18);
  color: var(--q-text-secondary);
}

.transcode-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* ---------------- 响应式 ---------------- */
@include q-mobile {
  .upload-drop {
    padding: 24px 12px;
  }

  .file-card__name {
    max-width: 100%;
  }

  .file-card__actions,
  .panel-actions,
  .transcode-card__actions {
    width: 100%;
  }
}
</style>
