<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import {
  ElInput,
  ElMessage,
  ElMessageBox,
  ElSelect,
  type FormInstance,
  type FormRules
} from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import request from '@/utils/request'
import MarkdownEditor from '@/components/MarkdownEditor.vue'
import { renderMarkdown } from '@/utils/markdown'
import { handleCodeGroupClick, handleCodeGroupKeydown } from '@/utils/codeBlock'
import { autoSaveStatusText, useAutoSave } from '@/composables/useAutoSave'

/** 后台文章详情（来自 GET /admin/article/{id}） */
interface AdminArticleDetail {
  id: number
  title: string
  summary: string
  content: string
  categoryId: number | null
  categoryName: string
  categorySlug: string
  tags: string[]
  status: 'draft' | 'published'
  publishedAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

/** 分类类型（来自 GET /category/list） */
interface CategoryItem {
  id: number
  name: string
  slug: string
  articleCount: number
}

/** 后台标签项（来自 GET /admin/tag/list） */
interface AdminTagItem {
  id: number
  name: string
  articleCount: number
}

/** MyBatis-Plus 分页返回结构 */
interface TagPage {
  records: AdminTagItem[]
  total: number
}

/** 正文配图上传结果（来自 POST /admin/upload/image） */
interface UploadedImage {
  url: string
  name: string
  size: number
}

const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
/** 正文编辑器（Typodown 封装），插入图片时往它的光标处塞 Markdown */
const editorRef = ref<InstanceType<typeof MarkdownEditor> | null>(null)
const form = reactive({
  title: '',
  summary: '',
  categoryName: '',
  content: '',
  tags: [] as string[]
})

const rules: FormRules<typeof form> = {
  title: [{ required: true, message: '请输入文章标题', trigger: 'blur' }],
  summary: [{ max: 500, message: '摘要不能超过 500 字', trigger: 'blur' }],
  categoryName: [{ required: true, message: '请选择文章分类', trigger: 'change' }]
}

/**
 * 分类 / 标签下拉框最下面都固定一项「新增××」，点开的是这两个小弹窗：
 * 先把新分类、新标签建进库里，再自动选中，免得在文章编辑器里手输一个库里没有的名字。
 */
const categorySelectRef = ref<InstanceType<typeof ElSelect> | null>(null)
const tagSelectRef = ref<InstanceType<typeof ElSelect> | null>(null)

const categoryDialogVisible = ref(false)
const categorySubmitting = ref(false)
const categoryFormRef = ref<FormInstance>()
const categoryForm = ref({ categoryName: '', categoryIdentifier: '' })

const categoryRules: FormRules<typeof categoryForm> = {
  categoryName: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { max: 50, message: '分类名称不能超过 50 个字符', trigger: 'blur' }
  ],
  categoryIdentifier: [
    { required: true, message: '请输入英文标识', trigger: 'blur' },
    { max: 50, message: '英文标识不能超过 50 个字符', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_-]+$/,
      message: '英文标识只能用字母、数字、连字符或下划线',
      trigger: 'blur'
    }
  ]
}

const tagDialogVisible = ref(false)
const tagSubmitting = ref(false)
const tagFormRef = ref<FormInstance>()
const tagForm = ref({ name: '' })

const tagRules: FormRules<typeof tagForm> = {
  name: [
    { required: true, message: '请输入标签名称', trigger: 'blur' },
    { max: 50, message: '标签名称不能超过 50 个字符', trigger: 'blur' }
  ]
}

/** 新建时为空，保存草稿成功后写入后端返回的ID */
const articleId = ref<number | null>(null)
const status = ref<'draft' | 'published'>('draft')
const meta = reactive({
  createdAt: '',
  updatedAt: '',
  publishedAt: ''
})

const categories = ref<CategoryItem[]>([])
/** 标签下拉选项，取后台标签库的前 100 个；编辑器里也能直接输入新标签 */
const tagOptions = ref<string[]>([])
const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const unpublishing = ref(false)
/** 正文图片正在上传：编辑器右上角那个按钮跟着转圈 */
const uploadingImage = ref(false)
const dirty = ref(false)
/** 回填表单期间不把接口返回的内容当成用户修改 */
const hydrating = ref(false)
/** 标题输入框，点「编辑」时聚焦过去 */
const titleInputRef = ref<InstanceType<typeof ElInput> | null>(null)
/**
 * 已有文章进来先是只读的渲染预览，点「编辑」才进入可写状态；
 * 新建页没有可预览的内容，直接就是可写状态。
 */
const editing = ref(true)
/** 已落库的分类 / 标签快照：这两项不自动保存，靠它判断还有没有必须手动保存的改动 */
const savedMeta = reactive({ categoryName: '', tags: [] as string[] })

const isEditMode = computed(() => articleId.value !== null)

function formatDateTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

async function loadCategories() {
  try {
    const res = (await request.get('/category/list')) as { data: CategoryItem[] }
    categories.value = res.data
  } catch {
    // 分类接口失败时仍可手动输入分类名，不阻塞编辑
    categories.value = []
  }
}

async function loadTags() {
  try {
    const res = (await request.get('/admin/tag/list', {
      params: { page: 1, size: 100 }
    })) as { data: TagPage }
    tagOptions.value = res.data.records.map((tag) => tag.name)
  } catch {
    // 标签接口失败时仍然可以手写标签名，不阻塞编辑
    tagOptions.value = []
  }
}

/** 打开新增分类弹窗：先收起下拉面板，否则面板会压在弹窗上面 */
function openCategoryDialog() {
  categorySelectRef.value?.blur()
  categoryForm.value = { categoryName: '', categoryIdentifier: '' }
  categoryDialogVisible.value = true
  void nextTick(() => categoryFormRef.value?.clearValidate())
}

/** 新建分类后直接选中它，省得再展开一次下拉框 */
async function submitCategory() {
  const valid = await categoryFormRef.value?.validate().catch(() => false)
  if (!valid) return

  categorySubmitting.value = true
  try {
    const res = (await request.post('/admin/category/create', {
      categoryName: categoryForm.value.categoryName.trim(),
      categoryIdentifier: categoryForm.value.categoryIdentifier.trim()
    })) as { data: CategoryItem }

    categories.value = [...categories.value, res.data]
    form.categoryName = res.data.name
    categoryDialogVisible.value = false
    ElMessage.success(`分类「${res.data.name}」已创建`)
  } catch {
    // 英文标识重复等业务失败由 request 拦截器弹出提示，这里保持弹窗打开方便修改
  } finally {
    categorySubmitting.value = false
  }
}

/** 打开新增标签弹窗，同样先把下拉面板收起来 */
function openTagDialog() {
  tagSelectRef.value?.blur()
  tagForm.value = { name: '' }
  tagDialogVisible.value = true
  void nextTick(() => tagFormRef.value?.clearValidate())
}

/** 新建标签后加进下拉选项并选中，编辑中的文章立刻带上这个标签 */
async function submitTag() {
  const valid = await tagFormRef.value?.validate().catch(() => false)
  if (!valid) return

  tagSubmitting.value = true
  try {
    const name = tagForm.value.name.trim()
    const res = (await request.post('/admin/tag/create', { name })) as { data: AdminTagItem }

    if (!tagOptions.value.includes(res.data.name)) {
      tagOptions.value = [...tagOptions.value, res.data.name]
    }
    if (!form.tags.includes(res.data.name)) {
      form.tags = [...form.tags, res.data.name]
    }
    tagDialogVisible.value = false
    ElMessage.success(`标签「${res.data.name}」已创建`)
  } catch {
    // 标签重名等业务失败由 request 拦截器弹出提示，这里保持弹窗打开方便修改
  } finally {
    tagSubmitting.value = false
  }
}

/** 记住当前已落库的分类 / 标签，用来判断「必须手动保存」的元数据有没有改过 */
function rememberSavedMeta() {
  savedMeta.categoryName = form.categoryName
  savedMeta.tags = [...form.tags]
}

/**
 * 拉取文章详情回填表单；enterPreview 为 true 时回到只读预览态
 * （首次进来和切换文章都要，发布 / 取消发布后不打断当前编辑）。
 */
async function loadDetail(id: string, { enterPreview = false }: { enterPreview?: boolean } = {}) {
  loading.value = true
  hydrating.value = true
  try {
    const res = (await request.get(`/admin/article/${id}`)) as { data: AdminArticleDetail }
    const detail = res.data
    articleId.value = detail.id
    status.value = detail.status
    form.title = detail.title ?? ''
    form.summary = detail.summary ?? ''
    form.categoryName = detail.categoryName ?? ''
    form.content = detail.content ?? ''
    form.tags = detail.tags ?? []
    meta.createdAt = detail.createdAt ?? ''
    meta.updatedAt = detail.updatedAt ?? ''
    meta.publishedAt = detail.publishedAt ?? ''
    rememberSavedMeta()
    // 换了文章：排队里的自动保存作废，状态文字清空
    resetAutoSave()
    if (enterPreview) {
      editing.value = false
    }
  } catch {
    ElMessage.error('文章不存在或已被删除')
    await router.replace('/admin/articles')
  } finally {
    loading.value = false
  }
  // 等表单 watcher 跑完再解除标记，否则回填会被记成一次未保存修改
  await nextTick()
  hydrating.value = false
  dirty.value = false
}

function goBack() {
  void router.push('/admin/articles')
}

/** 预览态正文：和前台详情页共用一份渲染规则（代码块、表格、代码组都能看） */
const previewContent = computed(() => {
  const source = form.content.trim()
  return source ? renderMarkdown(source) : ''
})

/** 分类 / 标签不参与自动保存，改过之后必须点「保存」才会落库 */
const metaDirty = computed(
  () =>
    form.categoryName !== savedMeta.categoryName ||
    form.tags.join('\u0000') !== savedMeta.tags.join('\u0000')
)

/**
 * 自动保存只覆盖文本：标题 / 摘要 / 正文。
 * 新建态要先选好分类（create 的必填项）才会真的建草稿，所以一并放进 enabled 里卡住。
 */
const {
  state: autoSaveState,
  lastSavedAt: autoSavedAt,
  pending: autoSavePending,
  schedule: scheduleAutoSave,
  flush: flushAutoSave,
  reset: resetAutoSave
} = useAutoSave({
  delay: 1000,
  save: async () => {
    const id = await ensureArticleId()
    const res = (await request.post(
      '/admin/article/update',
      {
        id,
        title: form.title.trim(),
        // 摘要和正文在库里是 NOT NULL，留空时提交空串
        summary: form.summary ?? '',
        content: form.content ?? ''
        // 不带 categoryName / tags：这两项只在点「保存」时提交
      },
      { silent: true }
    )) as { data: { updatedAt: string | null } }
    // 只补右侧「文章信息」的时间，不整页重拉，避免覆盖正在输入的内容
    if (res.data?.updatedAt) {
      meta.updatedAt = res.data.updatedAt
    }
  },
  enabled: () =>
    editing.value &&
    !hydrating.value &&
    form.title.trim().length > 0 &&
    (articleId.value !== null || form.categoryName.trim().length > 0)
})

/** 编辑态里只有这三个文本字段会触发自动保存 */
watch([() => form.title, () => form.summary, () => form.content], () => {
  if (hydrating.value || !editing.value) return
  scheduleAutoSave()
})

/** 新建态：分类选好之后，把刚才排队等着的第一次保存补上 */
watch(
  () => form.categoryName,
  () => {
    if (hydrating.value || articleId.value !== null || !editing.value) return
    scheduleAutoSave()
  }
)

/** 头部状态文字：保存中 / 失败要有反馈，标题为空这种「存不了」也要说清楚 */
const autoSaveHint = computed(() => {
  if (!editing.value) return ''
  if (autoSaveState.value === 'saving' || autoSaveState.value === 'error') {
    return autoSaveStatusText(autoSaveState.value, autoSavedAt.value)
  }
  if (autoSavePending.value && !form.title.trim()) return '标题不能为空，暂不自动保存'
  if (metaDirty.value) return '分类或标签改动待保存'
  if (autoSavePending.value) return ''
  return autoSaveStatusText(autoSaveState.value, autoSavedAt.value)
})

/** 点「编辑」进入可写状态，顺便把光标放到标题上 */
function startEditing() {
  editing.value = true
  void nextTick(() => titleInputRef.value?.focus())
}

/** 状态文字上的「重试」：把失败的自动保存再跑一次 */
function retryAutoSave() {
  void flushAutoSave()
}

/** 点「完成」：先把排队中的文本存掉，再回到只读预览 */
async function finishEditing() {
  if (dirty.value || autoSavePending.value) {
    const saved = await flushAutoSave()
    if (!saved) {
      if (form.title.trim()) {
        ElMessage.warning('还有改动没保存成功，先点「保存」再点完成')
      } else {
        ElMessage.warning('标题不能为空，先补上标题再点完成')
      }
      return
    }
  }
  if (metaDirty.value) {
    ElMessage.warning('分类或标签改过，点「保存」之后才会生效')
    return
  }
  editing.value = false
}

/** 只刷新右侧「文章信息」的时间，不动表单内容 */
async function refreshMeta(id: number) {
  try {
    const res = (await request.get(`/admin/article/${id}`)) as { data: AdminArticleDetail }
    meta.createdAt = res.data.createdAt ?? ''
    meta.updatedAt = res.data.updatedAt ?? ''
    meta.publishedAt = res.data.publishedAt ?? ''
  } catch {
    // 只是元信息，拿不到不影响继续编辑
  }
}

function buildPayload() {
  return {
    title: form.title.trim(),
    // 摘要和正文在库里是 NOT NULL，留空时提交空串而不是 undefined
    summary: form.summary ?? '',
    content: form.content ?? '',
    categoryName: form.categoryName,
    // 标签整体提交，空数组表示清空这篇文章的标签
    tags: form.tags
  }
}

/** 上传图片并把 Markdown 图片语法插到光标处，编辑器和前台详情页都会显示。 */
async function insertImage(file: File) {
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('只能插入图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 5MB')
    return
  }

  const formData = new FormData()
  formData.append('file', file)

  uploadingImage.value = true
  try {
    // 上传比普通请求慢，单独放宽超时（默认 5 秒）
    const res = (await request.post('/admin/upload/image', formData, { timeout: 20000 })) as {
      data: UploadedImage
    }
    const alt = file.name.replace(/\.[^.]+$/, '').replace(/[[\]]/g, '') || '图片'
    editorRef.value?.insertMarkdown(`![${alt}](${res.data.url})`)
    ElMessage.success('图片已插入正文')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    uploadingImage.value = false
  }
}

/**
 * 编辑器里粘贴 / 拖进来的图片文件：在编辑器的光标处逐个插入。
 */
async function onImageFiles(files: File[]) {
  for (const file of files) {
    await insertImage(file)
  }
}

/**
 * 保证当前文章已经落库：新建态先调 create 拿到ID，并把地址栏换成编辑态地址，
 * 这样之后的保存和发布都是对同一篇文章的更新。
 */
async function ensureArticleId() {
  if (articleId.value !== null) return articleId.value

  const res = (await request.post('/admin/article/create', buildPayload())) as {
    data: { id: number }
  }
  articleId.value = res.data.id
  rememberSavedMeta()
  // 内容已经落库，先把"未保存"标记清掉，否则下面换地址会被离开确认拦下来
  dirty.value = false
  // 静默把地址换成编辑态地址，之后所有保存都是对同一篇文章的更新
  await router.replace(`/admin/articles/${res.data.id}/edit`)
  await refreshMeta(res.data.id)
  return res.data.id
}

/**
 * 手动保存：新建走 create，已有ID走 update；这一路会把分类和标签一起提交，
 * 所以它是「分类 / 标签改完必须点一下」的那颗按钮。保存后仍留在编辑态。
 */
async function saveDraft() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    // 先把排队中的自动保存跑完，免得两个请求乱序把旧内容写回去
    await flushAutoSave()
    const isNew = articleId.value === null
    const id = await ensureArticleId()
    if (!isNew) {
      await request.post('/admin/article/update', { id, ...buildPayload() })
    }
    rememberSavedMeta()
    dirty.value = false
    resetAutoSave()
    await refreshMeta(id)
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}

/** 发布前先落库再调发布接口，保证发布的是当前编辑器里的内容。 */
async function publishArticle() {
  // 预览态没有表单（也没有未保存的文本），直接拿已落库的内容去发布
  if (editing.value) {
    if (!formRef.value) return
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) return
  }

  if (!form.summary.trim()) {
    ElMessage.warning('发布前请先填写摘要，前台文章卡片会用到')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('发布前请先填写正文')
    return
  }

  publishing.value = true
  try {
    // 发布前先把自动保存排队的内容落库，避免下面的 update 被旧响应覆盖
    await flushAutoSave()
    const isNew = articleId.value === null
    const id = await ensureArticleId()
    if (!isNew) {
      await request.post('/admin/article/update', { id, ...buildPayload() })
    }

    rememberSavedMeta()
    dirty.value = false
    await request.post('/admin/article/publish', null, { params: { id } })
    await loadDetail(String(id), { enterPreview: false })
    ElMessage.success('文章已发布，前台文章列表已经可以看到')
  } finally {
    publishing.value = false
  }
}

/** 取消发布：内容保留，文章从前台下线回退为草稿。 */
async function unpublishArticle() {
  if (articleId.value === null) return

  try {
    await ElMessageBox.confirm(
      '取消发布后文章会从前台列表下线，内容保留为草稿，确定取消发布吗？',
      '取消发布',
      { confirmButtonText: '取消发布', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch {
    return
  }

  unpublishing.value = true
  try {
    await request.post('/admin/article/unpublish', null, { params: { id: articleId.value } })
    ElMessage.success('已取消发布，文章回退为草稿')
    await loadDetail(String(articleId.value), { enterPreview: false })
  } finally {
    unpublishing.value = false
  }
}

// 自己保存后 replace 的地址不重复拉取，避免覆盖正在输入的内容
watch(
  () => route.params.id,
  (id) => {
    const next = typeof id === 'string' && id ? id : ''
    if (!next || String(articleId.value) === next) return
    void loadDetail(next, { enterPreview: true })
  }
)

watch(
  form,
  () => {
    if (hydrating.value) return
    dirty.value = true
  },
  { deep: true }
)

onBeforeRouteLeave(async () => {
  if (!dirty.value && !autoSavePending.value) return true

  // 先把排队中的自动保存跑完；能存下去、也没有必须手动保存的改动，就直接放行
  const textSaved = await flushAutoSave()
  if (textSaved && !metaDirty.value) return true

  try {
    await ElMessageBox.confirm(
      textSaved
        ? '分类或标签的改动还没保存，离开后这次修改会丢失，确定离开吗？'
        : '还有改动没保存成功，离开后这次修改会丢失，确定离开吗？',
      '未保存的修改',
      {
        confirmButtonText: '离开',
        cancelButtonText: '继续编辑',
        type: 'warning'
      }
    )
    return true
  } catch {
    return false
  }
})

/** 关标签页 / 刷新时浏览器原生的离开确认 */
function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!dirty.value && !autoSavePending.value) return
  event.preventDefault()
  event.returnValue = ''
}

onMounted(async () => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  await Promise.all([loadCategories(), loadTags()])
  const id = typeof route.params.id === 'string' ? route.params.id : ''
  if (id) {
    // 已有文章先给只读预览，点「编辑」再动内容
    await loadDetail(id, { enterPreview: true })
  } else {
    // 新建页没有可预览的东西，直接可写
    editing.value = true
  }
  dirty.value = false
})

onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload))
</script>

<template>
  <div v-loading="loading" class="admin-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">
          {{ isEditMode ? '编辑文章' : '新建文章' }}
          <el-tag :type="status === 'published' ? 'success' : 'info'" effect="light" class="status-tag">
            {{ status === 'published' ? '已发布' : '草稿' }}
          </el-tag>
        </h1>
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

        <el-button v-if="!editing" type="primary" @click="startEditing">编辑</el-button>
        <template v-else>
          <el-button :loading="saving" @click="saveDraft">保存</el-button>
          <el-button @click="finishEditing">完成</el-button>
        </template>

        <router-link v-if="status === 'published' && articleId" :to="`/articles/${articleId}`" target="_blank">
          <el-button>查看前台</el-button>
        </router-link>
        <el-button @click="goBack">返回列表</el-button>
        <el-button
          v-if="status === 'published'"
          :loading="unpublishing"
          class="warning-button"
          @click="unpublishArticle"
        >
          取消发布
        </el-button>
        <el-button v-else type="primary" :loading="publishing" @click="publishArticle">
          发布文章
        </el-button>
      </div>
    </header>

    <div class="editor-layout">
      <!-- 只读预览：标题 / 摘要当普通文字，正文用前台详情页那套 Markdown 渲染 -->
      <section v-if="!editing" class="admin-panel editor-panel preview-panel">
        <h2 class="preview-title">{{ form.title || '（未命名）' }}</h2>

        <div v-if="form.categoryName || form.tags.length" class="preview-chips">
          <span v-if="form.categoryName" class="preview-chip is-category">
            {{ form.categoryName }}
          </span>
          <span v-for="tag in form.tags" :key="tag" class="preview-chip">{{ tag }}</span>
        </div>

        <p v-if="form.summary" class="preview-summary">{{ form.summary }}</p>

        <div
          v-if="previewContent"
          class="markdown-body preview-body"
          v-html="previewContent"
          @click="handleCodeGroupClick"
          @keydown="handleCodeGroupKeydown"
        ></div>
        <p v-else class="preview-empty">正文还是空的，点右上角「编辑」开始写。</p>
      </section>

      <section v-else class="admin-panel editor-panel">
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
          <el-form-item label="文章标题" prop="title">
            <el-input
              ref="titleInputRef"
              v-model="form.title"
              placeholder="一句话说清这篇文章讲什么"
              maxlength="200"
            />
          </el-form-item>

          <el-form-item label="文章摘要" prop="summary">
            <el-input
              v-model="form.summary"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="出现在文章列表卡片上的简介，发布时必填"
            />
          </el-form-item>

          <el-form-item label="文章分类" prop="categoryName">
            <el-select
              ref="categorySelectRef"
              v-model="form.categoryName"
              class="category-select"
              popper-class="admin-select-popper"
              placeholder="选择文章分类"
              filterable
              default-first-option
            >
              <el-option
                v-for="category in categories"
                :key="category.slug"
                :label="category.name"
                :value="category.name"
              />
              <template #footer>
                <button type="button" class="select-create" @click="openCategoryDialog">
                  <el-icon><Plus /></el-icon>
                  新增分类
                </button>
              </template>
              <template #empty>
                <p class="select-empty">没有匹配的分类，点下面的「新增分类」就能建一个</p>
              </template>
            </el-select>
          </el-form-item>

          <el-form-item label="标签" prop="tags">
            <el-select
              ref="tagSelectRef"
              v-model="form.tags"
              class="tag-select"
              popper-class="admin-select-popper"
              multiple
              filterable
              allow-create
              default-first-option
              :reserve-keyword="false"
              placeholder="选择已有标签，或输入新标签后回车"
            >
              <el-option v-for="tag in tagOptions" :key="tag" :label="tag" :value="tag" />
              <template #footer>
                <button type="button" class="select-create" @click="openTagDialog">
                  <el-icon><Plus /></el-icon>
                  新增标签
                </button>
              </template>
            </el-select>
          </el-form-item>

          <el-form-item label="正文（Markdown）" prop="content">
            <!-- 正文用 Typodown：光标所在结构显示源码，移开即渲染（组件见 components/MarkdownEditor.vue） -->
            <MarkdownEditor
              ref="editorRef"
              v-model="form.content"
              placeholder="# 标题：正文直接写 Markdown，列表、表格、代码块、引用、图片都支持"
              :uploading="uploadingImage"
              @image-files="onImageFiles"
            />
          </el-form-item>
        </el-form>
      </section>

      <aside class="editor-side">
        <section v-if="isEditMode" class="admin-panel meta-panel">
          <h2>文章信息</h2>
          <dl class="meta-list">
            <div>
              <dt>创建时间</dt>
              <dd>{{ formatDateTime(meta.createdAt) }}</dd>
            </div>
            <div>
              <dt>更新时间</dt>
              <dd>{{ formatDateTime(meta.updatedAt) }}</dd>
            </div>
            <div>
              <dt>发布时间</dt>
              <dd>{{ status === 'published' ? formatDateTime(meta.publishedAt) : '—' }}</dd>
            </div>
            <div>
              <dt>文章ID</dt>
              <dd>#{{ articleId }}</dd>
            </div>
          </dl>
        </section>
      </aside>
    </div>

    <!-- 下拉框底部的「新增分类」：分类必须先在库里有，编辑器这边只负责选中 -->
    <el-dialog
      v-model="categoryDialogVisible"
      title="新增分类"
      width="440"
      :close-on-click-modal="false"
    >
      <el-form
        ref="categoryFormRef"
        :model="categoryForm"
        :rules="categoryRules"
        label-position="top"
        size="large"
        @submit.prevent
      >
        <el-form-item label="分类名称" prop="categoryName">
          <el-input
            v-model="categoryForm.categoryName"
            maxlength="50"
            placeholder="例如：前端"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="英文标识" prop="categoryIdentifier">
          <el-input
            v-model="categoryForm.categoryIdentifier"
            maxlength="50"
            placeholder="例如：frontend"
            show-word-limit
            @keyup.enter="submitCategory"
          />
          <p class="form-tip">用于前台地址栏筛选，创建后会自动选中这个分类</p>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="categorySubmitting" @click="categoryDialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="categorySubmitting" @click="submitCategory">
          创建
        </el-button>
      </template>
    </el-dialog>

    <!-- 下拉框底部的「新增标签」：建好之后立刻挂到当前文章上 -->
    <el-dialog
      v-model="tagDialogVisible"
      title="新增标签"
      width="420"
      :close-on-click-modal="false"
    >
      <el-form
        ref="tagFormRef"
        :model="tagForm"
        :rules="tagRules"
        label-position="top"
        size="large"
        @submit.prevent
      >
        <el-form-item label="标签名称" prop="name">
          <el-input
            v-model="tagForm.name"
            maxlength="50"
            placeholder="例如：Vue"
            show-word-limit
            @keyup.enter="submitTag"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="tagSubmitting" @click="tagDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="tagSubmitting" @click="submitTag">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.status-tag {
  margin-left: 10px;
  vertical-align: middle;
}

/* 顶部自动保存状态：保存中 / 已保存 HH:mm / 保存失败 · 重试 */
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

.preview-panel {
  padding: 26px 26px 30px;
}

.preview-title {
  margin: 0;
  color: var(--text-strong);
  font-size: 24px;
  line-height: 1.45;
}

.preview-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.preview-chip {
  padding: 3px 12px;
  border-radius: 999px;
  color: var(--accent-brown);
  font-size: 12px;
  background: var(--panel-alt-bg);
  box-shadow: inset 0 0 0 1px rgba(138, 90, 59, 0.22);
}

.preview-chip.is-category {
  color: #7a5436;
  background: #ecdec5;
  box-shadow: none;
}

.preview-summary {
  margin: 16px 0 0;
  color: var(--text-body);
  font-size: 14px;
  line-height: 1.8;
}

/* 正文渲染块：样式来自全局 styles/markdown.css，这里只加分隔线 */
.preview-body {
  margin-top: 22px;
  padding-top: 22px;
  border-top: 1px solid rgba(138, 90, 59, 0.16);
}

.preview-empty {
  margin: 24px 0 0;
  color: var(--text-muted);
  font-size: 14px;
}

.editor-layout {
  display: grid;
  /* 正文编辑器已经自带实时预览，右侧只剩「文章信息」，所以这里收窄成一条侧栏 */
  grid-template-columns: minmax(0, 1fr) minmax(240px, 300px);
  gap: 18px;
  align-items: start;
}

.editor-panel {
  padding: 22px 22px 6px;
}

.category-select {
  width: 100%;
}

.tag-select {
  width: 100%;
}

/*
 * 下拉框最下面固定的「新增分类 / 新增标签」。
 * 这一项放在 el-select 的 footer 插槽里，不跟着选项列表滚动，永远贴在面板底部。
 */
.select-create {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 38px;
  padding: 0 20px;
  border: none;
  color: var(--accent-brown);
  font-family: inherit;
  font-size: 14px;
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.select-create:hover,
.select-create:focus-visible {
  /* 和上面选项行 hover 用同一个底色（变量来自下拉面板，见 styles/admin.css） */
  background: var(--el-fill-color-light, #f6efe3);
  outline: none;
}

.select-create .el-icon {
  font-size: 16px;
}

/* 分类下拉框里搜不到东西时的提示，指向下面那条「新增分类」 */
.select-empty {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.form-tip {
  width: 100%;
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.editor-side {
  display: flex;
  position: sticky;
  top: 20px;
  flex-direction: column;
  gap: 18px;
}

.meta-panel {
  padding: 18px 20px 22px;
}

.meta-panel h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 16px;
  font-weight: 600;
}

.meta-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 14px 0 0;
}

.meta-list div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
}

.meta-list dt {
  color: var(--text-muted);
}

.meta-list dd {
  margin: 0;
  color: var(--text-strong);
}

@media (max-width: 1200px) {
  .editor-layout {
    grid-template-columns: 1fr;
  }

  .editor-side {
    position: static;
  }
}
</style>
