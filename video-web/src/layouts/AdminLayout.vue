<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

interface AdminMenuItem {
  index: string
  title: string
}

// M1 上传与 M3 管理页已开放，菜单全部可点击
const MENU_ITEMS: AdminMenuItem[] = [
  { index: '/admin/dashboard', title: '仪表盘' },
  { index: '/admin/videos', title: '视频管理' },
  { index: '/admin/upload', title: '视频上传' },
  { index: '/admin/categories', title: '分类管理' },
]

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCompact = ref(false)
const activeMenu = computed<string>(() => route.path)
const pageTitle = computed<string>(() =>
  typeof route.meta.title === 'string' ? route.meta.title : '管理后台',
)
// 窄屏顶栏菜单展示全部已开放项
const compactMenuItems = MENU_ITEMS

let mediaQuery: MediaQueryList | null = null

function handleMediaChange(event: MediaQueryListEvent): void {
  isCompact.value = event.matches
}

onMounted(() => {
  mediaQuery = window.matchMedia('(max-width: 900px)')
  isCompact.value = mediaQuery.matches
  mediaQuery.addEventListener('change', handleMediaChange)
})

onBeforeUnmount(() => {
  mediaQuery?.removeEventListener('change', handleMediaChange)
  mediaQuery = null
})

async function handleLogout(): Promise<void> {
  await userStore.logout()
  ElMessage.success('已退出登录')
  await router.replace('/admin/login')
}
</script>

<template>
  <el-container class="admin-layout">
    <el-aside v-if="!isCompact" class="admin-aside" width="220px">
      <RouterLink class="admin-brand" to="/admin/dashboard">
        <span class="admin-brand__mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="15" height="15">
            <path d="M9 7l8 5-8 5z" fill="currentColor" />
          </svg>
        </span>
        <span class="admin-brand__text">
          <span class="admin-brand__name">vidkeep</span>
          <span class="admin-brand__sub">管理后台</span>
        </span>
      </RouterLink>

      <el-menu
        class="admin-menu"
        :default-active="activeMenu"
        :router="true"
        background-color="transparent"
        text-color="#99a5b6"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in MENU_ITEMS" :key="item.index" :index="item.index">
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="admin-body">
      <el-header class="admin-header" :height="isCompact ? 'auto' : '60px'">
        <div class="admin-header__left">
          <span v-if="!isCompact" class="admin-header__title">{{ pageTitle }}</span>
          <el-menu
            v-else
            class="admin-header__menu"
            mode="horizontal"
            :default-active="activeMenu"
            :router="true"
            :ellipsis="false"
          >
            <el-menu-item
              v-for="item in compactMenuItems"
              :key="item.index"
              :index="item.index"
            >
              {{ item.title }}
            </el-menu-item>
          </el-menu>
        </div>

        <div class="admin-header__right">
          <span class="admin-header__user">{{ userStore.displayName }}</span>
          <el-button text type="primary" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>

      <el-main class="admin-main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped lang="scss">
@use '../styles/variables' as *;

.admin-layout {
  height: 100vh;
  overflow: hidden;
  background: $vk-light-bg;
}

.admin-aside {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: $vk-ink-800;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
}

.admin-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 60px;
  padding: 0 18px;
  color: $vk-text;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.admin-brand__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 24px;
  height: 24px;
  color: #ffffff;
  background: $vk-accent;
  border-radius: 7px;
}

.admin-brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.admin-brand__name {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: 0.04em;
}

.admin-brand__sub {
  margin-top: 2px;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: #7c8798;
}

.admin-menu {
  flex: 1;
  padding: 10px 0;
  overflow-y: auto;
  border-right: none;

  :deep(.el-menu-item) {
    height: 42px;
    margin: 2px 10px;
    padding: 0 12px;
    font-size: 13px;
    color: #99a5b6;
    border-radius: 8px;

    &:hover {
      color: $vk-text;
      background: rgba(255, 255, 255, 0.05);
    }

    &.is-active {
      color: #ffffff;
      background: rgba(91, 141, 239, 0.18);
    }
  }
}

.admin-body {
  min-width: 0;
}

.admin-header {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 20px;
  background: $vk-light-surface;
  border-bottom: 1px solid $vk-light-border;

  @include vk-tablet {
    padding: 8px 12px;
  }
}

.admin-header__left {
  display: flex;
  align-items: center;
  min-width: 0;
}

.admin-header__title {
  font-size: 15px;
  font-weight: 600;
  color: $vk-light-text;
}

.admin-header__menu {
  height: 46px;
  border-bottom: none;

  :deep(.el-menu-item) {
    height: 46px;
    padding: 0 10px;
    font-size: 13px;
  }
}

.admin-header__right {
  display: flex;
  flex: none;
  align-items: center;
  gap: 6px;
}

.admin-header__user {
  max-width: 140px;
  overflow: hidden;
  font-size: 13px;
  color: #4d5868;
  white-space: nowrap;
  text-overflow: ellipsis;

  @include vk-mobile {
    display: none;
  }
}

.admin-main {
  padding: 0;
  overflow-y: auto;
  background: $vk-light-bg;
}
</style>
