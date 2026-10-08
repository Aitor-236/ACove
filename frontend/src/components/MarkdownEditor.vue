<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { EditorView } from '@codemirror/view'
import { Picture } from '@element-plus/icons-vue'
import { createTypodown, type Typodown } from '@vemonet/typodown'
import '@vemonet/typodown/style.css'

/**
 * Typodown（CodeMirror 6）的 Vue 封装：后台正文用的实时预览 Markdown 编辑器。
 * 光标所在的结构显示原始标记（#、**、`），移开就渲染成正文；Markdown 字符串
 * 始终是唯一真源，保存时不做任何重排（前台渲染仍走 utils/markdown.ts）。
 *
 * 组件只做三件事：建/销毁编辑器、把 v-model 和编辑器内容对齐、把图片文件透给页面。
 * 上传、校验、提示这些业务逻辑留在 AdminArticleEdit.vue，这里不碰接口。
 *
 * 配色不在这里收口：Typodown 的元素是 JS 动态建的，scoped 样式选不中，
 * 主题统一写在 styles/typodown.css 的 .typodown[data-td-theme='acove'] 下。
 */
const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    height?: string
    /** 页面正在上传图片：上传按钮跟着转圈并禁用，避免重复点 */
    uploading?: boolean
  }>(),
  {
    placeholder: '',
    height: 'clamp(460px, 66vh, 780px)',
    uploading: false
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'image-files': [files: File[]]
  'raw-mode-change': [raw: boolean]
}>()

const hostRef = ref<HTMLElement | null>(null)
const fileRef = ref<HTMLInputElement | null>(null)
const editor = ref<Typodown | null>(null)

/**
 * 正文里的图片在库里存的是 /uploads/... 相对地址（等于接口路径去掉 /api 前缀），
 * 编辑器里统一补上 /api，跟 utils/markdown.ts 渲染正文的规则保持一致。
 */
function resolveImageSrc(src: string) {
  return src.startsWith('/uploads/') ? `/api${src}` : src
}

onMounted(() => {
  if (!hostRef.value) return

  editor.value = createTypodown(hostRef.value, {
    value: props.modelValue,
    // 固定浅色「acove」主题，不用 auto：免得跟随系统偏好翻成 GitHub 深色
    theme: 'acove',
    placeholder: props.placeholder,
    // 前台正文是 breaks: true（敲一次回车就换行），所以不要把软换行合并成一段
    joinSoftBreaks: false,
    html: true,
    spellcheck: false,
    tabSize: 4,
    // 悬浮格式工具栏和右侧大纲都不要，界面尽量干净
    toolbar: 'hidden',
    outline: false,
    persist: false,
    resolveImageSrc,
    onChange: (value) => emit('update:modelValue', value)
  })

  syncRawMode()
})

onBeforeUnmount(() => {
  editor.value?.destroy()
  editor.value = null
})

// 外部改内容（如 loadDetail 回填、切换文章）时同步进编辑器。
// 先比对再 setValue：内容一致就不动，避免和 onChange 来回触发。
watch(
  () => props.modelValue,
  (value) => {
    const instance = editor.value
    if (!instance || instance.getValue() === value) return
    instance.setValue(value)
  }
)

function syncRawMode() {
  emit('raw-mode-change', editor.value?.isRawMarkdown() ?? false)
}

/**
 * Typodown 的源码模式（Ctrl/⌘+/ 也能切）切完没有事件通知，只有内部状态，
 * 所以敲键盘时补一次同步；组件自己的按钮切换后直接调 syncRawMode。
 * 捕获阶段先于 CodeMirror 的按键处理执行，用微任务把读取推到它之后。
 */
let syncQueued = false
function queueRawModeSync() {
  if (syncQueued) return
  syncQueued = true
  queueMicrotask(() => {
    syncQueued = false
    syncRawMode()
  })
}

/**
 * 把一段 Markdown 插到光标处（上传完图片后调用）。
 *
 * Typodown 只暴露 getValue / setValue / focus，没有「按光标插入」的公开 API，
 * 这里取它内部的 CodeMirror view 直接 replaceSelection —— 运行时 view 就是实例上
 * 的普通属性（类型声明里只是标成 private）。万一取不到，退化成追加到文末，
 * 保证图片至少不会丢。
 */
function insertMarkdown(text: string) {
  const instance = editor.value
  if (!instance) return

  const view = (instance as unknown as { view?: EditorView }).view
  if (view) {
    view.dispatch(view.state.replaceSelection(text))
  } else {
    const current = instance.getValue()
    instance.setValue(`${current}${current && !current.endsWith('\n') ? '\n' : ''}${text}`)
  }
  instance.focus()
}

function toggleRawMode() {
  const instance = editor.value
  if (!instance) return
  instance.toggleRawMarkdown()
  syncRawMode()
  instance.focus()
}

function imageFiles(list: FileList | null | undefined) {
  return Array.from(list ?? []).filter((file) => file.type.startsWith('image/'))
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

/**
 * 截图 / 图片粘贴：Typodown 自己只处理文本和 HTML，图片文件这里在捕获阶段先拦下来。
 * 没有图片文件就不拦截，普通文本粘贴照旧交给编辑器。
 */
function onPaste(event: ClipboardEvent) {
  const images = imageFiles(event.clipboardData?.files)
  if (!images.length) return
  event.preventDefault()
  emit('image-files', images)
}

/** 拖进来的是文件才拦，拖动选中文字这种交回给 CodeMirror 自己处理 */
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
  toggleRawMode,
  focus: () => editor.value?.focus()
})
</script>

<template>
  <div class="markdown-editor" :style="{ height: props.height }">
    <!-- Typodown 只会往这个空壳里 append 自己的包装元素，按钮和文件框都是它的兄弟节点，
         免得 Vue 的 diff 和 Typodown 手动插入的 DOM 打架 -->
    <div
      ref="hostRef"
      class="markdown-editor-surface"
      @paste.capture="onPaste"
      @dragover.capture="onDragOver"
      @drop.capture="onDrop"
      @keydown.capture="queueRawModeSync"
    ></div>

    <!-- 插入图片：也可以直接往编辑区粘贴 / 拖拽图片 -->
    <button
      type="button"
      class="markdown-editor-image"
      :disabled="props.uploading"
      :title="props.uploading ? '图片上传中…' : '插入图片（也可以直接粘贴或拖拽图片）'"
      @click="pickImage"
    >
      <el-icon :class="{ 'is-loading': props.uploading }"><Picture /></el-icon>
    </button>

    <input
      ref="fileRef"
      class="markdown-editor-file"
      type="file"
      accept="image/png,image/jpeg,image/webp,image/gif"
      multiple
      @change="onPickFiles"
    />
  </div>
</template>
