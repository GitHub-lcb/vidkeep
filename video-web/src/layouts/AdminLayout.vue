<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLogo from '@/components/AppLogo.vue'
import QIcon from '@/components/QIcon.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import { useUserStore } from '@/stores/user'

/**
 * 管理后台外壳（Qoder Workbench 风格）：
 * 40px 顶栏（品牌 + 页面标题 + 动作）/ 248px 导航侧栏 / 内容区（bg-elevated + 白色卡片）。
 */
interface NavItem {
  path: string
  label: string
  icon: 'home' | 'film' | 'upload' | 'folder' | 'logs'
}

const CONTENT_NAV: NavItem[] = [
  { path: '/admin/dashboard', label: '概览', icon: 'home' },
  { path: '/admin/videos', label: '作品管理', icon: 'film' },
  { path: '/admin/upload', label: '视频上传', icon: 'upload' },
  { path: '/admin/categories', label: '分类管理', icon: 'folder' },
]

const SYSTEM_NAV: NavItem[] = [{ path: '/admin/logs', label: '操作日志', icon: 'logs' }]

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const drawerOpen = ref(false)
const pageTitle = computed<string>(() =>
  typeof route.meta.title === 'string' ? route.meta.title : '管理后台',
)
const avatarLetter = computed<string>(() => (userStore.displayName || 'A').slice(0, 1).toUpperCase())
const accountName = computed<string>(() => userStore.user?.username ?? 'admin')

function isActive(path: string): boolean {
  return route.path === path || route.path.startsWith(`${path}/`)
}

function closeDrawer(): void {
  drawerOpen.value = false
}

async function handleLogout(): Promise<void> {
  await userStore.logout()
  await router.replace('/admin/login')
}

watch(
  () => route.path,
  () => {
    drawerOpen.value = false
  },
)
</script>

<template>
  <div class="admin">
    <header class="admin-bar">
      <RouterLink class="admin-bar__brand" to="/admin/dashboard" aria-label="vidkeep 管理后台">
        <AppLogo size="sm" />
      </RouterLink>
      <span class="admin-bar__sep" aria-hidden="true" />
      <h1 class="admin-bar__title">{{ pageTitle }}</h1>

      <div class="admin-bar__actions">
        <RouterLink class="q-btn q-btn--ghost q-btn--sm admin-bar__front" to="/">
          <QIcon name="external" :size="14" />
          <span>查看前台</span>
        </RouterLink>
        <ThemeToggle />
        <button
          type="button"
          class="q-icon-btn admin-bar__menu"
          aria-label="打开导航"
          @click="drawerOpen = !drawerOpen"
        >
          <QIcon name="menu" :size="16" />
        </button>
      </div>
    </header>

    <div class="admin-body">
      <aside class="admin-side" :class="{ 'is-open': drawerOpen }" aria-label="后台导航">
        <nav class="admin-nav">
          <p class="admin-nav__label">内容</p>
          <RouterLink
            v-for="item in CONTENT_NAV"
            :key="item.path"
            class="admin-nav__item"
            :class="{ 'is-active': isActive(item.path) }"
            :to="item.path"
          >
            <QIcon :name="item.icon" :size="16" />
            <span>{{ item.label }}</span>
          </RouterLink>

          <p class="admin-nav__label admin-nav__label--gap">系统</p>
          <RouterLink
            v-for="item in SYSTEM_NAV"
            :key="item.path"
            class="admin-nav__item"
            :class="{ 'is-active': isActive(item.path) }"
            :to="item.path"
          >
            <QIcon :name="item.icon" :size="16" />
            <span>{{ item.label }}</span>
          </RouterLink>
        </nav>

        <div class="admin-user">
          <span class="admin-user__avatar">{{ avatarLetter }}</span>
          <span class="admin-user__body">
            <span class="admin-user__name">{{ userStore.displayName }}</span>
            <span class="admin-user__role">{{ accountName }}</span>
          </span>
          <button type="button" class="q-icon-btn" aria-label="退出登录" @click="handleLogout">
            <QIcon name="logout" :size="15" />
          </button>
        </div>
      </aside>

      <button
        v-if="drawerOpen"
        type="button"
        class="admin-mask"
        aria-label="关闭导航"
        @click="closeDrawer"
      />

      <main class="admin-main">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.admin {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  color: var(--q-text);
  background: var(--q-bg-layout);
}

/* ---------- 40px 顶栏 ---------- */
.admin-bar {
  display: flex;
  flex: none;
  align-items: center;
  gap: 10px;
  height: var(--q-titlebar-h);
  padding: 0 12px;
  background: var(--q-bg-layout);
  border-bottom: 1px solid var(--q-border-quaternary);
}

.admin-bar__brand {
  flex: none;
  padding: 0 2px;
}

.admin-bar__sep {
  width: 1px;
  height: 16px;
  background: var(--q-border-tertiary);
}

.admin-bar__title {
  font-size: var(--q-fs-13);
  font-weight: 500;
  color: var(--q-text-secondary);
  white-space: nowrap;
}

.admin-bar__actions {
  display: flex;
  gap: 2px;
  align-items: center;
  margin-left: auto;
}

.admin-bar__front {
  gap: 6px;
}

.admin-bar__menu {
  display: none;
}

/* ---------- 侧栏 + 内容 ---------- */
.admin-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.admin-side {
  display: flex;
  flex: none;
  flex-direction: column;
  width: var(--q-sidebar-w);
  padding: 12px 8px;
  background: var(--q-bg-container);
  border-right: 1px solid var(--q-border-quaternary);
}

.admin-nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
}

.admin-nav__label {
  padding: 0 10px;
  margin: 6px 0 2px;
  font-size: var(--q-fs-11);
  font-weight: 500;
  color: var(--q-text-quaternary);
}

.admin-nav__label--gap {
  margin-top: 18px;
}

.admin-nav__item {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 10px;
  font-size: var(--q-fs-13);
  color: var(--q-text-secondary);
  border-radius: var(--q-r-lg);
  transition: color var(--q-dur-fast) var(--q-ease), background-color var(--q-dur-fast) var(--q-ease);

  &:hover {
    color: var(--q-text);
    background: var(--q-fill-tertiary);
  }

  &.is-active {
    font-weight: 500;
    color: var(--q-text);
    background: var(--q-fill-secondary);
  }
}

.admin-user {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 8px;
  margin-top: 8px;
  border-top: 1px solid var(--q-border-quaternary);
}

.admin-user__avatar {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  font-size: var(--q-fs-11);
  font-weight: 600;
  color: var(--q-primary-text);
  background: var(--q-primary-bg);
  border-radius: 50%;
}

.admin-user__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  line-height: 1.3;
}

.admin-user__name {
  overflow: hidden;
  font-size: var(--q-fs-12);
  font-weight: 500;
  color: var(--q-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-user__role {
  font-size: var(--q-fs-11);
  color: var(--q-text-tertiary);
}

.admin-main {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  background: var(--q-bg-elevated);
}

.admin-mask {
  display: none;
}

@media (max-width: 1100px) {
  .admin-side {
    position: fixed;
    top: var(--q-titlebar-h);
    bottom: 0;
    left: 0;
    z-index: 40;
    transform: translateX(-100%);
    transition: transform var(--q-dur-base) var(--q-ease);
  }

  .admin-side.is-open {
    transform: none;
  }

  .admin-mask {
    position: fixed;
    inset: var(--q-titlebar-h) 0 0;
    z-index: 30;
    display: block;
    padding: 0;
    cursor: pointer;
    background: var(--q-bg-mask);
    border: none;
  }

  .admin-bar__menu {
    display: inline-flex;
  }
}

@media (max-width: 720px) {
  .admin-bar__front span {
    display: none;
  }
}
</style>
