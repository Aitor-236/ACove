<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import type { Extension } from '@codemirror/state'
import {
  EditorView,
  drawSelection,
  highlightActiveLine,
  highlightSpecialChars,
  keymap,
  lineNumbers,
  placeholder as cmPlaceholder
} from '@codemirror/view'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { markdown } from '@codemirror/lang-markdown'
import { highlightSelectionMatches, search, searchKeymap } from '@codemirror/search'
import { tags as t } from '@lezer/highlight'
import { Picture } from '@element-plus/icons-vue'
import MarkdownPreview from './MarkdownPreview.vue'

/**
 * 后台正文编辑器：左 CodeMirror 6 源码 + 右 renderMarkdown 预览的分屏。
 *
 * Markdown 字符串是唯一真源（右栏只是它的投影），保存时不重排；
 * 组件只负责「源码输入 → v-model」「右栏渲染」「图片文件透给页面」，
 * 上传、校验、提示这些业务逻辑留在 AdminArticleEdit.vue。
 *
 * 分屏用 Element Plus 自带的 el-splitter（全局注册，2.14.5 起）：
 * 宽屏左右分栏、比例记在 localStorage；≤1180px 自动转成上下堆叠。
 */
const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    /** 页面正在上传图片：右上角按钮跟着转圈并禁用，避免重复点 */
    uploading?: boolean
  }>(),
  {
    placeholder: '# 标题：正文直接写 Markdown，列表、表格、代码块、引用、图片都支持',
    uploading: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'image-files': [files: File[]]
}>()

/** 分栏比例的记忆键与可拖范围（左侧百分比） */
const RATIO_KEY = 'acove:editor-split'
const MIN_PERCENT = 28
const MAX_PERCENT = 76
/** 窄屏断点：跟首页同一档，堆叠时不再记忆比例 */
const NARROW_QUERY = '(max-width: 1180px)'

function readSavedRatio(): string {
  const saved = Number(window.localStorage.getItem(RATIO_KEY))
  return Number.isFinite(saved) && saved >= MIN_PERCENT && saved <= MAX_PERCENT ? `${saved}%` : '50%'
}

const sourceHostRef = ref<HTMLElement | null>(null)
const previewRef = ref<HTMLElement | null>(null)
const fileRef = ref<HTMLInputElement | null>(null)
const view = shallowRef<EditorView | null>(null)

/**
 * 右栏预览的节流：renderMarkdown 要跑 marked + highlight.js + DOMPurify，
 * 每敲一个字就整篇重渲染一遍，长文 + 中文输入法会明显卡顿。
 * 只对「用户打字」限流；外部回填 / 切换文章时预览立刻跟上。
 */
const PREVIEW_DELAY = 200
const previewSource = ref(props.modelValue)
let previewTimer: number | undefined
let lastEmitted = props.modelValue

function schedulePreview(value: string) {
  window.clearTimeout(previewTimer)
  previewTimer = window.setTimeout(() => {
    previewSource.value = value
  }, PREVIEW_DELAY)
}

const leftSize = ref(readSavedRatio())
const stacked = ref(false)

/**
 * 源码面板的配色：刻意不用 --code-*——那组是给深棕代码块用的浅色，
 * 落在米白底上会糊。这里是「白底深字 + 棕色点缀」，同色系但按浅底挑。
 */
const acoveSourceTheme = EditorView.theme({
  '&': {
    height: '100%',
    color: 'var(--text-strong)',
    backgroundColor: 'var(--panel-bg)',
    fontSize: '14px'
  },
  '&.cm-focused': { outline: 'none' },
  '.cm-scroller': {
    fontFamily: "'JetBrains Mono', Consolas, Monaco, monospace",
    lineHeight: '1.7'
  },
  '.cm-content': { padding: '14px 16px', caretColor: 'var(--accent-brown)' },
  '.cm-line': { padding: '0' },
  '.cm-gutters': {
    border: 'none',
    borderRight: '1px solid var(--panel-border)',
    color: 'var(--text-faint)',
    backgroundColor: 'var(--panel-bg)'
  },
  '.cm-activeLine': { backgroundColor: 'rgba(185, 138, 94, 0.1)' },
  '.cm-activeLineGutter': {
    color: 'var(--accent-brown)',
    backgroundColor: 'rgba(185, 138, 94, 0.14)'
  },
  '.cm-cursor, .cm-dropCursor': { borderLeftColor: 'var(--accent-brown)' },
  '.cm-selectionBackground, &.cm-focused .cm-selectionBackground, .cm-content ::selection': {
    backgroundColor: 'rgba(185, 138, 94, 0.3)'
  },
  '.cm-searchMatch': { backgroundColor: 'rgba(185, 138, 94, 0.3)' },
  '.cm-searchMatch.cm-searchMatch-selected': { backgroundColor: 'rgba(185, 138, 94, 0.55)' },
  '.cm-panels': {
    color: 'var(--text-strong)',
    backgroundColor: 'var(--panel-alt-bg)',
    borderColor: 'var(--panel-border)'
  },
  '.cm-panels.cm-panels-top': { borderBottom: '1px solid var(--panel-border)' },
  '.cm-textfield, .cm-button': {
    fontFamily: 'inherit',
    color: 'var(--text-strong)',
    backgroundColor: 'var(--panel-bg)',
    border: '1px solid var(--panel-border-strong)',
    borderRadius: '8px'
  },
  '.cm-tooltip': {
    color: 'var(--text-strong)',
    backgroundColor: 'var(--panel-bg)',
    border: '1px solid var(--panel-border)'
  },
  '.cm-placeholder': { color: 'var(--text-faint)' }
})

/** Markdown 源码的语义高亮：标签集来自 @lezer/markdown 的 styleTags */
const acoveMarkdownHighlight = HighlightStyle.define([
  { tag: t.heading1, color: 'var(--accent-brown)', fontWeight: '700', fontSize: '1.3em' },
  { tag: t.heading2, color: 'var(--accent-brown)', fontWeight: '700', fontSize: '1.16em' },
  {
    tag: [t.heading3, t.heading4, t.heading5, t.heading6],
    color: 'var(--accent-brown)',
    fontWeight: '600'
  },
  { tag: t.strong, color: 'var(--text-strong)', fontWeight: '700' },
  { tag: t.emphasis, fontStyle: 'italic' },
  { tag: t.strikethrough, color: 'var(--text-muted)', textDecoration: 'line-through' },
  { tag: [t.link, t.url], color: 'var(--accent-brown)', textDecoration: 'underline' },
  { tag: t.monospace, color: 'var(--accent-brown)', backgroundColor: 'rgba(138, 90, 59, 0.1)' },
  { tag: t.quote, color: 'var(--text-muted)', fontStyle: 'italic' },
  { tag: [t.list, t.labelName, t.string, t.escape, t.character], color: 'var(--accent-brown-soft)' },
  { tag: [t.processingInstruction, t.contentSeparator], color: 'var(--text-faint)' }
])

function editorExtensions(): Extension[] {
  return [
    lineNumbers(),
    highlightActiveLine(),
    highlightSpecialChars(),
    drawSelection(),
    history(),
    markdown(),
    EditorView.lineWrapping,
    search({ top: true }),
    highlightSelectionMatches(),
    syntaxHighlighting(acoveMarkdownHighlight),
    acoveSourceTheme,
    cmPlaceholder(props.placeholder),
    keymap.of([...searchKeymap, ...historyKeymap, ...defaultKeymap, indentWithTab]),
    EditorView.updateListener.of((update) => {
      if (!update.docChanged || applyingExternal) return
      const value = update.state.doc.toString()
      lastEmitted = value
      emit('update:modelValue', value)
    })
  ]
}

/** 外部改内容（回填、切换文章）时标记一下，避免触发 updateListener 再 emit 回去 */
let applyingExternal = false

watch(
  () => props.modelValue,
  (value) => {
    // 编辑器自己 emit 出来的回响：源码已经是最新的，只排一次预览渲染
    if (value === lastEmitted) {
      schedulePreview(value)
      return
    }

    // 外部改动（回填、切换文章）：源码与预览都立刻跟上，不排队
    const instance = view.value
    if (instance && instance.state.doc.toString() !== value) {
      applyingExternal = true
      instance.dispatch({
        changes: { from: 0, to: instance.state.doc.length, insert: value }
      })
      applyingExternal = false
    }
    window.clearTimeout(previewTimer)
    lastEmitted = value
    previewSource.value = value
  }
)

/* ---------- 滚动同步：按比例联动，只有主动滚的那一侧驱动另一侧 ---------- */

type PaneSide = 'source' | 'preview'

let scrollOwner: PaneSide | null = null
let scrollOwnerTimer: number | undefined
let syncFrame: number | undefined

/** 程序化改 scrollTop 也会触发 scroll 事件，所以「谁在滚」只认用户输入事件 */
function claimScroll(owner: PaneSide) {
  scrollOwner = owner
  window.clearTimeout(scrollOwnerTimer)
  scrollOwnerTimer = window.setTimeout(() => {
    scrollOwner = null
  }, 140)
}

function syncScrollFrom(owner: PaneSide) {
  if (scrollOwner !== owner || syncFrame !== undefined) return
  syncFrame = window.requestAnimationFrame(() => {
    syncFrame = undefined
    if (scrollOwner !== owner) return

    const sourceEl = view.value?.scrollDOM
    const previewEl = previewRef.value
    if (!sourceEl || !previewEl) return

    const fromEl = owner === 'source' ? sourceEl : previewEl
    const toEl = owner === 'source' ? previewEl : sourceEl
    const fromRange = fromEl.scrollHeight - fromEl.clientHeight
    const toRange = toEl.scrollHeight - toEl.clientHeight
    // 有一边压根没得滚就不联动，免得把短的那边顶到底
    if (fromRange <= 0 || toRange <= 0) return

    toEl.scrollTop = (fromEl.scrollTop / fromRange) * toRange
  })
}

/** scroll 只读不抢；用户输入事件才认领「谁在滚」，两侧的监听器都是同一套 */
function bindScrollSync(el: HTMLElement, owner: PaneSide) {
  const onScroll = () => syncScrollFrom(owner)
  const onIntent = () => claimScroll(owner)
  el.addEventListener('scroll', onScroll, { passive: true })
  el.addEventListener('wheel', onIntent, { passive: true })
  el.addEventListener('pointerdown', onIntent, { passive: true })
  el.addEventListener('touchstart', onIntent, { passive: true })
  el.addEventListener('keydown', onIntent)
  return () => {
    el.removeEventListener('scroll', onScroll)
    el.removeEventListener('wheel', onIntent)
    el.removeEventListener('pointerdown', onIntent)
    el.removeEventListener('touchstart', onIntent)
    el.removeEventListener('keydown', onIntent)
  }
}

let unbindSourceScroll: (() => void) | undefined
let unbindPreviewScroll: (() => void) | undefined

/* ---------- 分栏比例 ---------- */

function onNarrowChange(event: MediaQueryListEvent) {
  stacked.value = event.matches
}

/** resizeEnd 给的是像素尺寸，转成百分比再存；堆叠态不记比例 */
function onResizeEnd(_index: number, sizes: number[]) {
  if (stacked.value) return
  const [first, second] = sizes
  if (!first || !second) return
  const percent = Math.round((first / (first + second)) * 100)
  if (percent < MIN_PERCENT || percent > MAX_PERCENT) return
  leftSize.value = `${percent}%`
  window.localStorage.setItem(RATIO_KEY, String(percent))
}

/* ---------- 生命周期 ---------- */

let narrowQuery: MediaQueryList | null = null

onMounted(() => {
  narrowQuery = window.matchMedia(NARROW_QUERY)
  stacked.value = narrowQuery.matches
  narrowQuery.addEventListener('change', onNarrowChange)

  const host = sourceHostRef.value
  if (host) {
    view.value = new EditorView({
      doc: props.modelValue,
      extensions: editorExtensions(),
      parent: host
    })
    unbindSourceScroll = bindScrollSync(view.value.scrollDOM, 'source')
  }
  if (previewRef.value) {
    unbindPreviewScroll = bindScrollSync(previewRef.value, 'preview')
  }
})

onBeforeUnmount(() => {
  narrowQuery?.removeEventListener('change', onNarrowChange)
  unbindSourceScroll?.()
  unbindPreviewScroll?.()
  window.clearTimeout(scrollOwnerTimer)
  window.clearTimeout(previewTimer)
  if (syncFrame !== undefined) window.cancelAnimationFrame(syncFrame)
  view.value?.destroy()
  view.value = null
})

/* ---------- 对外方法 ---------- */

/** 把一段 Markdown 插到光标处（上传完图片后页面调用） */
function insertMarkdown(text: string) {
  const instance = view.value
  if (!instance) return
  instance.dispatch(instance.state.replaceSelection(text))
  instance.focus()
}

function pickImage() {
  fileRef.value?.click()
}

/** 选完只把文件透给页面去校验 + 上传；清空 value，连续选同一张图也能再触发 */
function onPickFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (files.length) emit('image-files', files)
}

function imageFiles(list: FileList | null | undefined) {
  return Array.from(list ?? []).filter((file) => file.type.startsWith('image/'))
}

/** 截图 / 图片粘贴：编辑器自己只处理文本，图片文件这里在捕获阶段先拦下来 */
function onPaste(event: ClipboardEvent) {
  const images = imageFiles(event.clipboardData?.files)
  if (!images.length) return
  event.preventDefault()
  emit('image-files', images)
}

/** 拖进来的是文件才拦，拖动选中文字这种交回给 CodeMirror */
function onDragOver(event: DragEvent) {
  if (event.dataTransfer?.types.includes('Files')) event.preventDefault()
}

function onDrop(event: DragEvent) {
  const files = Array.from(event.dataTransfer?.files ?? [])
  if (!files.length) return
  // 拦下文件，避免浏览器直接打开它、把编辑到一半的页面顶掉
  event.preventDefault()
  const images = files.filter((file) => file.type.startsWith('image/'))
  if (images.length) emit('image-files', images)
}

defineExpose({
  insertMarkdown,
  focus: () => view.value?.focus()
})
</script>

<template>
  <div class="split-editor" :class="{ 'is-stacked': stacked }">
    <el-splitter :layout="stacked ? 'vertical' : 'horizontal'" @resize-end="onResizeEnd">
      <el-splitter-panel :size="stacked ? '50%' : leftSize" min="28%">
        <div
          class="pane pane-source"
          @paste.capture="onPaste"
          @dragover.capture="onDragOver"
          @drop.capture="onDrop"
        >
          <!-- CodeMirror 的 DOM 是 JS 建好插进来的，样式走 EditorView.theme + 下面 :deep() -->
          <div ref="sourceHostRef" class="source-host"></div>

          <!-- 插入图片：也可以直接往源码区粘贴 / 拖拽图片 -->
          <button
            type="button"
            class="pane-action"
            :disabled="props.uploading"
            :title="props.uploading ? '图片上传中…' : '插入图片（也可以直接粘贴或拖拽图片）'"
            @click="pickImage"
          >
            <el-icon :class="{ 'is-loading': props.uploading }"><Picture /></el-icon>
          </button>

          <input
            ref="fileRef"
            class="pane-file"
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            multiple
            @change="onPickFiles"
          />
        </div>
      </el-splitter-panel>

      <el-splitter-panel min="24%">
        <div ref="previewRef" class="pane pane-preview">
          <MarkdownPreview
            :source="previewSource"
            empty-text="左边开始写，这里会实时显示渲染效果。"
          />
        </div>
      </el-splitter-panel>
    </el-splitter>
  </div>
</template>

<style scoped>
.split-editor {
  width: 100%;
}

/* 分屏整体高度：和上一版编辑器同一档手感；堆叠态两栏加起来要放得下 */
.split-editor :deep(.el-splitter) {
  height: clamp(460px, 66vh, 780px);
}

.split-editor.is-stacked :deep(.el-splitter) {
  height: clamp(620px, 84vh, 980px);
}

/* 中缝：默认细棕线，hover / 拖动时变亮 */
.split-editor :deep(.el-splitter-bar) {
  background: var(--panel-border);
}

.split-editor :deep(.el-splitter-bar:hover),
.split-editor :deep(.el-splitter-bar-active) {
  background: var(--accent-brown-soft);
}

.pane {
  position: relative;
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--panel-border);
  border-radius: 16px;
  background: var(--panel-bg);
}

.pane:focus-within {
  border-color: var(--accent-brown-soft);
}

.source-host {
  height: 100%;
}

/* 右栏正文：滚动在这里发生（styles/markdown.css 管正文样式，这里只管框与留白） */
.pane-preview {
  padding: 18px 20px;
  overflow-y: auto;
  color: var(--text-body);
  font-size: 14px;
  line-height: 1.8;
  overflow-wrap: anywhere;
  --markdown-gap: 14px;
  --markdown-font-size: 13px;
  --code-block-radius: 14px;
  --code-block-padding: 14px;
  --code-block-padding-top: 34px;
  --code-label-top: 11px;
  --code-label-left: 14px;
}

/* 右栏只有半边宽：表格按自然列宽渲染，超宽时在表格内部横向滚动 */
.pane-preview :deep(table) {
  display: block;
  width: max-content;
  max-width: 100%;
  overflow-x: auto;
}

/* 右上角「插入图片」：常驻但低调，鼠标进源码区或聚焦时亮起来 */
.pane-action {
  display: inline-flex;
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 5;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 1px solid var(--panel-border-strong);
  border-radius: 10px;
  color: var(--accent-brown);
  font-size: 15px;
  background: var(--panel-bg);
  box-shadow: 0 6px 18px var(--panel-border);
  cursor: pointer;
  opacity: 0.72;
  transition:
    opacity 0.2s ease,
    border-color 0.2s ease,
    background-color 0.2s ease;
}

.pane-source:hover .pane-action,
.pane-source:focus-within .pane-action {
  opacity: 1;
}

.pane-action:hover:not(:disabled) {
  border-color: var(--accent-brown-soft);
  background: var(--panel-alt-bg);
}

.pane-action:disabled {
  cursor: default;
  opacity: 0.5;
}

/* 隐藏原生文件选择框，点右上角按钮时由它弹出系统选择窗口 */
.pane-file {
  display: none;
}
</style>
