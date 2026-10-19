<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import MarkdownSplitEditor from '@/components/MarkdownSplitEditor.vue'
import request from '@/utils/request'

type ProfilePageMode = 'markdown' | 'html'

interface AdminProfilePage {
  mode: ProfilePageMode
  markdownContent: string
  htmlBundleId: string
  previewApiUrl: string
}

interface UploadedHtmlPackage {
  bundleId: string
  previewApiUrl: string
}

interface UploadedImage {
  url: string
}

const FILE_MAX_SIZE = 5 * 1024 * 1024

const loading = ref(true)
const loadError = ref(false)
const saving = ref(false)
const uploading = ref(false)
const previewLoading = ref(false)
const mode = ref<ProfilePageMode>('markdown')
const markdownContent = ref('')
const htmlBundleId = ref('')
const previewApiUrl = ref('')
const htmlPreviewDocument = ref('')
const fileInputRef = ref<HTMLInputElement | null>(null)
const htmlFrameRef = ref<HTMLIFrameElement | null>(null)
const editorRef = ref<{ insertMarkdown: (text: string) => void } | null>(null)
const currentPreviewUrl = ref('')

const htmlPreviewTitle = computed(() =>
  htmlBundleId.value ? 'HTML 页面预览' : '还没有导入 HTML 页面',
)

let frameResizeObserver: ResizeObserver | null = null

function applyAdminPage(data: AdminProfilePage) {
  mode.value = data.mode === 'html' ? 'html' : 'markdown'
  markdownContent.value = data.markdownContent || ''
  htmlBundleId.value = data.htmlBundleId || ''
  previewApiUrl.value = data.previewApiUrl || ''
}

async function loadHtmlPreview(url = previewApiUrl.value) {
  if (!url) {
    htmlPreviewDocument.value = ''
    currentPreviewUrl.value = ''
    return
  }

  previewLoading.value = true
  try {
    const res = (await request.get(url)) as { data: string }
    htmlPreviewDocument.value = typeof res.data === 'string' ? res.data : ''
    currentPreviewUrl.value = url
  } catch {
    htmlPreviewDocument.value = ''
  } finally {
    previewLoading.value = false
  }
}

async function loadProfilePage() {
  loading.value = true
  loadError.value = false
  try {
    const res = (await request.get('/admin/profile-page')) as { data: AdminProfilePage }
    applyAdminPage(res.data)
    if (res.data.previewApiUrl) {
      await loadHtmlPreview(res.data.previewApiUrl)
    }
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function onModeChange(nextMode: string | number | boolean) {
  if (nextMode !== 'markdown' && nextMode !== 'html') return
  mode.value = nextMode
  if (
    nextMode === 'html' &&
    previewApiUrl.value &&
    currentPreviewUrl.value !== previewApiUrl.value
  ) {
    await loadHtmlPreview()
  }
}

function pickHtmlPackage() {
  fileInputRef.value?.click()
}

async function uploadHtmlPackage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  if (!file.name.toLowerCase().endsWith('.zip')) {
    ElMessage.warning('请选择 ZIP 格式的 HTML 资源包')
    return
  }
  if (file.size > FILE_MAX_SIZE) {
    ElMessage.warning('ZIP 文件不能超过 5MB')
    return
  }

  const formData = new FormData()
  formData.append('file', file)
  uploading.value = true
  try {
    const res = (await request.post('/admin/profile-page/html-package', formData, {
      timeout: 30000,
    })) as { data: UploadedHtmlPackage }
    htmlBundleId.value = res.data.bundleId
    previewApiUrl.value = res.data.previewApiUrl
    mode.value = 'html'
    currentPreviewUrl.value = ''
    await loadHtmlPreview(res.data.previewApiUrl)
    ElMessage.success('资源包校验通过，可以预览并保存')
  } catch {
    // 错误由 request 拦截器提示，继续保留当前已保存内容。
  } finally {
    uploading.value = false
  }
}

async function insertImage(file: File) {
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('只能插入图片文件')
    return
  }
  if (file.size > FILE_MAX_SIZE) {
    ElMessage.warning('图片不能超过 5MB')
    return
  }

  const formData = new FormData()
  formData.append('file', file)
  uploading.value = true
  try {
    const res = (await request.post('/admin/upload/image', formData, { timeout: 20000 })) as {
      data: UploadedImage
    }
    const alt = file.name.replace(/\.[^.]+$/, '').replace(/[\[\]]/g, '') || '图片'
    editorRef.value?.insertMarkdown(`![${alt}](${res.data.url})`)
    ElMessage.success('图片已插入正文')
  } catch {
    // 错误由 request 拦截器提示。
  } finally {
    uploading.value = false
  }
}

async function onImageFiles(files: File[]) {
  for (const file of files) {
    await insertImage(file)
  }
}

async function saveProfilePage() {
  if (mode.value === 'html' && !htmlBundleId.value) {
    ElMessage.warning('请先导入并预览一个 HTML 资源包')
    return
  }

  saving.value = true
  try {
    const res = (await request.post('/admin/profile-page/save', {
      mode: mode.value,
      markdownContent: markdownContent.value,
      htmlBundleId: htmlBundleId.value,
    })) as { data: AdminProfilePage }
    const previousPreviewUrl = previewApiUrl.value
    applyAdminPage(res.data)
    if (
      mode.value === 'html' &&
      previewApiUrl.value &&
      previousPreviewUrl !== previewApiUrl.value
    ) {
      await loadHtmlPreview(previewApiUrl.value)
    }
    ElMessage.success('个人简介已保存并公开')
  } catch {
    // 错误由 request 拦截器提示，保留编辑内容。
  } finally {
    saving.value = false
  }
}

function resizePreviewFrame(event: Event) {
  const frame = event.currentTarget as HTMLIFrameElement
  const document = frame.contentDocument
  if (!document) return

  frameResizeObserver?.disconnect()
  const resize = () => {
    const body = document.body
    const root = document.documentElement
    const height = Math.max(
      body?.scrollHeight ?? 0,
      body?.offsetHeight ?? 0,
      root?.scrollHeight ?? 0,
      root?.offsetHeight ?? 0,
    )
    frame.style.height = `${Math.max(height, 1)}px`
  }

  resize()
  if (typeof ResizeObserver !== 'undefined') {
    frameResizeObserver = new ResizeObserver(resize)
    if (document.body) frameResizeObserver.observe(document.body)
    frameResizeObserver.observe(document.documentElement)
  }
  document.addEventListener('load', resize, true)
  document.fonts?.ready.then(resize).catch(() => {})
}

onMounted(() => {
  void loadProfilePage()
})

onBeforeUnmount(() => {
  frameResizeObserver?.disconnect()
  frameResizeObserver = null
})
</script>

<template>
  <div class="admin-page admin-profile-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">个人简介</h1>
      </div>
      <div class="admin-page-actions">
        <el-button
          type="primary"
          :loading="saving"
          :disabled="
            loading ||
            loadError ||
            uploading ||
            (mode === 'html' && (!htmlBundleId || !htmlPreviewDocument || previewLoading))
          "
          @click="saveProfilePage"
        >
          保存并公开
        </el-button>
      </div>
    </header>

    <section v-if="loading" class="admin-panel admin-state">正在加载个人简介设置…</section>
    <section v-else-if="loadError" class="admin-panel admin-state">
      <p>个人简介设置加载失败，可能没有站长权限。</p>
      <el-button type="primary" @click="loadProfilePage">重新加载</el-button>
    </section>

    <template v-else>
      <section class="admin-panel profile-mode-panel">
        <div class="profile-mode-copy">
          <strong>展示方式</strong>
        </div>
        <el-radio-group
          :model-value="mode"
          class="profile-mode-switch"
          :disabled="saving || uploading"
          aria-label="个人简介展示方式"
          @update:model-value="onModeChange"
        >
          <el-radio-button value="markdown">Markdown</el-radio-button>
          <el-radio-button value="html">导入 HTML</el-radio-button>
        </el-radio-group>
      </section>

      <MarkdownSplitEditor
        v-if="mode === 'markdown'"
        ref="editorRef"
        v-model:model-value="markdownContent"
        :uploading="uploading"
        :placeholder="'# 关于我\\n\\n写下你的个人介绍…'"
        @image-files="onImageFiles"
      />

      <template v-else>
        <section class="admin-panel html-import-panel">
          <div class="html-import-copy">
            <strong>单页 HTML 资源包</strong>
            <p>
              上传 ZIP，根目录需要有且只有一个 <code>index.html</code>。可包含
              CSS、图片和字体，支持内联静态 SVG 图标和文字；不支持脚本、表单、iframe、外部资源或其他 HTML 页面。
            </p>
            <span
              >ZIP 最大 5MB，解压后最多 20MB、100 个文件。导入后先预览，再点击「保存并公开」。</span
            >
          </div>
          <div class="html-import-actions">
            <input
              ref="fileInputRef"
              class="html-file-input"
              type="file"
              accept=".zip,application/zip"
              @change="uploadHtmlPackage"
            />
            <el-button :loading="uploading" @click="pickHtmlPackage">
              <el-icon><Upload /></el-icon>
              {{ htmlBundleId ? '替换资源包' : '导入 ZIP' }}
            </el-button>
          </div>
        </section>

        <section class="admin-panel html-preview-panel">
          <div class="html-preview-heading">
            <strong>{{ htmlPreviewTitle }}</strong>
            <span v-if="htmlBundleId">当前预览不会公开，保存后访客即可查看。</span>
          </div>
          <div v-if="previewLoading" class="html-preview-state">正在加载 HTML 预览…</div>
          <div v-else-if="htmlPreviewDocument" class="html-frame-wrap">
            <iframe
              ref="htmlFrameRef"
              :key="currentPreviewUrl"
              class="html-preview-frame"
              :srcdoc="htmlPreviewDocument"
              title="个人简介 HTML 预览"
              sandbox="allow-same-origin allow-popups"
              referrerpolicy="no-referrer"
              @load="resizePreviewFrame"
            ></iframe>
          </div>
          <div v-else class="html-preview-state">
            {{
              htmlBundleId
                ? '预览加载失败，请重新加载后再保存。'
                : '导入资源包后会在这里显示隔离预览。'
            }}
            <el-button
              v-if="htmlBundleId && !previewLoading"
              link
              type="primary"
              @click="loadHtmlPreview()"
            >
              重新加载预览
            </el-button>
          </div>
        </section>
      </template>
    </template>
  </div>
</template>

<style scoped>
.admin-profile-page {
  gap: 16px;
}

.profile-mode-panel {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
}

.profile-mode-copy,
.html-import-copy,
.html-preview-heading {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.profile-mode-copy strong,
.html-import-copy strong,
.html-preview-heading strong {
  color: var(--text-strong);
  font-size: 15px;
}

.profile-mode-copy span,
.html-import-copy span,
.html-preview-heading span {
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.profile-mode-switch {
  flex: 0 0 auto;
}

.html-import-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 20px;
}

.html-import-copy {
  min-width: 0;
}

.html-import-copy p {
  margin: 0;
  color: var(--text-body);
  font-size: 13px;
  line-height: 1.7;
}

.html-import-copy code {
  padding: 2px 5px;
  border-radius: 6px;
  color: var(--accent-brown);
  background: var(--panel-alt-bg);
}

.html-import-actions {
  flex: 0 0 auto;
}

.html-file-input {
  display: none;
}

.html-preview-panel {
  overflow: hidden;
}

.html-preview-heading {
  padding: 18px 20px;
  border-bottom: 1px solid var(--panel-border);
}

.html-frame-wrap {
  background: var(--panel-bg, #fffdf9);
}

.html-preview-frame {
  display: block;
  width: 100%;
  min-height: 320px;
  border: 0;
  background: var(--panel-bg, #fffdf9);
}

.html-preview-state {
  padding: 48px 20px;
  color: var(--text-muted);
  text-align: center;
  font-size: 13px;
}

@media (max-width: 640px) {
  .profile-mode-panel,
  .html-import-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .profile-mode-switch {
    width: 100%;
  }

  .profile-mode-switch :deep(.el-radio-button) {
    flex: 1 1 0;
  }

  .html-import-actions .el-button {
    width: 100%;
  }
}
</style>
