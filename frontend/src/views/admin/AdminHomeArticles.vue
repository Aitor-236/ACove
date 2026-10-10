<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { VueDraggable } from 'vue-draggable-plus'
import request from '@/utils/request'
import { autoSaveStatusText, useAutoSave } from '@/composables/useAutoSave'

/** 文章卡片（后台列表、已选列表都用同一套字段，来自 ArticleVO） */
interface AdminArticleItem {
  id: number
  title: string
  summary: string
  categoryName: string
  status: 'draft' | 'published'
  publishedAt: string | null
  updatedAt: string | null
  readingMinutes: number | null
}

/** MyBatis-Plus 分页返回结构 */
interface ArticlePage {
  records: AdminArticleItem[]
  total: number
}

/** 右侧「可添加的文章」一次最多拉这么多，超出的靠搜索缩小范围 */
const CANDIDATE_PAGE_SIZE = 100

/** 已选文章，数组顺序就是保存后的首页顺序 */
const selected = ref<AdminArticleItem[]>([])
/** 后端当前保存的顺序，用来判断有没有未保存的改动 */
const savedIds = ref<number[]>([])
/** 可添加的已发布文章 */
const candidates = ref<AdminArticleItem[]>([])
const candidateTotal = ref(0)

const selectedLoading = ref(false)
const candidateLoading = ref(false)
const keywordInput = ref('')
const appliedKeyword = ref('')
/** 回填已选列表期间不触发自动保存 */
const hydrating = ref(false)

const selectedIdSet = computed(() => new Set(selected.value.map((article) => article.id)))

/** 顺序或成员跟后端保存的不一致：既驱动离开确认，也决定状态文字 */
const dirty = computed(
  () => selected.value.map((article) => article.id).join(',') !== savedIds.value.join(',')
)

/**
 * 首页展示没有手动保存按钮：增删 / 拖拽停下来 300ms 就整体覆盖保存一次，
 * 空列表也算一次有效改动（保存后首页回退到最近三篇）。
 */
const {
  state: autoSaveState,
  lastSavedAt: autoSavedAt,
  pending: autoSavePending,
  schedule: scheduleAutoSave,
  flush: flushAutoSave
} = useAutoSave({
  delay: 300,
  save: async () => {
    const articleIds = selected.value.map((article) => article.id)
    await request.post('/admin/home-article/save', { articleIds }, { silent: true })
    savedIds.value = articleIds
  }
})

const autoSaveHint = computed(() => {
  const state = autoSaveState.value
  // 保存中 / 失败要马上反馈；防抖等待期间不打扰，存完再显示「已保存 HH:mm」
  if (state === 'saving' || state === 'error') return autoSaveStatusText(state, autoSavedAt.value)
  if (autoSavePending.value || dirty.value) return ''
  return autoSaveStatusText(state, autoSavedAt.value)
})

watch(
  selected,
  () => {
    if (hydrating.value) return
    scheduleAutoSave()
  },
  { deep: true }
)

/** 还有没落库的改动（排队中的 + 保存失败的） */
function hasUnsavedChanges() {
  return dirty.value || autoSavePending.value
}

function retryAutoSave() {
  void flushAutoSave()
}

function formatDateTime(value?: string | null) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

function isSelected(id: number) {
  return selectedIdSet.value.has(id)
}

async function loadSelected() {
  selectedLoading.value = true
  hydrating.value = true
  try {
    const res = (await request.get('/admin/home-article/list')) as { data: AdminArticleItem[] }
    selected.value = res.data ?? []
    savedIds.value = selected.value.map((article) => article.id)
  } catch {
    selected.value = []
    savedIds.value = []
  } finally {
    // 等 selected 的 watcher 跑完再解除标记，回填不会被当成一次改动
    await nextTick()
    hydrating.value = false
    selectedLoading.value = false
  }
}

async function loadCandidates() {
  candidateLoading.value = true
  try {
    const res = (await request.get('/admin/article/list', {
      params: {
        page: 1,
        size: CANDIDATE_PAGE_SIZE,
        status: 'published',
        keyword: appliedKeyword.value || undefined
      }
    })) as { data: ArticlePage }

    candidates.value = res.data.records ?? []
    candidateTotal.value = res.data.total ?? candidates.value.length
  } catch {
    candidates.value = []
    candidateTotal.value = 0
  } finally {
    candidateLoading.value = false
  }
}

function applySearch() {
  appliedKeyword.value = keywordInput.value.trim()
  void loadCandidates()
}

function clearSearch() {
  keywordInput.value = ''
  appliedKeyword.value = ''
  void loadCandidates()
}

/** 加到已选列表末尾（首页顺序的最后一位） */
function addArticle(article: AdminArticleItem) {
  if (isSelected(article.id)) return
  selected.value.push(article)
}

function removeArticle(id: number) {
  const index = selected.value.findIndex((article) => article.id === id)
  if (index >= 0) {
    selected.value.splice(index, 1)
  }
}

async function reloadAll() {
  await Promise.all([loadSelected(), loadCandidates()])
}

/** 有改动没存成功时拦一下离开，避免白改 */
onBeforeRouteLeave(async () => {
  if (!hasUnsavedChanges()) return true

  const saved = await flushAutoSave()
  if (saved) return true

  try {
    await ElMessageBox.confirm(
      '首页展示还有改动没保存成功，离开后这次修改会丢失，确定离开吗？',
      '未保存的修改',
      { confirmButtonText: '离开', cancelButtonText: '继续编辑', type: 'warning' }
    )
    return true
  } catch {
    return false
  }
})

/** 关标签页 / 刷新时浏览器原生的离开确认 */
function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!hasUnsavedChanges()) return
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  void reloadAll()
})

onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload))
</script>

<template>
  <div class="admin-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">首页展示</h1>
      </div>

      <div class="admin-page-actions">
        <button
          v-if="autoSaveState === 'error'"
          type="button"
          class="save-status is-error"
          @click="retryAutoSave"
        >
          {{ autoSaveHint }}
        </button>
        <span v-else-if="autoSaveHint" class="save-status">{{ autoSaveHint }}</span>

        <el-button :loading="selectedLoading || candidateLoading" @click="reloadAll">刷新</el-button>
      </div>
    </header>

    <div class="home-grid">
      <!-- 左：已选文章，拖拽决定首页里的先后 -->
      <section class="admin-panel selected-panel" v-loading="selectedLoading">
        <header class="panel-head">
          <h2 class="panel-title">已选文章</h2>
          <span class="panel-count">{{ selected.length }} 篇</span>
        </header>
        <p class="panel-hint">长按拖动排序</p>

        <VueDraggable
          v-if="selected.length"
          v-model="selected"
          tag="ul"
          class="selected-list"
          :animation="180"
          ghost-class="is-ghost"
          filter=".item-actions"
          :prevent-on-filter="false"
          :delay="200"
          :delay-on-touch-only="true"
        >
          <li v-for="(article, index) in selected" :key="article.id" class="selected-item">
            <span class="order-index">{{ index + 1 }}</span>

            <div class="item-main">
              <p class="item-title">{{ article.title }}</p>
              <div class="item-meta">
                <span v-if="article.categoryName" class="category-chip">
                  {{ article.categoryName }}
                </span>
                <span v-if="article.status !== 'published'" class="draft-chip">未发布</span>
                <span class="time-text">{{ formatDateTime(article.updatedAt) }}</span>
              </div>
            </div>

            <div class="item-actions">
              <el-button link type="danger" @click="removeArticle(article.id)">移除</el-button>
            </div>
          </li>
        </VueDraggable>

        <div v-else class="admin-state">
          <p>还没选任何文章，首页会显示最近三篇已发布文章。</p>
        </div>
      </section>

      <!-- 右：可添加的已发布文章，加进左栏即可上首页 -->
      <section class="admin-panel candidate-panel">
        <header class="panel-head">
          <h2 class="panel-title">可添加的文章</h2>
          <span class="panel-count">已发布 {{ candidateTotal }} 篇</span>
        </header>

        <div class="candidate-search">
          <el-input
            v-model="keywordInput"
            placeholder="搜索标题或摘要"
            clearable
            size="large"
            @keyup.enter="applySearch"
            @clear="clearSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" size="large" @click="applySearch">搜索</el-button>
        </div>

        <ul v-loading="candidateLoading" class="candidate-list">
          <li v-for="article in candidates" :key="article.id" class="candidate-item">
            <div class="item-main">
              <p class="item-title">{{ article.title }}</p>
              <div class="item-meta">
                <span v-if="article.categoryName" class="category-chip">
                  {{ article.categoryName }}
                </span>
                <span class="time-text">{{ formatDateTime(article.publishedAt) }}</span>
              </div>
            </div>

            <el-button
              v-if="isSelected(article.id)"
              size="small"
              disabled
              class="add-button"
            >
              已添加
            </el-button>
            <el-button
              v-else
              size="small"
              type="primary"
              class="add-button"
              @click="addArticle(article)"
            >
              添加
            </el-button>
          </li>
        </ul>

        <div v-if="!candidateLoading && !candidates.length" class="admin-state">
          <p v-if="appliedKeyword">没有匹配「{{ appliedKeyword }}」的已发布文章。</p>
          <p v-else>还没有已发布的文章，先去文章列表发布一篇。</p>
        </div>

        <p v-if="candidateTotal > candidates.length" class="panel-foot-hint">
          只显示前 {{ candidates.length }} 篇，用搜索缩小范围。
        </p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.home-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  align-items: start;
  gap: 18px;
}

/* 拖拽中的占位行：淡底 + 虚线，一眼能看出会落在哪 */
.selected-item.is-ghost {
  border-style: dashed;
  border-color: rgba(138, 90, 59, 0.5);
  background: var(--panel-alt-bg);
  opacity: 0.7;
}

.selected-panel,
.candidate-panel {
  padding: 18px;
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(138, 90, 59, 0.14);
}

.panel-title {
  margin: 0;
  color: var(--text-strong);
  font-size: 16px;
  font-weight: 600;
}

.panel-count {
  color: var(--text-muted);
  font-size: 12.5px;
}

.panel-hint,
.panel-foot-hint {
  margin: 10px 2px 0;
  color: var(--text-muted);
  font-size: 12.5px;
  line-height: 1.6;
}

/* 自动保存状态：和「刷新」按钮并排的一行小字，失败时点一下重试 */
.save-status {
  align-self: center;
  margin-right: 2px;
  padding: 0;
  border: none;
  color: var(--text-muted);
  font-family: inherit;
  font-size: 12.5px;
  background: transparent;
}

.save-status.is-error {
  color: var(--trend-down, #b4553c);
  text-decoration: underline;
  cursor: pointer;
}

.panel-foot-hint {
  margin-top: 12px;
}

.selected-list,
.candidate-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}

.selected-item,
.candidate-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid var(--panel-border);
  border-radius: 16px;
  background: var(--panel-alt-bg);
}

/* 整行长条都能拖：光标和触摸都由整行承担，行首只留一个序号 */
.selected-item {
  cursor: grab;
}

.selected-item:active {
  cursor: grabbing;
}

.order-index {
  flex: 0 0 auto;
  min-width: 22px;
  color: var(--text-muted);
  font-size: 12.5px;
  text-align: center;
}

.item-main {
  flex: 1;
  min-width: 0;
}

.item-title {
  margin: 0;
  overflow: hidden;
  color: var(--text-strong);
  font-size: 13.5px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 6px;
}

.category-chip {
  padding: 2px 8px;
  border-radius: 999px;
  color: #7a5436;
  font-size: 11px;
  background: #ecdec5;
}

/* 草稿还在选择里，但不会出现在首页，用状态色区分一下 */
.draft-chip {
  padding: 2px 8px;
  border-radius: 999px;
  color: #b4553c;
  font-size: 11px;
  background: rgba(180, 85, 60, 0.12);
}

.time-text {
  color: var(--text-muted);
  font-size: 12px;
}

.item-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 2px;
  /* 操作区不参与拖拽（filter 已经排除），光标也回到默认 */
  cursor: default;
}

.candidate-search {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}

.candidate-list {
  max-height: 460px;
  overflow-y: auto;
}

.add-button {
  flex: 0 0 auto;
}

@media (max-width: 1000px) {
  .home-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
