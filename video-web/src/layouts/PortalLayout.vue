<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLogo from '@/components/AppLogo.vue'
import QIcon from '@/components/QIcon.vue'
import SiteGate from '@/components/SiteGate.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import { listCategories } from '@/api/category'
import { listPublicVideos } from '@/api/play'
import type { CategoryItem } from '@/types/category'
import type { PublicVideoItem } from '@/types/video'

/**
 * 前台外壳（Qoder Workbench 风格）：
 * 40px 顶栏（品牌 + 中部搜索 + 动作区）/ 248px 左侧导航 / 主内容 / 280px 右栏推荐。
 */
const route = useRoute()
const router = useRouter()

const categories = ref<CategoryItem[]>([])
const recent = ref<PublicVideoItem[]>([])
const keyword = ref('')

/** 播放页进入沉浸模式：隐藏全局侧栏与右栏，把宽度让给播放器 */
const immersive = computed<boolean>(() => route.name === 'portal-player')

const activeCategoryId = computed<number>(() => {
  const raw = route.query.categoryId
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0
})

function syncKeyword(): void {
  const raw = route.query.keyword
  keyword.value = typeof raw === 'string' ? raw : ''
}

function goCategory(categoryId: number): void {
  router.push({ path: '/', query: categoryId > 0 ? { categoryId: String(categoryId) } : {} })
}

function submitSearch(): void {
  const value = keyword.value.trim()
  router.push({ path: '/', query: value ? { keyword: value } : {} })
}

function clearSearch(): void {
  keyword.value = ''
  submitSearch()
}

function openVideo(videoId: number): void {
  router.push(`/videos/${videoId}`)
}

function handleSiteVerified(needsReload: boolean): void {
  if (needsReload) window.location.reload()
}

onMounted(async () => {
  syncKeyword()
  try {
    categories.value = await listCategories()
  } catch {
    categories.value = []
  }
  try {
    const page = await listPublicVideos({ page: 1, size: 5 })
    recent.value = page.records
  } catch {
    recent.value = []
  }
})

watch(
  () => route.query.keyword,
  () => syncKeyword(),
)
</script>

<template>
  <div class="portal" :class="{ 'portal--immersive': immersive }">
    <SiteGate @verified="handleSiteVerified" />

    <header class="portal-bar">
      <RouterLink class="portal-bar__brand" to="/" aria-label="vidkeep 首页">
        <AppLogo size="md" />
      </RouterLink>

      <div class="portal-bar__center">
        <label class="portal-search">
          <QIcon name="search" :size="14" />
          <input
            v-model="keyword"
            type="search"
            placeholder="搜索视频标题"
            aria-label="搜索视频"
            @keyup.enter="submitSearch"
          />
          <button
            v-if="keyword"
            type="button"
            class="portal-search__clear"
            aria-label="清除搜索"
            @click="clearSearch"
          >
            <QIcon name="close" :size="12" />
          </button>
        </label>
      </div>

      <div class="portal-bar__actions">
        <ThemeToggle />
        <RouterLink class="q-btn q-btn--ghost q-btn--sm portal-bar__admin" to="/admin/login">
          <QIcon name="settings" :size="14" />
          <span>管理后台</span>
        </RouterLink>
      </div>
    </header>

    <div class="portal-body">
      <aside class="portal-side" aria-label="站点导航">
        <nav class="portal-nav">
          <RouterLink
            class="portal-nav__item"
            :class="{ 'is-active': !activeCategoryId && !keyword }"
            to="/"
          >
            <QIcon name="home" :size="16" />
            <span>首页</span>
          </RouterLink>
          <RouterLink
            class="portal-nav__item"
            :class="{ 'is-active': Boolean(keyword) }"
            :to="{ path: '/', query: keyword ? { keyword } : {} }"
          >
            <QIcon name="search" :size="16" />
            <span>搜索</span>
          </RouterLink>
        </nav>

        <div class="portal-side__group">
          <p class="portal-side__label">分类</p>
          <nav class="portal-nav">
            <button
              v-for="category in categories"
              :key="category.id"
              type="button"
              class="portal-nav__item portal-nav__item--button"
              :class="{ 'is-active': activeCategoryId === category.id }"
              @click="goCategory(category.id)"
            >
              <QIcon name="tag" :size="16" />
              <span class="q-nowrap">{{ category.name }}</span>
            </button>
          </nav>
        </div>

        <div class="portal-side__foot">
          <RouterLink class="portal-side__foot-link" to="/admin/login">管理后台</RouterLink>
          <p class="portal-side__version">vidkeep · 自有内容视频站</p>
        </div>
      </aside>

      <main class="portal-main">
        <RouterView />
      </main>

      <aside class="portal-rail" aria-label="最近更新">
        <section class="rail-section">
          <header class="rail-section__head">
            <h2 class="rail-section__title">最近添加</h2>
            <QIcon name="clock" :size="14" />
          </header>
          <div v-if="recent.length === 0" class="rail-section__empty">暂无视频</div>
          <ul v-else class="rail-list">
            <li v-for="video in recent" :key="video.id">
              <button type="button" class="rail-item" @click="openVideo(video.id)">
                <img
                  v-if="video.coverUrl"
                  class="rail-item__cover"
                  :src="video.coverUrl"
                  alt=""
                  loading="lazy"
                />
                <span v-else class="rail-item__cover rail-item__cover--empty">
                  <QIcon name="film" :size="16" />
                </span>
                <span class="rail-item__body">
                  <span class="rail-item__title">{{ video.title }}</span>
                  <span class="rail-item__meta">
                    {{ video.episodeCount }} 集 · {{ video.viewCount }} 次播放
                  </span>
                </span>
              </button>
            </li>
          </ul>
        </section>

        <section class="rail-section rail-section--note">
          <p class="rail-note__title">vidkeep</p>
          <p class="rail-note__desc">
            管理员在后台上传自有视频，系统自动转码为 HLS；支持断点续传、秒传与进度记忆。
          </p>
          <RouterLink class="q-btn q-btn--outline q-btn--sm rail-note__btn" to="/admin/login">
            进入管理后台
          </RouterLink>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped lang="scss">
.portal {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  color: var(--q-text);
  background: var(--q-bg-layout);
}

/* ---------- 40px 顶栏 ---------- */
.portal-bar {
  display: flex;
  flex: none;
  align-items: center;
  gap: 12px;
  height: var(--q-titlebar-h);
  padding: 0 12px;
  background: var(--q-bg-layout);
  border-bottom: 1px solid var(--q-border-quaternary);
}

.portal-bar__brand {
  flex: none;
  padding: 0 4px;
}

.portal-bar__center {
  display: flex;
  flex: 1;
  justify-content: center;
  min-width: 0;
}

.portal-search {
  display: flex;
  align-items: center;
  gap: 6px;
  width: min(420px, 100%);
  height: var(--q-ctl-md);
  padding: 0 10px;
  color: var(--q-text-tertiary);
  cursor: text;
  background: var(--q-fill-tertiary);
  border-radius: var(--q-r-control);
  transition: background-color var(--q-dur-fast) var(--q-ease),
    box-shadow var(--q-dur-fast) var(--q-ease);

  &:focus-within {
    background: var(--q-bg-container);
    box-shadow: 0 0 0 1px var(--q-border) inset;
  }

  input {
    flex: 1;
    min-width: 0;
    height: 100%;
    font-size: var(--q-fs-13);
    color: var(--q-text);
    background: transparent;
    border: none;
    outline: none;

    &::-webkit-search-cancel-button {
      display: none;
    }
  }
}

.portal-search__clear {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  color: var(--q-text-tertiary);
  cursor: pointer;
  background: var(--q-fill-tertiary);
  border-radius: 50%;

  &:hover {
    color: var(--q-text);
    background: var(--q-fill-secondary);
  }
}

.portal-bar__actions {
  display: flex;
  flex: none;
  gap: 2px;
  align-items: center;
}

.portal-bar__admin {
  gap: 6px;
}

/* ---------- 三栏主体 ---------- */
.portal-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.portal-side {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 18px;
  width: var(--q-sidebar-w);
  padding: 12px 8px;
  overflow-y: auto;
  background: var(--q-bg-container);
  border-right: 1px solid var(--q-border-quaternary);
}

.portal-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.portal-nav__item {
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

.portal-nav__item--button {
  width: 100%;
  text-align: left;
  cursor: pointer;
}

.portal-side__group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.portal-side__label {
  padding: 0 10px;
  margin-bottom: 2px;
  font-size: var(--q-fs-11);
  font-weight: 500;
  color: var(--q-text-quaternary);
}

.portal-side__foot {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px;
  margin-top: auto;
}

.portal-side__foot-link {
  font-size: var(--q-fs-12);
  color: var(--q-text-secondary);

  &:hover {
    color: var(--q-primary-text);
  }
}

.portal-side__version {
  font-size: var(--q-fs-11);
  color: var(--q-text-quaternary);
}

.portal-main {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  background: var(--q-bg-layout);
}

/* 播放页沉浸：隐藏两侧栏，播放器占满宽度 */
.portal--immersive {
  .portal-side,
  .portal-rail {
    display: none;
  }
}

/* ---------- 280px 右栏 ---------- */
.portal-rail {
  flex: none;
  width: var(--q-rail-w);
  padding: 16px 12px;
  overflow-y: auto;
  background: var(--q-bg-container);
  border-left: 1px solid var(--q-border-quaternary);
}

.rail-section + .rail-section {
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid var(--q-border-quaternary);
}

.rail-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 8px;
  color: var(--q-text-tertiary);
}

.rail-section__title {
  font-size: var(--q-fs-12);
  font-weight: 500;
  color: var(--q-text-secondary);
}

.rail-section__empty {
  padding: 12px 4px;
  font-size: var(--q-fs-12);
  color: var(--q-text-quaternary);
}

.rail-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.rail-item {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 6px;
  text-align: left;
  cursor: pointer;
  border-radius: var(--q-r-lg);
  transition: background-color var(--q-dur-fast) var(--q-ease);

  &:hover {
    background: var(--q-fill-tertiary);
  }
}

.rail-item__cover {
  flex: none;
  width: 72px;
  height: 40px;
  object-fit: cover;
  background: var(--q-bg-elevated);
  border-radius: var(--q-r-md);
}

.rail-item__cover--empty {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--q-text-quaternary);
}

.rail-item__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.rail-item__title {
  overflow: hidden;
  font-size: var(--q-fs-12);
  font-weight: 500;
  color: var(--q-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rail-item__meta {
  font-size: var(--q-fs-11);
  color: var(--q-text-tertiary);
}

.rail-section--note {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  background: var(--q-bg-elevated);
  border-radius: var(--q-r-xl);
}

.rail-note__title {
  font-family: var(--q-font-brand);
  font-size: var(--q-fs-14);
  font-weight: 500;
  color: var(--q-text);
}

.rail-note__desc {
  font-size: var(--q-fs-11);
  line-height: var(--q-lh-18);
  color: var(--q-text-tertiary);
}

.rail-note__btn {
  align-self: flex-start;
  margin-top: 4px;
}

@media (max-width: 1280px) {
  .portal-rail {
    display: none;
  }
}

@media (max-width: 1100px) {
  .portal-side {
    display: none;
  }
}

@media (max-width: 720px) {
  .portal-bar {
    gap: 8px;
    padding: 0 8px;
  }

  .portal-bar__admin span {
    display: none;
  }
}
</style>
