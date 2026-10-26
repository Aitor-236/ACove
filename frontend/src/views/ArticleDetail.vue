<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElImageViewer } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { usePageMetadata } from '@/composables/usePageMetadata'
import { renderMarkdown } from '@/utils/markdown'
import { handleCodeGroupClick, handleCodeGroupKeydown } from '@/utils/codeBlock'
import {
  handleMarkdownImageClick,
  handleMarkdownImageError,
  handleMarkdownImageKeydown
} from '@/utils/markdownImages'

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
const previewImage = ref('')

function handleContentClick(event: MouseEvent) {
  handleCodeGroupClick(event)
  handleMarkdownImageClick(event, (source) => (previewImage.value = source))
}

function handleContentKeydown(event: KeyboardEvent) {
  handleCodeGroupKeydown(event)
  handleMarkdownImageKeydown(event, (source) => (previewImage.value = source))
}

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

usePageMetadata(() => ({ title: article.value?.title, description: article.value?.summary, type: 'article', noindex: !article.value }))

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

      <article v-else-if="article" class="article-detail">
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
          @click="handleContentClick"
          @keydown="handleContentKeydown"
          @error.capture="handleMarkdownImageError"
        ></div>
      </article>

      <ElImageViewer
        v-if="previewImage"
        :url-list="[previewImage]"
        :teleported="true"
        @close="previewImage = ''"
      />
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
  border-radius: var(--radius-control, 4px);
  color: var(--accent-brown);
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  background: var(--panel-alt-bg);
  transition: background-color 0.2s ease;
}

.back-link:hover {
  background: #fbf6ec;
}

.article-detail {
  padding: 52px 56px 56px;
}

.detail-header {
  padding-bottom: 26px;
  border-bottom: 1px solid rgba(var(--accent-brown-rgb), 0.16);
}

.card-tag {
  display: inline-block;
  padding: 4px 12px;
  color: var(--accent-brown);
  font-size: 12px;
  text-decoration: none;
  background: transparent;
}

.card-tag:hover {
  text-decoration: underline;
  text-underline-offset: 4px;
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
  color: var(--accent-brown);
  font-size: 12px;
  text-decoration: none;
  background: transparent;
}

.tag-chip:hover {
  text-decoration: underline;
  text-underline-offset: 4px;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  color: var(--text-muted);
  font-size: 13px;
}

/* 正文排版与代码块样式统一在全局 styles/markdown.css（前台详情页 / 后台预览共用），
   这里只留详情页自己的上边距（标题块与正文之间那一段） */
.markdown-body {
  margin-top: 28px;
}

.state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 64px 24px;
  text-align: center;
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

.state-card p {
  margin: 0 0 18px;
  color: var(--text-muted);
}

.state-link {
  display: inline-flex;
  padding: 9px 18px;
  color: var(--accent-brown);
  font-size: 14px;
  text-underline-offset: 4px;
}

.state-link:hover {
  color: var(--text-strong);
  text-decoration: underline;
}

@media (max-width: 720px) {
  .article-detail {
    padding: 34px 24px 40px;
  }
}
</style>
