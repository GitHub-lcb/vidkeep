<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import QIcon from '@/components/QIcon.vue'
import VideoCard from '@/components/VideoCard.vue'
import { listCategories } from '@/api/category'
import { listPublicVideos } from '@/api/play'
import { isBizError } from '@/api/request'
import type { CategoryItem } from '@/types/category'
import type { PublicVideoItem } from '@/types/video'

/**
 * 前台首页：最近更新（featured + 列表）+ 分类筛选 + 搜索 + 分页。
 * 视觉：Qoder 规范——页面标题 18/600、工具栏 28px 控件、卡片网格、克制留白。
 */
const PAGE_SIZE = 12

const route = useRoute()
const router = useRouter()

const categories = ref<CategoryItem[]>([])
const videos = ref<PublicVideoItem[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const errorMessage = ref('')

const keyword = computed<string>(() =>
  typeof route.query.keyword === 'string' ? route.query.keyword : '',
)
const categoryId = computed<number>(() => {
  const parsed = Number(route.query.categoryId)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0
})
const activeCategoryName = computed<string>(() => {
  if (categoryId.value === 0) return '视频库'
  return categories.value.find((item) => item.id === categoryId.value)?.name ?? '分类'
})
const totalPages = computed<number>(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const showFeatured = computed<boolean>(
  () => !loading.value && !keyword.value && categoryId.value === 0 && page.value === 1 && videos.value.length > 0,
)
const featured = computed<PublicVideoItem | null>(() => (showFeatured.value ? videos.value[0] : null))
const gridVideos = computed<PublicVideoItem[]>(() =>
  showFeatured.value ? videos.value.slice(1) : videos.value,
)

function errorText(error: unknown, fallback: string): string {
  return isBizError(error) ? error.message : fallback
}

async function loadVideos(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await listPublicVideos({
      page: page.value,
      size: PAGE_SIZE,
      keyword: keyword.value || undefined,
      categoryId: categoryId.value || undefined,
    })
    videos.value = result.records
    total.value = result.total
  } catch (error) {
    videos.value = []
    total.value = 0
    errorMessage.value = errorText(error, '加载视频失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function goCategory(id: number): void {
  router.push({ path: '/', query: id > 0 ? { categoryId: String(id) } : {} })
}

function clearFilter(): void {
  router.push({ path: '/' })
}

function goPage(target: number): void {
  if (target < 1 || target > totalPages.value || target === page.value) return
  page.value = target
  void loadVideos()
}

onMounted(async () => {
  void loadVideos()
  try {
    categories.value = await listCategories()
  } catch {
    categories.value = []
  }
})

watch(
  () => [route.query.keyword, route.query.categoryId],
  () => {
    page.value = 1
    void loadVideos()
  },
)
</script>

<template>
  <div class="home q-fade-in">
    <header class="home-head">
      <div>
        <p class="home-eyebrow">VIDKEEP LIBRARY</p>
        <h1 class="home-title">{{ keyword ? '搜索结果' : activeCategoryName }}</h1>
        <p class="home-desc">
          <template v-if="keyword">关键字「{{ keyword }}」共 {{ total }} 个视频</template>
          <template v-else-if="categoryId > 0">共 {{ total }} 个视频</template>
          <template v-else>管理员上传的自有内容，自动转码为 HLS 后在此播放。</template>
        </p>
      </div>
      <div class="home-head__aside">
        <span class="home-count">{{ total }} 个视频</span>
      </div>
    </header>

    <div class="home-toolbar">
      <div class="home-chips" role="tablist" aria-label="分类筛选">
        <button
          type="button"
          class="home-chip"
          :class="{ 'is-active': categoryId === 0 }"
          @click="goCategory(0)"
        >
          全部
        </button>
        <button
          v-for="category in categories"
          :key="category.id"
          type="button"
          class="home-chip"
          :class="{ 'is-active': categoryId === category.id }"
          @click="goCategory(category.id)"
        >
          {{ category.name }}
        </button>
      </div>

      <button
        v-if="keyword || categoryId > 0"
        type="button"
        class="q-btn q-btn--text q-btn--sm"
        @click="clearFilter"
      >
        清除筛选
      </button>
    </div>

    <div v-if="errorMessage" class="home-error" role="alert">
      <QIcon name="alert" :size="16" />
      <span>{{ errorMessage }}</span>
      <button class="q-btn q-btn--outline q-btn--sm" type="button" @click="loadVideos">
        <QIcon name="refresh" :size="13" />
        重试
      </button>
    </div>

    <!-- 首屏最近更新：大卡 + 紧凑列表 -->
    <section v-if="featured" class="home-featured">
      <RouterLink class="feature" :to="`/videos/${featured.id}`">
        <span class="feature__cover">
          <img
            v-if="featured.coverUrl"
            class="feature__img"
            :src="featured.coverUrl"
            :alt="featured.title"
          />
          <span v-else class="feature__placeholder">
            <QIcon name="film" :size="28" />
          </span>
          <span class="feature__play" aria-hidden="true">
            <QIcon name="play" :size="18" />
          </span>
        </span>
        <span class="feature__body">
          <span class="feature__eyebrow">最近添加</span>
          <span class="feature__title">{{ featured.title }}</span>
          <span class="feature__meta">
            {{ featured.categoryName || '未分类' }} · {{ featured.episodeCount }} 集
          </span>
        </span>
      </RouterLink>

      <div class="home-recent">
        <p class="home-recent__label">最近更新</p>
        <ul class="home-recent__list">
          <li v-for="video in videos.slice(1, 5)" :key="video.id">
            <RouterLink class="recent-row" :to="`/videos/${video.id}`">
              <img
                v-if="video.coverUrl"
                class="recent-row__cover"
                :src="video.coverUrl"
                alt=""
                loading="lazy"
              />
              <span v-else class="recent-row__cover recent-row__cover--empty">
                <QIcon name="film" :size="14" />
              </span>
              <span class="recent-row__body">
                <span class="recent-row__title">{{ video.title }}</span>
                <span class="recent-row__meta">{{ video.episodeCount }} 集 · {{ video.categoryName || '未分类' }}</span>
              </span>
            </RouterLink>
          </li>
        </ul>
      </div>
    </section>

    <!-- 视频网格 -->
    <section class="home-section">
      <header class="home-section__head">
        <h2 class="home-section__title">全部视频</h2>
        <button
          v-if="keyword || categoryId > 0"
          type="button"
          class="q-btn q-btn--text q-btn--sm"
          @click="clearFilter"
        >
          清除筛选
        </button>
      </header>

      <div v-if="loading" class="home-grid">
        <div v-for="index in 8" :key="index" class="home-skeleton">
          <div class="q-skeleton home-skeleton__cover" />
          <div class="q-skeleton home-skeleton__line" />
          <div class="q-skeleton home-skeleton__line home-skeleton__line--short" />
        </div>
      </div>

      <div v-else-if="gridVideos.length > 0" class="home-grid">
        <VideoCard v-for="video in gridVideos" :key="video.id" :video="video" />
      </div>

      <div v-else class="q-empty">
        <div class="q-empty__stack">
          <span class="q-empty__face"><QIcon name="film" :size="18" /></span>
        </div>
        <p class="q-empty__title">{{ keyword ? '没有找到匹配的视频' : '视频库还是空的' }}</p>
        <p class="q-empty__desc">
          {{ keyword ? '试试更换关键字，或清除筛选查看全部视频。' : '管理员可以在后台上传第一个视频。' }}
        </p>
        <div class="q-empty__actions">
          <button v-if="keyword || categoryId > 0" class="q-btn q-btn--outline q-btn--sm" type="button" @click="clearFilter">
            查看全部视频
          </button>
          <RouterLink v-else class="q-btn q-btn--outline q-btn--sm" to="/admin/upload">
            <QIcon name="upload" :size="14" />
            去上传
          </RouterLink>
        </div>
      </div>

      <div v-if="!loading && totalPages > 1" class="home-pager">
        <button
          type="button"
          class="q-btn q-btn--outline q-btn--sm"
          :disabled="page <= 1"
          @click="goPage(page - 1)"
        >
          <QIcon name="chevron-left" :size="14" />
          上一页
        </button>
        <span class="home-pager__text">{{ page }} / {{ totalPages }}</span>
        <button
          type="button"
          class="q-btn q-btn--outline q-btn--sm"
          :disabled="page >= totalPages"
          @click="goPage(page + 1)"
        >
          下一页
          <QIcon name="chevron-right" :size="14" />
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.home {
  width: 100%;
  max-width: 1180px;
  padding: 28px 28px 56px;
  margin: 0 auto;
}

.home-head {
  display: flex;
  gap: 16px;
  align-items: flex-end;
  justify-content: space-between;
}

.home-eyebrow {
  font-size: var(--q-fs-10);
  font-weight: 500;
  letter-spacing: 0.14em;
  color: var(--q-text-quaternary);
}

.home-title {
  margin-top: 6px;
  font-size: var(--q-fs-24);
  font-weight: 600;
  line-height: var(--q-lh-32);
  letter-spacing: -0.02em;
}

.home-desc {
  margin-top: 4px;
  font-size: var(--q-fs-12);
  line-height: var(--q-lh-18);
  color: var(--q-text-tertiary);
}

.home-count {
  font-size: var(--q-fs-12);
  color: var(--q-text-tertiary);
  font-variant-numeric: tabular-nums;
}

/* 工具栏 */
.home-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 0;
  margin-top: 8px;
  border-bottom: 1px solid var(--q-border-quaternary);
}

.home-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-width: 0;
}

.home-chip {
  height: 26px;
  padding: 0 10px;
  font-size: var(--q-fs-12);
  color: var(--q-text-secondary);
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: var(--q-r-lg);
  transition: color var(--q-dur-fast) var(--q-ease), background-color var(--q-dur-fast) var(--q-ease);

  &:hover {
    color: var(--q-text);
    background: var(--q-fill-tertiary);
  }

  &.is-active {
    font-weight: 500;
    color: var(--q-primary-text);
    background: var(--q-primary-bg);
  }
}

.home-search {
  display: flex;
  flex: none;
  gap: 6px;
  align-items: center;
}

.home-error {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 12px;
  margin-top: 16px;
  font-size: var(--q-fs-12);
  color: var(--q-error);
  background: var(--q-error-bg);
  border-radius: var(--q-r-lg);
}

/* 最近更新 */
.home-featured {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: 24px;
  margin-top: 20px;
}

.feature {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.feature__cover {
  position: relative;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  background: var(--q-fill-tertiary);
  border-radius: var(--q-r-xl);
  box-shadow: 0 0 0 1px var(--q-border-quaternary) inset;
}

.feature__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--q-dur-slow) var(--q-ease);
}

.feature__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--q-text-quaternary);
}

.feature__play {
  position: absolute;
  right: 12px;
  bottom: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  color: #fdfdfd;
  background: rgba(8, 8, 7, 0.6);
  border-radius: 50%;
  backdrop-filter: blur(4px);
  transition: transform var(--q-dur-fast) var(--q-ease);
}

.feature:hover .feature__img {
  transform: scale(1.015);
}

.feature:hover .feature__play {
  transform: scale(1.06);
}

.feature__body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-top: 12px;
}

.feature__eyebrow {
  font-size: var(--q-fs-11);
  color: var(--q-primary-text);
}

.feature__title {
  font-size: var(--q-fs-16);
  font-weight: 600;
  line-height: var(--q-lh-24);
  letter-spacing: -0.01em;
  color: var(--q-text);
}

.feature__meta {
  font-size: var(--q-fs-12);
  color: var(--q-text-tertiary);
}

.home-recent {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.home-recent__label {
  padding-bottom: 8px;
  font-size: var(--q-fs-12);
  font-weight: 500;
  color: var(--q-text-tertiary);
  border-bottom: 1px solid var(--q-border-quaternary);
}

.home-recent__list {
  display: flex;
  flex-direction: column;
}

.recent-row {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 6px;
  border-radius: var(--q-r-lg);
  transition: background-color var(--q-dur-fast) var(--q-ease);

  &:hover {
    background: var(--q-fill-tertiary);
  }
}

.recent-row__cover {
  flex: none;
  width: 72px;
  height: 40px;
  object-fit: cover;
  background: var(--q-fill-tertiary);
  border-radius: var(--q-r-md);
}

.recent-row__cover--empty {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--q-text-quaternary);
}

.recent-row__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.recent-row__title {
  overflow: hidden;
  font-size: var(--q-fs-12);
  font-weight: 500;
  color: var(--q-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-row__meta {
  font-size: var(--q-fs-11);
  color: var(--q-text-tertiary);
}

/* 网格 */
.home-section {
  margin-top: 28px;
}

.home-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
}

.home-section__title {
  font-size: var(--q-fs-14);
  font-weight: 600;
  color: var(--q-text);
}

.home-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px 16px;
}

.home-skeleton__cover {
  aspect-ratio: 16 / 9;
  border-radius: var(--q-r-surface);
}

.home-skeleton__line {
  height: 10px;
  margin-top: 10px;
  border-radius: var(--q-r-sm);
}

.home-skeleton__line--short {
  width: 55%;
  margin-top: 6px;
}

.home-pager {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: center;
  margin-top: 28px;
}

.home-pager__text {
  font-size: var(--q-fs-12);
  color: var(--q-text-tertiary);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1100px) {
  .home-featured {
    grid-template-columns: minmax(0, 1fr);
    gap: 20px;
  }

  .home-recent {
    display: none;
  }
}

@media (max-width: 900px) {
  .home-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 20px 12px;
  }
}

@media (max-width: 720px) {
  .home {
    padding: 20px 16px 40px;
  }

  .home-title {
    font-size: var(--q-fs-20);
    line-height: var(--q-lh-26);
  }

  .home-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .home-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
