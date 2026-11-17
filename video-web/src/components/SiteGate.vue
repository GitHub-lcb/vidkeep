<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { isBizError, SITE_REQUIRED_EVENT } from '@/api/request'
import { getSiteStatus, verifySitePassword } from '@/api/site'
import type { InputInstance } from 'element-plus'
import AppLogo from '@/components/AppLogo.vue'

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

const visible = ref(false)
const submitting = ref(false)
const password = ref('')
const errorMessage = ref('')
const inputRef = ref<InputInstance>()

function readPassedFlag(): boolean {
  try {
    return window.sessionStorage.getItem(PASSED_STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

function writePassedFlag(): void {
  try {
    window.sessionStorage.setItem(PASSED_STORAGE_KEY, '1')
  } catch {
    // 写入失败只影响重复弹窗体验
  }
}

function clearPassedFlag(): void {
  try {
    window.sessionStorage.removeItem(PASSED_STORAGE_KEY)
  } catch {
    // 忽略
  }
}

function openGate(): void {
  errorMessage.value = ''
  if (visible.value) return
  visible.value = true
  void nextTick(() => inputRef.value?.focus())
}

function handleSiteRequired(): void {
  clearPassedFlag()
  openGate()
}

async function checkSite(): Promise<void> {
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
    // 状态接口异常不阻塞浏览：真正需要口令时公开接口 2004 会触发事件
  }
}

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
    <Transition name="site-gate">
      <div
        v-if="visible"
        class="site-gate"
        role="dialog"
        aria-modal="true"
        aria-labelledby="site-gate-title"
      >
        <section class="site-gate__card">
          <AppLogo size="sm" />

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

            <button
              class="q-btn q-btn--primary q-btn--lg q-btn--block site-gate__submit"
              type="submit"
              :disabled="submitting"
            >
              {{ submitting ? '校验中…' : '进入站点' }}
            </button>
          </form>

          <p class="site-gate__footnote">vidkeep · 访问保护</p>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss">
.site-gate {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  background: color-mix(in srgb, var(--q-bg-layout) 55%, rgba(8, 8, 7, 0.72));
  backdrop-filter: blur(6px);
}

.site-gate__card {
  width: 100%;
  max-width: 360px;
  padding: 28px 26px 22px;
  background: var(--q-bg-container);
  border-radius: var(--q-r-2xl);
  box-shadow: var(--q-shadow-pop);
}

.site-gate__title {
  margin-top: 18px;
  font-size: var(--q-fs-16);
  font-weight: 600;
  line-height: var(--q-lh-24);
  color: var(--q-text);
}

.site-gate__desc {
  margin-top: 4px;
  font-size: var(--q-fs-12);
  line-height: var(--q-lh-18);
  color: var(--q-text-tertiary);
}

.site-gate__form {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 20px;

  :deep(.el-input__wrapper) {
    padding: 0 12px;
    border-radius: var(--q-r-control);
  }

  :deep(.el-input__inner) {
    height: var(--q-ctl-lg);
  }
}

.site-gate__error {
  font-size: var(--q-fs-11);
  line-height: var(--q-lh-16);
  color: var(--q-error);
}

.site-gate__submit {
  margin-top: 2px;
}

.site-gate__footnote {
  margin-top: 18px;
  font-size: var(--q-fs-11);
  color: var(--q-text-quaternary);
  text-align: center;
}

.site-gate-enter-active,
.site-gate-leave-active {
  transition: opacity var(--q-dur-base) var(--q-ease);
}

.site-gate-enter-active .site-gate__card,
.site-gate-leave-active .site-gate__card {
  transition: transform var(--q-dur-base) var(--q-ease), opacity var(--q-dur-base) var(--q-ease);
}

.site-gate-enter-from,
.site-gate-leave-to {
  opacity: 0;
}

.site-gate-enter-from .site-gate__card,
.site-gate-leave-to .site-gate__card {
  opacity: 0;
  transform: translateY(6px) scale(0.98);
}
</style>
