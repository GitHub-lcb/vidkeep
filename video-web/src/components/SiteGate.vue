<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { isBizError, SITE_REQUIRED_EVENT } from '@/api/request'
import { getSiteStatus, verifySitePassword } from '@/api/site'
import type { InputInstance } from 'element-plus'

/**
 * 站点访问口令门禁（F-18）：
 * - 挂载时查询 /api/public/site/status：未开启口令直接放行；开启且本会话未通过则弹窗
 * - 监听 window 的 vidkeep:site-required 事件：公开接口返回 2004 时强制弹窗
 * - 校验成功后写入 sessionStorage 标记（避免同标签页刷新重复弹）并通知父组件刷新页面
 *
 * 事件参数 needsReload：true 表示「刚通过口令」，父组件应整页刷新让后续请求带上 HttpOnly Cookie；
 * false 表示站点未开启口令或本会话已通过，父组件无需动作（避免重复刷新造成死循环）。
 */

/** 本会话口令已通过标记；刷新标签页后仍有效，Cookie 由浏览器携带 */
const PASSED_STORAGE_KEY = 'vidkeep_site_ok'

const emit = defineEmits<{ verified: [needsReload: boolean] }>()

/** 是否显示口令弹窗 */
const visible = ref(false)
/** 校验请求进行中 */
const submitting = ref(false)
const password = ref('')
/** 口令错误等提示文案 */
const errorMessage = ref('')
const inputRef = ref<InputInstance>()

function readPassedFlag(): boolean {
  try {
    return window.sessionStorage.getItem(PASSED_STORAGE_KEY) === '1'
  } catch {
    // 隐私模式下访问 sessionStorage 可能抛错，按未通过处理
    return false
  }
}

function writePassedFlag(): void {
  try {
    window.sessionStorage.setItem(PASSED_STORAGE_KEY, '1')
  } catch {
    // 写入失败只影响重复弹窗体验，不影响本次访问
  }
}

function clearPassedFlag(): void {
  try {
    window.sessionStorage.removeItem(PASSED_STORAGE_KEY)
  } catch {
    // 忽略
  }
}

/** 打开弹窗并聚焦输入框（已在弹窗时只重置错误提示） */
function openGate(): void {
  errorMessage.value = ''
  if (visible.value) return
  visible.value = true
  void nextTick(() => inputRef.value?.focus())
}

/** 收到 2004：本地「已通过」标记已不可信，清标记并强制弹窗 */
function handleSiteRequired(): void {
  clearPassedFlag()
  openGate()
}

/** 挂载时检查站点口令状态 */
async function checkSite(): Promise<void> {
  // 本会话已通过：不重复请求、不重复弹窗（刷新后 Cookie 仍然有效）
  if (readPassedFlag()) {
    emit('verified', false)
    return
  }

  try {
    const status = await getSiteStatus()
    if (!status.enabled) {
      emit('verified', false)
      return
    }
    openGate()
  } catch {
    // 状态接口异常不阻塞前台浏览：真正需要口令时公开接口会返回 2004 → 事件强制弹窗
  }
}

/** 提交口令：成功写标记并通知父组件；失败展示后端 message（2006 为口令错误） */
async function handleSubmit(): Promise<void> {
  if (submitting.value) return
  const trimmed = password.value.trim()
  if (trimmed.length === 0) {
    errorMessage.value = '请输入访问口令'
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    await verifySitePassword(trimmed)
    writePassedFlag()
    visible.value = false
    password.value = ''
    emit('verified', true)
  } catch (error) {
    errorMessage.value = isBizError(error) ? error.message : '口令校验失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  window.addEventListener(SITE_REQUIRED_EVENT, handleSiteRequired)
  void checkSite()
})

onBeforeUnmount(() => {
  window.removeEventListener(SITE_REQUIRED_EVENT, handleSiteRequired)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="site-gate-fade">
      <div
        v-if="visible"
        class="site-gate"
        role="dialog"
        aria-modal="true"
        aria-labelledby="site-gate-title"
      >
        <section class="site-gate__card">
          <div class="site-gate__brand">
            <span class="site-gate__mark" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="16" height="16">
                <path
                  d="M7 10V8a5 5 0 0 1 10 0v2m-11 0h12a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1Zm6 4v2"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.6"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </span>
            <span class="site-gate__brand-name">vidkeep</span>
          </div>

          <h2 id="site-gate-title" class="site-gate__title">访问口令</h2>
          <p class="site-gate__desc">本站已开启访问保护，请输入访问口令后继续浏览。</p>

          <form class="site-gate__form" @submit.prevent="handleSubmit">
            <el-input
              ref="inputRef"
              v-model="password"
              type="password"
              size="large"
              placeholder="请输入访问口令"
              autocomplete="current-password"
              maxlength="128"
              show-password
              :disabled="submitting"
              @keyup.enter="handleSubmit"
            />

            <p v-if="errorMessage.length > 0" class="site-gate__error" role="alert">
              {{ errorMessage }}
            </p>

            <el-button
              class="site-gate__submit"
              type="primary"
              size="large"
              native-type="submit"
              :loading="submitting"
            >
              {{ submitting ? '校验中…' : '进入站点' }}
            </el-button>
          </form>

          <p class="site-gate__footnote">vidkeep · 访问保护</p>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss">
@use '../styles/variables' as *;

.site-gate {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  background:
    radial-gradient(900px 520px at 50% -8%, rgba(91, 141, 239, 0.2), transparent 62%),
    linear-gradient(165deg, rgba(6, 9, 14, 0.94) 0%, rgba(8, 12, 19, 0.97) 100%);
  backdrop-filter: blur(6px);

  @include vk-mobile {
    padding: 20px 16px;
  }
}

.site-gate__card {
  width: 100%;
  max-width: 380px;
  padding: 32px 30px 26px;
  background: linear-gradient(180deg, rgba(23, 31, 44, 0.96), rgba(15, 21, 31, 0.98));
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  box-shadow:
    0 24px 80px rgba(0, 0, 0, 0.55),
    inset 0 1px 0 rgba(255, 255, 255, 0.04);

  @include vk-mobile {
    padding: 26px 20px 22px;
    border-radius: 14px;
  }
}

.site-gate__brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.site-gate__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #ffffff;
  background: $vk-accent;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(91, 141, 239, 0.32);
}

.site-gate__brand-name {
  font-size: 16px;
  font-weight: 650;
  letter-spacing: 0.04em;
  color: #f3f6fb;
}

.site-gate__title {
  margin-top: 24px;
  font-size: 20px;
  font-weight: 600;
  color: #eef2f8;
}

.site-gate__desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.65;
  color: #7f8b9d;
}

.site-gate__form {
  margin-top: 22px;
}

.site-gate__form :deep(.el-input__wrapper) {
  padding: 2px 12px;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 9px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.09) inset;
  transition:
    box-shadow 0.2s ease,
    background-color 0.2s ease;
}

.site-gate__form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.16) inset;
}

.site-gate__form :deep(.el-input__wrapper.is-focus) {
  background: rgba(255, 255, 255, 0.06);
  box-shadow:
    0 0 0 1px #5b8def inset,
    0 0 0 3px rgba(91, 141, 239, 0.14);
}

.site-gate__form :deep(.el-input__inner) {
  height: 40px;
  color: $vk-text;
}

.site-gate__form :deep(.el-input__inner::placeholder) {
  color: #5f6b7d;
}

.site-gate__error {
  margin-top: 10px;
  font-size: 12.5px;
  line-height: 1.5;
  color: #f08181;
}

.site-gate__submit {
  width: 100%;
  height: 44px;
  margin-top: 16px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.04em;
  background: linear-gradient(180deg, #5f8ff2, #4a76d6);
  border: none;
  border-radius: 9px;
  box-shadow: 0 10px 24px rgba(74, 118, 214, 0.32);
}

.site-gate__submit:hover,
.site-gate__submit:focus {
  background: linear-gradient(180deg, #6d9af5, #4f7cdd);
}

.site-gate__submit:active {
  background: linear-gradient(180deg, #4a76d6, #436bc4);
}

.site-gate__footnote {
  margin-top: 20px;
  font-size: 12px;
  letter-spacing: 0.04em;
  text-align: center;
  color: #5f6b7d;
}

/* 淡入淡出：口令通过后关闭弹窗时页面已准备刷新，避免视觉跳变 */
.site-gate-fade-enter-active,
.site-gate-fade-leave-active {
  transition: opacity 0.2s ease;
}

.site-gate-fade-enter-from,
.site-gate-fade-leave-to {
  opacity: 0;
}
</style>
