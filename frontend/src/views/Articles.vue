<script lang="ts">
// 按列表 URL 记住滚动位置，数据加载后再恢复，避免不同筛选或页码共用旧位置。
const savedScrollPositions = new Map<string, number>()
</script>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'

/** 文章卡片类型（来自 GET /article/list） */
interface ArticleItem {
  id: number
  title: string
  summary: string
  categoryName: string
  categorySlug: string
  /** 列表卡片显示更新时间和详情页保持一致 */
  updatedAt: string | null
  readingMinutes: number
  tags: string[]
}

/** 分类类型（来自 GET /category/list） */
interface CategoryItem {
  id: number
  name: string
  slug: string
  articleCount: number
}

/** 标签类型（来自 GET /tag/list，只含有已发布文章的标签） */
interface TagItem {
  id: number
  name: string
  articleCount: number
}

/** MyBatis-Plus 分页返回结构 */
interface ArticlePage {
  records: ArticleItem[]
  total: number
  current: number
  size: number
  pages: number
}

const PAGE_SIZE = 6

function formatDate(value?: string | null) {
  // 和详情页保持同样的粒度，卡片上多出的时分不影响现有布局
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

const route = useRoute()
const router = useRouter()

const articles = ref<ArticleItem[]>([])
const categories = ref<CategoryItem[]>([])
const tags = ref<TagItem[]>([])
/** 当前选中的分类 slug，空串表示"全部" */
const activeCategorySlug = ref('')
/** 当前选中的标签名，空串表示不按标签筛选 */
const activeTagName = ref('')
const keywordInput = ref('')
const appliedKeyword = ref('')
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
let articleRequestId = 0
onBeforeUnmount(() => { articleRequestId += 1 })

const totalArticles = computed(() =>
  categories.value.reduce((sum, category) => sum + category.articleCount, 0)
)

// 分类接口不可用时退回到文章接口返回的总数，避免“全部”显示为 0
const allArticleCount = computed(() =>
  categories.value.length ? totalArticles.value : total.value
)

/** 分类面板的选项：第一条是"全部"，其余按后端返回的顺序 */
const categoryOptions = computed(() => [
  { name: '全部', slug: '', count: allArticleCount.value },
  ...categories.value.map((category) => ({
    name: category.name,
    slug: category.slug,
    count: category.articleCount
  }))
])

/** 窄屏分类下拉框的选项：第一条是"全部"，后面各分类带上文章数 */
const categorySelectOptions = computed(() =>
  categoryOptions.value.map((option) => ({
    value: option.slug,
    label: `${option.name}（${option.count}）`
  }))
)

/** 窄屏标签下拉框的选项：第一条"全部标签"（空串 = 不按标签筛选） */
const tagSelectOptions = computed(() => [
  { value: '', label: '全部标签' },
  ...tags.value.map((tag) => ({ value: tag.name, label: `${tag.name}（${tag.articleCount}）` }))
])

/**
 * 窄屏下拉框的绑定：读的是现有筛选状态，写的还是原有那几个函数，
 * 所以点卡片分类/标签、地址栏带参、清除筛选都能和下拉框保持同步。
 */
const categorySelectValue = computed({
  get: () => activeCategorySlug.value,
  set: (value: string | null | undefined) => selectCategory(value ?? '')
})

const tagSelectValue = computed({
  get: () => activeTagName.value,
  set: (value: string | null | undefined) => applyTagFilter(value ?? '')
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

async function loadCategories() {
  try {
    const res = (await request.get('/category/list')) as { data: CategoryItem[] }
    categories.value = res.data
  } catch {
    // 分类接口失败时不影响文章列表本身的加载
    categories.value = []
  }
}

async function loadTags() {
  try {
    const res = (await request.get('/tag/list')) as { data: TagItem[] }
    tags.value = res.data
  } catch {
    // 标签面板是可选内容，接口失败时隐藏即可
    tags.value = []
  }
}

async function loadArticles() {
  const requestId = ++articleRequestId
  loading.value = true
  errorMessage.value = ''
  try {
    const res = (await request.get('/article/list', {
      silent: true,
      params: {
        page: page.value,
        size: PAGE_SIZE,
        category: activeCategorySlug.value || undefined,
        tag: activeTagName.value || undefined,
        keyword: appliedKeyword.value || undefined
      }
    })) as { data: ArticlePage }

    if (requestId !== articleRequestId) return
    articles.value = res.data.records
    total.value = res.data.total
    const lastPage = Math.max(1, Math.ceil(total.value / PAGE_SIZE))
    if (page.value > lastPage) {
      page.value = lastPage
      syncQuery(true)
      return
    }
    await restoreScroll(requestId)
  } catch {
    if (requestId !== articleRequestId) return
    articles.value = []
    total.value = 0
    errorMessage.value = '文章加载失败，请稍后重试。'
  } finally {
    if (requestId === articleRequestId) loading.value = false
  }
}

/** 把当前筛选写回地址栏，链接可以直接分享、刷新后筛选还在 */
function syncQuery(replace = false) {
  const query: Record<string, string> = {}
  if (activeCategorySlug.value) query.category = activeCategorySlug.value
  if (activeTagName.value) query.tag = activeTagName.value
  if (appliedKeyword.value) query.keyword = appliedKeyword.value
  if (page.value > 1) query.page = String(page.value)
  if (router.resolve({ path: '/articles', query }).fullPath === route.fullPath) {
    void loadArticles()
    return
  }
  void router[replace ? 'replace' : 'push']({ path: '/articles', query })
}

/** 从详情页的分类标签跳进来时地址栏带 ?category=slug，这里直接按它筛选 */
function applyRouteQuery() {
  const readQuery = (key: string) => typeof route.query[key] === 'string' ? route.query[key] as string : ''
  activeCategorySlug.value = readQuery('category')
  activeTagName.value = readQuery('tag')
  appliedKeyword.value = readQuery('keyword').trim()
  keywordInput.value = appliedKeyword.value
  const requestedPage = Number(readQuery('page'))
  page.value = Number.isSafeInteger(requestedPage) && requestedPage > 0 ? requestedPage : 1
}

function selectCategory(slug: string) {
  if (activeCategorySlug.value === slug) return
  activeCategorySlug.value = slug
  page.value = 1
  syncQuery()
}

/** 再点一次已选中的标签表示取消筛选。 */
function selectTagName(name: string) {
  activeTagName.value = activeTagName.value === name ? '' : name
  page.value = 1
  syncQuery()
}

/** 下拉框选标签：直接切到该标签，选「全部标签」才清掉 */
function applyTagFilter(name: string) {
  if (activeTagName.value === name) return
  activeTagName.value = name
  page.value = 1
  syncQuery()
}

function clearTagFilter() {
  if (!activeTagName.value) return
  activeTagName.value = ''
  page.value = 1
  syncQuery()
}

function applySearch() {
  appliedKeyword.value = keywordInput.value.trim()
  page.value = 1
  syncQuery()
}

function clearSearch() {
  keywordInput.value = ''
  appliedKeyword.value = ''
  page.value = 1
  syncQuery()
}

function changePage(next: number) {
  if (next < 1 || next > totalPages.value || next === page.value) return
  page.value = next
  syncQuery()
}

async function restoreScroll(requestId: number) {
  const top = savedScrollPositions.get(route.fullPath) ?? 0
  await nextTick()
  for (let attempt = 0; attempt < 6; attempt += 1) {
    if (requestId !== articleRequestId) return
    window.scrollTo({ top })
    if (Math.abs(window.scrollY - top) < 2) break
    await new Promise((resolve) => requestAnimationFrame(() => resolve(null)))
  }
}

onMounted(() => {
  void Promise.all([loadCategories(), loadTags()])
})

watch(() => route.fullPath, () => {
  if (route.path !== '/articles') return
  applyRouteQuery()
  void loadArticles()
}, { immediate: true })

function rememberScroll(path: string) {
  savedScrollPositions.set(path, window.scrollY)
  if (savedScrollPositions.size > 50) {
    const oldest = savedScrollPositions.keys().next().value
    if (oldest !== undefined) savedScrollPositions.delete(oldest)
  }
}

onBeforeRouteUpdate((_to, from) => rememberScroll(from.fullPath))
onBeforeRouteLeave((_to, from) => rememberScroll(from.fullPath))
</script>

<template>
  <div class="page-shell">
    <main class="page-container">
      <!-- 左侧文章列表 + 右侧分类 / 标签筛选 -->
      <div class="articles-layout">
        <section class="content-column">
          <!-- 关键词搜索：对应 GET /article/list 的 keyword 参数 -->
          <div class="search-bar">
            <svg
              class="search-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              aria-hidden="true"
            >
              <circle cx="11" cy="11" r="7" />
              <path d="m20 20-3.6-3.6" />
            </svg>
            <input
              v-model="keywordInput"
              class="search-input"
              type="text"
              placeholder="搜索文章标题或摘要"
              @keyup.enter="applySearch"
            />
            <button type="button" class="search-button" @click="applySearch">搜索</button>
            <button
              v-if="appliedKeyword"
              type="button"
              class="search-clear"
              @click="clearSearch"
            >
              清除
            </button>
          </div>

          <!-- 窄屏（≤860px）筛选：右侧分类/标签栏换成这里并排的两个下拉框 -->
          <div class="filter-bar">
            <el-select
              v-model="categorySelectValue"
              class="filter-select"
              size="large"
              placeholder="全部分类"
              popper-class="front-select-popper"
            >
              <el-option
                v-for="option in categorySelectOptions"
                :key="option.value || 'all'"
                :label="option.label"
                :value="option.value"
              />
            </el-select>

            <el-select
              v-model="tagSelectValue"
              class="filter-select"
              size="large"
              placeholder="全部标签"
              popper-class="front-select-popper"
            >
              <el-option
                v-for="option in tagSelectOptions"
                :key="option.value || 'all'"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </div>

          <div v-if="appliedKeyword || activeTagName" class="filter-hint">
            <span v-if="appliedKeyword" class="filter-hint-text">
              搜索“{{ appliedKeyword }}”共 {{ total }} 篇
            </span>
            <button
              v-if="activeTagName"
              type="button"
              class="filter-hint-chip"
              @click="clearTagFilter"
            >
              标签：{{ activeTagName }}
              <span class="filter-hint-clear">清除</span>
            </button>
          </div>

          <!-- 加载中 -->
          <div v-if="loading" class="state-card">
            <span class="state-spinner" aria-hidden="true"></span>
            <p>正在加载文章…</p>
          </div>

          <!-- 加载失败 -->
          <div v-else-if="errorMessage" class="state-card">
            <p>{{ errorMessage }}</p>
          </div>

          <!-- 文章列表：数据来自后端文章接口，仅做展示 -->
          <div v-else-if="articles.length" class="article-list">
            <router-link
              v-for="article in articles"
              :key="article.id"
              class="article-item"
              :to="`/articles/${article.id}`"
            >
              <div class="article-head">
                <button
                  type="button"
                  class="card-tag"
                  :title="`只看「${article.categoryName}」分类`"
                  @click.prevent="selectCategory(article.categorySlug)"
                >
                  {{ article.categoryName }}
                </button>
                <time :datetime="article.updatedAt || undefined">
                  {{ formatDate(article.updatedAt) }}
                </time>
              </div>
              <h2>{{ article.title }}</h2>
              <p>{{ article.summary }}</p>
              <div v-if="article.tags && article.tags.length" class="article-tags">
                <button
                  v-for="tag in article.tags"
                  :key="tag"
                  type="button"
                  class="article-tag"
                  :title="`只看「${tag}」标签`"
                  @click.prevent="selectTagName(tag)"
                >
                  {{ tag }}
                </button>
              </div>
            </router-link>
          </div>

          <!-- 空状态 -->
          <div v-else class="state-card">
            <p v-if="appliedKeyword">没有找到与“{{ appliedKeyword }}”相关的文章。</p>
            <p v-else-if="activeTagName">标签“{{ activeTagName }}”下暂时还没有文章。</p>
            <p v-else>这个分类暂时还没有文章。</p>
          </div>

          <!-- 分页：对应 GET /article/list 的 page / size 参数 -->
          <div v-if="!loading && !errorMessage && totalPages > 1" class="pager">
            <button
              type="button"
              class="pager-button"
              :disabled="page <= 1"
              @click="changePage(page - 1)"
            >
              上一页
            </button>
            <span class="pager-info">第 {{ page }} / {{ totalPages }} 页</span>
            <button
              type="button"
              class="pager-button"
              :disabled="page >= totalPages"
              @click="changePage(page + 1)"
            >
              下一页
            </button>
          </div>
        </section>

        <!-- 右侧筛选面板：分类 + 标签，点一下就能筛出对应的文章 -->
        <aside class="sidebar" aria-label="文章筛选">
          <section class="sidebar-panel">
            <h2 class="sidebar-title">分类</h2>
            <div class="filter-list">
              <button
                v-for="option in categoryOptions"
                :key="option.slug || 'all'"
                type="button"
                class="filter-tag"
                :class="{ 'is-active': activeCategorySlug === option.slug }"
                @click="selectCategory(option.slug)"
              >
                <span class="filter-name">{{ option.name }}</span>
                <span class="filter-count">{{ option.count }}</span>
              </button>
            </div>
          </section>

          <section v-if="tags.length" class="sidebar-panel">
            <h2 class="sidebar-title">标签</h2>
            <div class="filter-list">
              <button
                v-for="tag in tags"
                :key="tag.id"
                type="button"
                class="filter-tag"
                :class="{ 'is-active': activeTagName === tag.name }"
                @click="selectTagName(tag.name)"
              >
                <span class="filter-name">{{ tag.name }}</span>
                <span class="filter-count">{{ tag.articleCount }}</span>
              </button>
            </div>
          </section>
        </aside>
      </div>
    </main>
  </div>
</template>

<style scoped>
.articles-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  align-items: start;
  gap: 28px;
}

.content-column {
  min-width: 0;
}

/* ---------- 搜索栏 ---------- */

.search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 0 8px;
  border-bottom: 1px solid var(--panel-border-strong);
  transition: border-color 0.2s ease;
}

.search-bar:focus-within {
  border-color: var(--accent-brown);
}

.search-icon {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  color: var(--text-muted);
  transition: color 0.2s ease;
}

.search-bar:focus-within .search-icon {
  color: var(--accent-brown);
}

.search-input {
  flex: 1;
  min-width: 0;
  height: 42px;
  padding: 0;
  border: none;
  border-radius: 0;
  color: var(--text-strong);
  font-size: 14px;
  outline: none;
  background: transparent;
  box-shadow: none;
}

.search-input::placeholder {
  color: var(--text-muted);
}

.search-input:focus {
  border: none;
  box-shadow: none;
}

.search-button,
.search-clear,
.pager-button {
  height: 40px;
  padding: 0 18px;
  border: none;
  border-radius: var(--radius-control, 4px);
  font-size: 14px;
  cursor: pointer;
  transition: color 0.2s ease;
}

.search-button,
.search-clear {
  padding-right: 8px;
  padding-left: 8px;
}

.search-button {
  color: var(--accent-brown);
  background: transparent;
}

.search-clear {
  color: var(--text-muted);
  background: transparent;
}

.search-button:hover,
.search-clear:hover,
.pager-button:not(:disabled):hover {
  color: var(--text-strong);
  text-decoration: underline;
  text-underline-offset: 4px;
}

.search-button:active,
.pager-button:not(:disabled):active {
  color: var(--accent-brown);
}

.search-button:focus-visible,
.search-clear:focus-visible,
.pager-button:focus-visible {
  outline: 2px solid var(--accent-brown);
  outline-offset: 2px;
}

/* ---------- 窄屏筛选下拉（≤860px 才出现，替代右侧分类/标签栏） ---------- */

.filter-bar {
  display: none;
  gap: 12px;
  margin-top: 14px;
  /* 聚焦描边 / 选中态跟着主题走，不要 Element Plus 默认蓝 */
  --el-color-primary: var(--accent-brown);
  --el-color-primary-light-3: #b98a5e;
  --el-color-primary-light-5: #cfa986;
  --el-color-primary-light-7: #e2cbaf;
  --el-color-primary-light-8: #ecdcc6;
  --el-color-primary-light-9: #f4ebdd;
  --el-color-primary-dark-2: #80583d;
  --el-text-color-placeholder: var(--text-muted);
  --el-border-color-hover: rgba(var(--accent-brown-rgb), 0.45);
  --el-fill-color-blank: var(--panel-bg);
}

.filter-select {
  flex: 1 1 0;
  min-width: 0;
}

.filter-bar :deep(.el-select__wrapper) {
  min-height: 46px;
  border-radius: var(--radius-control, 4px);
  background: var(--panel-bg);
  box-shadow: 0 0 0 1px rgba(var(--accent-brown-rgb), 0.24) inset;
}

.filter-bar :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--accent-brown) inset;
}

.filter-bar :deep(.el-select__selected-item) {
  color: var(--text-strong);
  font-size: 14px;
}

.filter-hint {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin: 14px 4px 0;
  color: var(--text-muted);
  font-size: 13px;
}

.filter-hint-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px;
  border: none;
  color: var(--accent-brown);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  background: transparent;
}

.filter-hint-chip:hover {
  text-decoration: underline;
  text-underline-offset: 4px;
}

.filter-hint-clear {
  color: var(--text-muted);
}

/* ---------- 文章列表 ---------- */

.article-list {
  display: flex;
  flex-direction: column;
  margin-top: 20px;
}

.article-item {
  display: block;
  padding: 30px 32px;
  cursor: pointer;
}

.article-item + .article-item {
  border-top: 1px solid var(--panel-border);
}

.article-item:hover h2 {
  color: var(--accent-brown);
  text-decoration: underline;
  text-underline-offset: 4px;
}

.article-item:focus-visible {
  outline: 2px solid var(--accent-brown);
  outline-offset: 2px;
}

.article-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-tag {
  padding: 4px 12px;
  border: none;
  color: var(--accent-brown);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  background: transparent;
}

.card-tag:hover {
  text-decoration: underline;
  text-underline-offset: 4px;
}

/* 文章卡片上的标签，点一下筛出同标签的文章 */
.article-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 16px;
}

.article-tag {
  padding: 3px 12px;
  border: none;
  color: var(--accent-brown);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  background: transparent;
}

.article-tag:hover {
  text-decoration: underline;
  text-underline-offset: 4px;
}

.article-head time {
  color: var(--text-muted);
  font-size: 13px;
}

.article-item h2 {
  margin: 16px 0 10px;
  color: var(--text-strong);
  font-size: 21px;
  line-height: 1.5;
}

.article-item p {
  margin: 0;
  color: var(--text-body);
  font-size: 14px;
  line-height: 1.8;
}

.article-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid rgba(var(--accent-brown-rgb), 0.14);
  color: var(--text-muted);
  font-size: 13px;
}

/* ---------- 加载 / 空状态 ---------- */

.state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 20px;
  padding: 60px 24px;
  text-align: center;
}

.state-card p {
  margin: 0;
  color: var(--text-muted);
}

.state-spinner {
  width: 26px;
  height: 26px;
  border: 2px solid rgba(var(--accent-brown-rgb), 0.22);
  border-top-color: var(--accent-brown);
  border-radius: 50%;
  animation: state-spin 0.8s linear infinite;
}

@keyframes state-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ---------- 分页 ---------- */

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 28px;
}

.pager-button {
  color: var(--accent-brown);
  background: transparent;
}

.pager-button:disabled {
  color: var(--text-muted);
  cursor: not-allowed;
  opacity: 0.6;
}

.pager-info {
  color: var(--text-muted);
  font-size: 13px;
}

/* ---------- 右侧分类 ---------- */

.sidebar {
  display: flex;
  position: sticky;
  top: 28px;
  flex-direction: column;
  gap: 20px;
  align-self: start;
}

/* 分类和标签以标题、留白分组，避免重复套框。 */
.sidebar-panel {
  padding: 24px 18px;
}

.sidebar-panel + .sidebar-panel {
  border-top: 1px solid var(--panel-border);
}

.sidebar-title {
  margin: 0 0 16px;
  padding: 0 8px;
  color: var(--text-strong);
  font-size: 17px;
}

.filter-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.filter-tag {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 10px 14px;
  border: none;
  color: var(--text-body);
  font-size: 14px;
  cursor: pointer;
  background: transparent;
  transition: color 0.2s ease;
}

.filter-tag:hover {
  color: var(--accent-brown);
}

.filter-tag.is-active {
  color: var(--accent-brown, #9b6846);
}

.filter-tag.is-active .filter-name,
.filter-tag:hover .filter-name {
  text-decoration: underline;
  text-underline-offset: 4px;
}

.filter-count {
  min-width: 26px;
  padding: 2px 8px;
  color: var(--text-muted);
  font-size: 12px;
  text-align: center;
}

.filter-tag.is-active .filter-count {
  color: var(--accent-brown);
}

@media (max-width: 860px) {
  .articles-layout {
    grid-template-columns: 1fr;
  }

  /* 窄屏不再把分类/标签栏折到列表下面，改用搜索栏下方那两个下拉框 */
  .sidebar {
    display: none;
  }

  .filter-bar {
    display: flex;
  }
}
</style>
