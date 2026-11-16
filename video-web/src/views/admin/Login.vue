<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { isBizError } from '@/api/request'
import { useUserStore } from '@/stores/user'
import type { LoginRequest } from '@/types/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive<LoginRequest>({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

/** 只接受站内相对路径，避免 redirect 被利用做站外跳转 */
function resolveRedirect(): string {
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')) {
    return redirect
  }
  return '/admin/dashboard'
}

async function handleSubmit(): Promise<void> {
  // loading 同步置位，防止回车与按钮点击重复提交
  if (loading.value || formRef.value === undefined) return
  loading.value = true

  try {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) return

    await userStore.login({ username: form.username.trim(), password: form.password })
    ElMessage.success('登录成功')
    await router.replace(resolveRedirect())
  } catch (error) {
    ElMessage.error(isBizError(error) ? error.message : '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <section class="login-card">
      <div class="login-brand">
        <span class="login-brand__mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="17" height="17">
            <path d="M9 7l8 5-8 5z" fill="currentColor" />
          </svg>
        </span>
        <span class="login-brand__name">vidkeep</span>
      </div>

      <h1 class="login-title">管理后台登录</h1>
      <p class="login-subtitle">使用管理员账号继续</p>

      <el-form
        ref="formRef"
        class="login-form"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            autocomplete="username"
            size="large"
            clearable
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            size="large"
            show-password
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <el-button
          class="login-submit"
          type="primary"
          size="large"
          native-type="submit"
          :loading="loading"
        >
          {{ loading ? '登录中…' : '登录' }}
        </el-button>
      </el-form>

      <p class="login-footnote">vidkeep · 自建视频站管理端</p>
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.login {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 40px 20px;
  overflow: hidden;
  background:
    radial-gradient(900px 520px at 50% -8%, rgba(91, 141, 239, 0.22), transparent 62%),
    radial-gradient(760px 420px at 110% 108%, rgba(91, 141, 239, 0.12), transparent 60%),
    linear-gradient(165deg, #070b11 0%, #0c121c 48%, #080d15 100%);

  // 极淡网格纹理，弱化纯色背景的空洞感
  &::before {
    position: absolute;
    inset: 0;
    pointer-events: none;
    content: '';
    background-image:
      linear-gradient(rgba(255, 255, 255, 0.025) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255, 255, 255, 0.025) 1px, transparent 1px);
    background-size: 64px 64px;
    -webkit-mask-image: radial-gradient(620px 420px at 50% 42%, #000000 18%, transparent 78%);
    mask-image: radial-gradient(620px 420px at 50% 42%, #000000 18%, transparent 78%);
  }

  @include vk-mobile {
    padding: 24px 16px;
  }
}

.login-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 400px;
  padding: 36px 34px 30px;
  background: linear-gradient(180deg, rgba(23, 31, 44, 0.94), rgba(15, 21, 31, 0.96));
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  box-shadow:
    0 24px 80px rgba(0, 0, 0, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(6px);

  @include vk-mobile {
    padding: 28px 22px 24px;
    border-radius: 14px;
  }
}

.login-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.login-brand__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  color: #ffffff;
  background: $vk-accent;
  border-radius: 9px;
  box-shadow: 0 6px 18px rgba(91, 141, 239, 0.35);
}

.login-brand__name {
  font-size: 18px;
  font-weight: 650;
  letter-spacing: 0.04em;
  color: #f3f6fb;
}

.login-title {
  margin-top: 26px;
  font-size: 20px;
  font-weight: 600;
  color: #eef2f8;
}

.login-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: #7f8b9d;
}

.login-form {
  margin-top: 26px;

  :deep(.el-form-item) {
    margin-bottom: 20px;
  }

  :deep(.el-form-item__label) {
    padding-bottom: 6px;
    font-size: 13px;
    line-height: 1.2;
    color: #9aa6b8;
  }

  :deep(.el-form-item__error) {
    padding-top: 4px;
    font-size: 12px;
    color: #f08181;
  }

  :deep(.el-input__wrapper) {
    padding: 2px 12px;
    background: rgba(255, 255, 255, 0.04);
    border-radius: 9px;
    box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.09) inset;
    transition:
      box-shadow 0.2s ease,
      background-color 0.2s ease;
  }

  :deep(.el-input__wrapper:hover) {
    box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.16) inset;
  }

  :deep(.el-input__wrapper.is-focus) {
    background: rgba(255, 255, 255, 0.06);
    box-shadow:
      0 0 0 1px #5b8def inset,
      0 0 0 3px rgba(91, 141, 239, 0.14);
  }

  :deep(.el-input__inner) {
    height: 40px;
    color: $vk-text;
  }

  :deep(.el-input__inner::placeholder) {
    color: #5f6b7d;
  }

  :deep(.el-input__inner:-webkit-autofill) {
    -webkit-text-fill-color: $vk-text;
    -webkit-box-shadow: 0 0 0 1000px rgba(20, 27, 38, 1) inset;
  }
}

.login-form .login-submit {
  width: 100%;
  height: 44px;
  margin-top: 4px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.04em;
  background: linear-gradient(180deg, #5f8ff2, #4a76d6);
  border: none;
  border-radius: 9px;
  box-shadow: 0 10px 24px rgba(74, 118, 214, 0.32);
}

.login-form .login-submit:hover,
.login-form .login-submit:focus {
  background: linear-gradient(180deg, #6d9af5, #4f7cdd);
}

.login-form .login-submit:active {
  background: linear-gradient(180deg, #4a76d6, #436bc4);
}

.login-footnote {
  margin-top: 22px;
  font-size: 12px;
  letter-spacing: 0.04em;
  text-align: center;
  color: #5f6b7d;
}
</style>
