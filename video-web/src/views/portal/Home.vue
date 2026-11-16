<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listPublicCategories, listPublicVideos } from '@/api/play'
import { isBizError } from '@/api/request'
import VideoCard from '@/components/VideoCard.vue'
import type { CategoryItem } from '@/types/category'
import type { PublicVideoItem } from '@/types/video'

/** 前台首页：分类筛选 + 标题搜索 + 视频网格分页（文档 6.2 公开列表接口） */

/** 每页数量：桌面 4 列 × 3 行 */
const PAGE_SIZE = 12

const categories = ref<CategoryItem[]>([])
const videos = ref<PublicVideoItem[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const errorMessage = ref('')

/** 当前生效的筛选条件（keyword 只在回车/点搜索后生效，避免输入每个字符都请求） */
const activeCategoryId = ref<number | null>(null)
const keyword = ref('')
const keywordInput = ref('')

/** 请求序号：筛选连续切换时丢弃过期响应，防止旧数据覆盖新结果 */
let requestSeq = 0

const totalPages = computed((): number => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const hasFilter = computed((): boolean => keyword.value.length > 0 || activeCategoryId.value !== null)

/** 加载分类；失败不阻塞视频列表，只是没有分类 chip */
async function loadCategories(): Promise<void> {
  try {
    categories.value = await listPublicCategories()
  } catch {
    categories.value = []
  }
}

/** 加载视频列表（分页 + 可选筛选） */
async function loadVideos(): Promise<void> {
  const seq = ++requestSeq
  loading.value = true
  errorMessage.value = ''

  try {
    const result = await listPublicVideos({
      page: page.value,
      size: PAGE_SIZE,
      keyword: keyword.value.length > 0 ? keyword.value : undefined,
      categoryId: activeCategoryId.value ?? undefined,
    })
    if (seq !== requestSeq) return
    videos.value = result.records
    total.value = result.total
  } catch (error) {
    if (seq !== requestSeq) return
    videos.value = []
    total.value = 0
    errorMessage.value = isBizError(error) ? error.message : '视频列表加载失败，请稍后重试'
    ElMessage.error(errorMessage.value)
  } finally {
    // 只有最后一次请求负责关闭 loading
    if (seq === requestSeq) loading.value = false
  }
}

/** 回车或点击搜索：应用关键字并从第一页重新加载 */
function handleSearch(): void {
  keyword.value = keywordInput.value.trim()
  page.value = 1
  void loadVideos()
}

/** 切换分类（null 表示全部） */
function handleCategorySelect(categoryId: number | null): void {
  if (activeCategoryId.value === categoryId) return
  activeCategoryId.value = categoryId
  page.value = 1
  void loadVideos()
}

/** 清除全部筛选条件 */
function clearFilters(): void {
  keyword.value = ''
  keywordInput.value = ''
  activeCategoryId.value = null
  page.value = 1
  void loadVideos()
}

/** 翻页：page 从 1 开始，页码非法时忽略 */
function goPage(target: number): void {
  if (target < 1 || target > totalPages.value || target === page.value) return
  page.value = target
  void loadVideos()
}

onMounted(() => {
  void loadCategories()
  void loadVideos()
})
</script>

<template>
  <div class="home">
    <section class="home-hero">
      <p class="home-hero__eyebrow">self-hosted video</p>
      <h1 class="home-hero__title">vidkeep</h1>
      <p class="home-hero__desc">自有内容，安静地看。</p>
    </section>

    <section class="home-toolbar">
      <div class="home-chips" role="group" aria-label="按分类筛选">
        <button
          type="button"
          class="home-chip"
          :class="{ 'is-active': activeCategoryId === null }"
          @click="handleCategorySelect(null)"
        >
          全部
        </button>
        <button
          v-for="item in categories"
          :key="item.id"
          type="button"
          class="home-chip"
          :class="{ 'is-active': activeCategoryId === item.id }"
          @click="handleCategorySelect(item.id)"
        >
          {{ item.name }}
        </button>
      </div>

      <form class="home-search" role="search" @submit.prevent="handleSearch">
        <input
          v-model="keywordInput"
          class="home-search__input"
          type="search"
          placeholder="搜索视频标题"
          aria-label="搜索视频标题"
        />
        <button class="home-search__button" type="submit">搜索</button>
      </form>
    </section>

    <div class="home-result">
      <p v-if="!loading && errorMessage === ''" class="home-result__text">
        {{ hasFilter ? `筛选到 ${total} 个视频` : `共 ${total} 个视频` }}
      </p>
      <button v-if="hasFilter && !loading" type="button" class="home-result__clear" @click="clearFilters">
        清除筛选
      </button>
    </div>

    <!-- 加载态：骨架卡片，避免列表高度跳变 -->
    <section v-if="loading" class="home-grid" aria-busy="true" aria-label="视频加载中">
      <div v-for="index in 8" :key="index" class="home-skeleton">
        <div class="home-skeleton__cover" />
        <div class="home-skeleton__line home-skeleton__line--title" />
        <div class="home-skeleton__line home-skeleton__line--meta" />
      </div>
    </section>

    <!-- 错误态：可重试 -->
    <section v-else-if="errorMessage !== ''" class="home-state">
      <p class="home-state__icon" aria-hidden="true">!</p>
      <h2 class="home-state__title">视频加载失败</h2>
      <p class="home-state__desc">{{ errorMessage }}</p>
      <button type="button" class="home-state__action" @click="loadVideos">重新加载</button>
    </section>

    <!-- 空态 -->
    <section v-else-if="videos.length === 0" class="home-state">
      <p class="home-state__icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="26" height="26">
          <path
            d="M4 6.5A2.5 2.5 0 016.5 4h11A2.5 2.5 0 0120 6.5v11a2.5 2.5 0 01-2.5 2.5h-11A2.5 2.5 0 014 17.5v-11z"
            fill="none"
            stroke="currentColor"
            stroke-width="1.4"
          />
          <path d="M10 9l5 3-5 3z" fill="currentColor" />
        </svg>
      </p>
      <h2 class="home-state__title">暂无视频</h2>
      <p class="home-state__desc">
        {{ hasFilter ? '没有匹配的视频，换个关键字或分类试试。' : '站长还没有发布视频，稍后再来看看吧。' }}
      </p>
      <button v-if="hasFilter" type="button" class="home-state__action" @click="clearFilters">
        查看全部视频
      </button>
    </section>

    <!-- 视频网格 -->
    <section v-else class="home-grid" aria-label="视频列表">
      <VideoCard v-for="video in videos" :key="video.id" :video="video" />
    </section>

    <!-- 分页：上一页 / 页码 / 下一页 -->
    <nav v-if="!loading && errorMessage === '' && totalPages > 1" class="home-pagination" aria-label="分页">
      <button
        type="button"
        class="home-pagination__button"
        :disabled="page <= 1"
        @click="goPage(page - 1)"
      >
        上一页
      </button>
      <span class="home-pagination__info">{{ page }} / {{ totalPages }}</span>
      <button
        type="button"
        class="home-pagination__button"
        :disabled="page >= totalPages"
        @click="goPage(page + 1)"
      >
        下一页
      </button>
    </nav>
  </div>
</template>

<style scoped lang="scss">
@use '../../styles/variables' as *;

.home {
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 24px 72px;

  @include vk-mobile {
    padding: 0 16px 48px;
  }
}

.home-hero {
  padding: 64px 0 30px;

  @include vk-tablet {
    padding: 48px 0 24px;
  }

  @include vk-mobile {
    padding: 36px 0 20px;
  }
}

.home-hero__eyebrow {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.32em;
  text-transform: uppercase;
  color: $vk-text-muted;
}

.home-hero__title {
  margin-top: 14px;
  font-size: clamp(38px, 6vw, 64px);
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.03em;
  background: linear-gradient(180deg, #ffffff 12%, #9fb0c6 92%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.home-hero__desc {
  margin-top: 14px;
  font-size: 14px;
  color: #a6b2c3;
}

.home-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 0;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);

  @include vk-tablet {
    flex-direction: column;
    align-items: stretch;
  }
}

.home-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.home-chip {
  padding: 6px 13px;
  font-size: 12.5px;
  color: $vk-text-muted;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  transition:
    color 0.18s ease,
    border-color 0.18s ease,
    background-color 0.18s ease;

  &:hover {
    color: $vk-text;
    border-color: rgba(255, 255, 255, 0.2);
  }

  &.is-active {
    color: #ffffff;
    background: rgba(91, 141, 239, 0.16);
    border-color: rgba(91, 141, 239, 0.62);
    box-shadow: inset 0 0 0 1px rgba(91, 141, 239, 0.18);
  }
}

.home-search {
  display: flex;
  flex-shrink: 0;
  gap: 8px;

  @include vk-tablet {
    width: 100%;
  }
}

.home-search__input {
  width: 220px;
  height: 36px;
  padding: 0 12px;
  font-size: 13px;
  color: $vk-text;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease;

  &::placeholder {
    color: #5f6b7d;
  }

  &:focus {
    background: rgba(255, 255, 255, 0.06);
    border-color: rgba(91, 141, 239, 0.62);
    outline: none;
  }

  @include vk-tablet {
    flex: 1;
    width: auto;
  }
}

.home-search__button {
  height: 36px;
  padding: 0 16px;
  font-size: 13px;
  color: #ffffff;
  cursor: pointer;
  background: linear-gradient(180deg, #5f8ff2, #4a76d6);
  border: none;
  border-radius: 8px;
  transition: background 0.18s ease;

  &:hover {
    background: linear-gradient(180deg, #6d9af5, #4f7cdd);
  }
}

.home-result {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 0 2px;
}

.home-result__text {
  font-size: 12.5px;
  color: $vk-text-muted;
}

.home-result__clear {
  font-size: 12.5px;
  color: $vk-text-muted;
  cursor: pointer;
  background: none;
  border: none;
  transition: color 0.18s ease;

  &:hover {
    color: $vk-text;
  }
}

.home-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
  margin-top: 16px;

  @include vk-tablet {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 16px;
  }

  @include vk-mobile {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 13px;
  }

  @media (max-width: 380px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

/* 骨架屏：渐变扫光，避免纯灰块 */
.home-skeleton {
  overflow: hidden;
  padding-bottom: 14px;
  background: rgba(255, 255, 255, 0.025);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
}

.home-skeleton__cover {
  width: 100%;
  aspect-ratio: 16 / 9;
  background: linear-gradient(100deg, #131b28 30%, #1b2536 50%, #131b28 70%);
  background-size: 220% 100%;
  animation: home-shimmer 1.4s ease-in-out infinite;
}

.home-skeleton__line {
  height: 10px;
  margin: 12px 13px 0;
  background: linear-gradient(100deg, #131b28 30%, #1b2536 50%, #131b28 70%);
  background-size: 220% 100%;
  border-radius: 5px;
  animation: home-shimmer 1.4s ease-in-out infinite;
}

.home-skeleton__line--title {
  width: 72%;
}

.home-skeleton__line--meta {
  width: 42%;
  margin-top: 9px;
  height: 8px;
}

@keyframes home-shimmer {
  0% {
    background-position: 120% 0;
  }

  100% {
    background-position: -120% 0;
  }
}

.home-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 72px 20px 76px;
  text-align: center;
}

.home-state__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: 6px;
  font-size: 22px;
  color: #7d8ca0;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 16px;
}

.home-state__title {
  font-size: 16px;
  font-weight: 600;
  color: $vk-text;
}

.home-state__desc {
  max-width: 28em;
  font-size: 13px;
  line-height: 1.7;
  color: $vk-text-muted;
}

.home-state__action {
  margin-top: 12px;
  padding: 8px 18px;
  font-size: 13px;
  color: $vk-text;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 8px;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.09);
    border-color: rgba(255, 255, 255, 0.24);
  }
}

.home-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding-top: 36px;
}

.home-pagination__button {
  padding: 7px 16px;
  font-size: 13px;
  color: $vk-text;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease;

  &:hover:not(:disabled) {
    background: rgba(255, 255, 255, 0.08);
    border-color: rgba(91, 141, 239, 0.5);
  }

  &:disabled {
    color: #4c5666;
    cursor: not-allowed;
    border-color: rgba(255, 255, 255, 0.06);
  }
}

.home-pagination__info {
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
  color: $vk-text-muted;
}
</style>
