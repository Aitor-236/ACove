<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { renderMarkdown } from '@/utils/markdown'
import { handleCodeGroupClick, handleCodeGroupKeydown } from '@/utils/codeBlock'

/** 公开文章详情（来自 GET /article/detail/{id}） */
interface ArticleDetail {
  id: number
  title: string
  summary: string
  content: string
  categoryId: number | null
  categoryName: string
  categorySlug: string
  tags: string[]
  readingMinutes: number
  /** 详情页和列表卡片统一显示更新时间，不再展示发布时间 */
  updatedAt: string | null
}

const route = useRoute()
const router = useRouter()
const article = ref<ArticleDetail | null>(null)
const loading = ref(false)
const errorMessage = ref('')

// Markdown 在后端只存原文，这里用公共渲染器解析并清洗（含代码块语言标签、图片地址补前缀）
const renderedContent = computed(() => {
  const source = article.value?.content
  if (!source) return ''
  return renderMarkdown(source)
})

function formatDate(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

/**
 * 返回上一页：从首页/列表点进来就退回来源页（并恢复它原来的滚动位置），
 * 直接打开详情页（没有上一页）时才回退到文章列表。
 */
function goBack() {
  if (window.history.state?.back) {
    router.back()
    return
  }
  void router.push('/articles')
}

async function loadArticle(id: string) {
  loading.value = true
  errorMessage.value = ''
  article.value = null
  try {
    const res = (await request.get(`/article/detail/${id}`)) as { data: ArticleDetail }
    article.value = res.data
  } catch {
    errorMessage.value = '这篇文章不存在，或者还没有发布。'
  } finally {
    loading.value = false
  }
}

// 支持从列表进入详情以及直接刷新详情链接
watch(
  () => route.params.id,
  (id) => {
    if (typeof id === 'string' && id) {
      void loadArticle(id)
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="page-shell">
    <main class="page-container detail-container">
      <div class="back-row">
        <button type="button" class="back-link" @click="goBack">返回</button>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="state-card">
        <span class="state-spinner" aria-hidden="true"></span>
        <p>正在加载文章…</p>
      </div>

      <!-- 加载失败或文章不可见 -->
      <div v-else-if="errorMessage" class="state-card">
        <p>{{ errorMessage }}</p>
        <router-link class="state-link" to="/articles">去看看其它文章</router-link>
      </div>

      <article v-else-if="article" class="article-detail surface-panel">
        <header class="detail-header">
          <router-link
            class="card-tag"
            :to="{ path: '/articles', query: { category: article.categorySlug } }"
          >
            {{ article.categoryName }}
          </router-link>
          <h1>{{ article.title }}</h1>
          <p v-if="article.summary" class="detail-summary">{{ article.summary }}</p>
          <div v-if="article.tags && article.tags.length" class="detail-tags">
            <router-link
              v-for="tag in article.tags"
              :key="tag"
              class="tag-chip"
              :to="{ path: '/articles', query: { tag } }"
            >
              {{ tag }}
            </router-link>
          </div>
          <div class="detail-meta">
            <!-- 和列表卡片统一显示更新时间（后端排序仍按创建时间） -->
            <time :datetime="article.updatedAt || undefined">
              {{ formatDate(article.updatedAt) }}
            </time>
          </div>
        </header>

        <!-- 内容已通过 DOMPurify 清洗；代码组的切换用事件委托挂在这个容器上 -->
        <div
          class="markdown-body"
          v-html="renderedContent"
          @click="handleCodeGroupClick"
          @keydown="handleCodeGroupKeydown"
        ></div>
      </article>
    </main>
  </div>
</template>

<style scoped>
.detail-container {
  width: min(860px, 100%);
}

.back-row {
  margin-bottom: 18px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  padding: 8px 14px;
  border: none;
  border-radius: 12px;
  color: var(--accent-brown);
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  background: var(--panel-alt-bg);
  transition:
    transform 0.2s ease,
    background-color 0.2s ease;
}

.back-link:hover {
  transform: translateX(-2px);
  background: #fbf6ec;
}

.article-detail {
  padding: 52px 56px 56px;
  border-radius: 28px;
}

.detail-header {
  padding-bottom: 26px;
  border-bottom: 1px solid rgba(138, 90, 59, 0.16);
}

.card-tag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 999px;
  color: #7a5436;
  font-size: 12px;
  text-decoration: none;
  background: #ecdec5;
  transition:
    color 0.2s ease,
    background-color 0.2s ease;
}

.card-tag:hover {
  color: #fdf9f2;
  background: var(--accent-brown);
}

.detail-header h1 {
  margin: 18px 0 12px;
  color: var(--text-strong);
  font-size: clamp(26px, 4vw, 36px);
  line-height: 1.4;
}

.detail-summary {
  margin: 0 0 16px;
  color: var(--text-body);
  font-size: 15px;
  line-height: 1.8;
}

.detail-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.tag-chip {
  display: inline-block;
  padding: 3px 12px;
  border-radius: 999px;
  color: var(--accent-brown);
  font-size: 12px;
  text-decoration: none;
  background: var(--panel-alt-bg);
  box-shadow: inset 0 0 0 1px rgba(138, 90, 59, 0.22);
  transition:
    color 0.2s ease,
    background-color 0.2s ease;
}

.tag-chip:hover {
  color: #fdf9f2;
  background: var(--accent-brown);
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  color: var(--text-muted);
  font-size: 13px;
}

.markdown-body {
  margin-top: 28px;
  color: var(--text-body);
  font-size: 16px;
  line-height: 1.9;
  word-break: break-word;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 32px 0 14px;
  color: var(--text-strong);
  line-height: 1.4;
}

.markdown-body :deep(h2) {
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(138, 90, 59, 0.18);
  font-size: 22px;
}

.markdown-body :deep(h3) {
  font-size: 19px;
}

.markdown-body :deep(p) {
  margin: 0 0 18px;
}

.markdown-body :deep(a) {
  color: var(--accent-brown);
  text-decoration: underline;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0 0 18px;
  padding-left: 24px;
}

.markdown-body :deep(li) {
  margin-bottom: 8px;
}

.markdown-body :deep(blockquote) {
  margin: 0 0 18px;
  padding: 12px 18px;
  border-left: 4px solid var(--accent-brown-soft);
  border-radius: 0 12px 12px 0;
  color: var(--text-body);
  background: var(--panel-alt-bg);
}

.markdown-body :deep(img) {
  display: block;
  max-width: 100%;
  margin: 0 auto 20px;
  border-radius: 16px;
  box-shadow: 0 12px 28px rgba(120, 88, 58, 0.2);
}

.markdown-body :deep(hr) {
  margin: 28px 0;
  border: none;
  border-top: 1px solid rgba(138, 90, 59, 0.22);
}

.state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 64px 24px;
  border: 1px dashed rgba(138, 90, 59, 0.22);
  border-radius: 28px;
  background: var(--panel-bg);
  text-align: center;
}

.state-spinner {
  width: 26px;
  height: 26px;
  border: 2px solid rgba(138, 90, 59, 0.22);
  border-top-color: var(--accent-brown);
  border-radius: 50%;
  animation: state-spin 0.8s linear infinite;
}

@keyframes state-spin {
  to {
    transform: rotate(360deg);
  }
}

.state-card p {
  margin: 0 0 18px;
  color: var(--text-muted);
}

.state-link {
  display: inline-flex;
  padding: 9px 18px;
  border-radius: 12px;
  color: #fdf9f2;
  font-size: 14px;
  background: linear-gradient(135deg, #b98a5e, #8a5a3b);
  box-shadow: 0 8px 18px rgba(138, 90, 59, 0.24);
}

@media (max-width: 720px) {
  .article-detail {
    padding: 34px 24px 40px;
  }
}
</style>
