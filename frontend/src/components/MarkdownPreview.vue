<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'
import { handleCodeGroupClick, handleCodeGroupKeydown } from '@/utils/codeBlock'

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
</script>

<template>
  <!-- 内容已用 DOMPurify 清洗过（renderMarkdown 里做的） -->
  <div
    v-if="html"
    class="markdown-body"
    v-html="html"
    @click="handleCodeGroupClick"
    @keydown="handleCodeGroupKeydown"
  ></div>
  <p v-else class="markdown-preview-empty">{{ props.emptyText }}</p>
</template>

<style scoped>
.markdown-preview-empty {
  margin: 0;
  color: var(--text-muted);
  font-size: 14px;
}
</style>
