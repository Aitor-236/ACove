<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElImageViewer } from 'element-plus'
import { renderMarkdown } from '@/utils/markdown'
import { handleCodeGroupClick, handleCodeGroupKeydown } from '@/utils/codeBlock'
import {
  handleMarkdownImageClick,
  handleMarkdownImageError,
  handleMarkdownImageKeydown
} from '@/utils/markdownImages'

/**
 * 正文渲染块：和前台详情页共用 utils/markdown.ts 那一份规则（代码块语法高亮、
 * GFM 表格、`::: code-group` 语言标签页）。
 *
 * 两处在用：文章编辑页的只读预览态、分屏编辑器（MarkdownSplitEditor）的右半。
 * 代码组的点击 / 方向键切换靠事件委托挂在这个容器上——正文是 v-html 注入的静态 HTML。
 */
const props = withDefaults(
  defineProps<{
    /** Markdown 原文；空串时只显示一句占位提示 */
    source: string
    /** 正文为空时的提示语，两个使用场景的措辞不一样 */
    emptyText?: string
  }>(),
  {
    emptyText: '正文还是空的。'
  }
)

const html = computed(() => {
  const source = props.source.trim()
  return source ? renderMarkdown(source) : ''
})
const previewImage = ref('')

function handleContentClick(event: MouseEvent) {
  handleCodeGroupClick(event)
  handleMarkdownImageClick(event, (source) => (previewImage.value = source))
}

function handleContentKeydown(event: KeyboardEvent) {
  handleCodeGroupKeydown(event)
  handleMarkdownImageKeydown(event, (source) => (previewImage.value = source))
}
</script>

<template>
  <!-- 内容已用 DOMPurify 清洗过（renderMarkdown 里做的） -->
  <div
    v-if="html"
    class="markdown-body"
    v-html="html"
    @click="handleContentClick"
    @keydown="handleContentKeydown"
    @error.capture="handleMarkdownImageError"
  ></div>
  <p v-else class="markdown-preview-empty">{{ props.emptyText }}</p>

  <ElImageViewer
    v-if="previewImage"
    :url-list="[previewImage]"
    :teleported="true"
    @close="previewImage = ''"
  />
</template>

<style scoped>
/*
 * 正文为空时的占位（分屏右栏显示「正文预览」）：在预览区里居中，
 * 一开始写正文就整块消失（模板里的 v-if / v-else）。
 */
.markdown-preview-empty {
  display: flex;
  min-height: 100%;
  align-items: center;
  justify-content: center;
  margin: 0;
  color: var(--text-faint, rgba(74, 54, 41, 0.38));
  font-size: 14px;
  letter-spacing: 0.08em;
  text-align: center;
}
</style>
