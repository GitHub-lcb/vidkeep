<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import AppLogo from '@/components/AppLogo.vue'
import QIcon from '@/components/QIcon.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import { isBizError } from '@/api/request'
import { useUserStore } from '@/stores/user'
import type { LoginRequest } from '@/types/auth'

/**
 * 管理后台登录（Qoder 规范）：左侧品牌说明 + 右侧表单，36px 控件与 16px 圆角。
 */
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
    <header class="login-bar">
      <AppLogo size="sm" />
      <ThemeToggle />
    </header>

    <section class="login-card">
      <aside class="login-aside">
        <p class="login-eyebrow">VIDKEEP ADMIN</p>
        <h1 class="login-headline">回到你的视频库</h1>
        <p class="login-lead">
          上传自有视频，自动转码为 HLS，管理作品、剧集与分类；播放统计与操作审计一屏掌握。
        </p>
        <ul class="login-points">
          <li><QIcon name="check" :size="14" /> 分片上传 · 断点续传 · 秒传</li>
          <li><QIcon name="check" :size="14" /> REMUX 优先的自动转码</li>
          <li><QIcon name="check" :size="14" /> 播放地址签名与访问口令</li>
        </ul>
        <p class="login-footnote">vidkeep · 自有内容视频站</p>
      </aside>

      <div class="login-panel">
        <h2 class="login-title">登录管理后台</h2>
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
              size="large"
              placeholder="请输入用户名"
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              size="large"
              placeholder="请输入密码"
              autocomplete="current-password"
              show-password
              @keyup.enter="handleSubmit"
            />
          </el-form-item>

          <button
            class="q-btn q-btn--primary q-btn--lg q-btn--block login-submit"
            type="submit"
            :disabled="loading"
          >
            {{ loading ? '登录中…' : '登录' }}
          </button>
        </el-form>

        <RouterLink class="login-back" to="/">
          <QIcon name="arrow-left" :size="13" />
          返回前台
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.login {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 56px 20px 40px;
  background: var(--q-bg-layout);
}

.login-bar {
  position: absolute;
  top: 12px;
  right: 16px;
  left: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.login-card {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  width: 100%;
  max-width: 860px;
  overflow: hidden;
  background: var(--q-bg-container);
  border-radius: var(--q-r-2xl);
  box-shadow: var(--q-shadow-pop);
}

.login-aside {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 40px 34px;
  background: var(--q-primary-bg);
}

.login-eyebrow {
  font-size: var(--q-fs-10);
  font-weight: 500;
  letter-spacing: 0.14em;
  color: var(--q-primary-text);
}

.login-headline {
  margin-top: 4px;
  font-size: var(--q-fs-24);
  font-weight: 600;
  line-height: var(--q-lh-32);
  letter-spacing: -0.02em;
  color: var(--q-text);
}

.login-lead {
  font-size: var(--q-fs-12);
  line-height: var(--q-lh-20);
  color: var(--q-text-secondary);
}

.login-points {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;

  li {
    display: flex;
    gap: 8px;
    align-items: center;
    font-size: var(--q-fs-12);
    color: var(--q-text-secondary);
  }

  svg {
    color: var(--q-primary);
  }
}

.login-footnote {
  margin-top: auto;
  font-size: var(--q-fs-11);
  color: var(--q-text-tertiary);
}

.login-panel {
  display: flex;
  flex-direction: column;
  padding: 40px 38px 32px;
}

.login-title {
  font-size: var(--q-fs-18);
  font-weight: 600;
  line-height: var(--q-lh-24);
}

.login-subtitle {
  margin-top: 4px;
  font-size: var(--q-fs-12);
  color: var(--q-text-tertiary);
}

.login-form {
  margin-top: 24px;

  :deep(.el-form-item) {
    margin-bottom: 16px;
  }

  :deep(.el-form-item__label) {
    padding-bottom: 4px;
  }

  :deep(.el-input__wrapper) {
    padding: 0 12px;
    border-radius: var(--q-r-control);
  }

  :deep(.el-input__inner) {
    height: var(--q-ctl-lg);
  }
}

.login-submit {
  margin-top: 6px;
}

.login-back {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  align-self: flex-start;
  margin-top: 20px;
  font-size: var(--q-fs-12);
  color: var(--q-text-tertiary);

  &:hover {
    color: var(--q-text);
  }
}

@media (max-width: 820px) {
  .login-card {
    grid-template-columns: minmax(0, 1fr);
    max-width: 400px;
  }

  .login-aside {
    display: none;
  }

  .login-panel {
    padding: 30px 24px 24px;
  }
}
</style>
