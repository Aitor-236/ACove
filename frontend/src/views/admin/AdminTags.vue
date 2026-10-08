<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'

/** 后台标签项（来自 GET /admin/tag/list） */
interface AdminTagItem {
  id: number
  name: string
  articleCount: number
  createdAt: string | null
}

/** MyBatis-Plus 分页返回结构 */
interface TagPage {
  records: AdminTagItem[]
  total: number
  current: number
  size: number
  pages: number
}

const PAGE_SIZE = 10

const tags = ref<AdminTagItem[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const keywordInput = ref('')
const appliedKeyword = ref('')
/** 正在删除的标签ID，用来只禁用这一行的按钮 */
const busyId = ref<number | null>(null)

const dialogVisible = ref(false)
const submitting = ref(false)
/** null 表示新建，否则表示正在重命名这个标签 */
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = ref({ name: '' })

const dialogTitle = computed(() => (editingId.value === null ? '新建标签' : '编辑标签'))

const rules: FormRules<typeof form> = {
  name: [
    { required: true, message: '请输入标签名称', trigger: 'blur' },
    { max: 50, message: '标签名称不能超过 50 个字符', trigger: 'blur' }
  ]
}

function formatDateTime(value?: string | null) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

function rowDisabled(id: number) {
  return busyId.value !== null && busyId.value !== id
}

async function loadTags() {
  loading.value = true
  try {
    const res = (await request.get('/admin/tag/list', {
      params: {
        page: page.value,
        size: PAGE_SIZE,
        keyword: appliedKeyword.value || undefined
      }
    })) as { data: TagPage }

    tags.value = res.data.records
    total.value = res.data.total
  } catch {
    // 错误提示由 request 拦截器统一处理
    tags.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function reloadFromFirstPage() {
  page.value = 1
  await loadTags()
}

function applySearch() {
  appliedKeyword.value = keywordInput.value.trim()
  void reloadFromFirstPage()
}

function clearSearch() {
  keywordInput.value = ''
  appliedKeyword.value = ''
  void reloadFromFirstPage()
}

function changePage(next: number) {
  const totalPages = Math.max(1, Math.ceil(total.value / PAGE_SIZE))
  if (next < 1 || next > totalPages || next === page.value) return
  page.value = next
  void loadTags()
}

/** 打开弹窗前先复位表单和上一次的校验提示 */
function openDialog(id: number | null, name: string) {
  editingId.value = id
  form.value = { name }
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function openCreate() {
  openDialog(null, '')
}

function openEdit(row: AdminTagItem) {
  openDialog(row.id, row.name)
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const name = form.value.name.trim()
    if (editingId.value === null) {
      await request.post('/admin/tag/create', { name })
      ElMessage.success('标签已创建')
      // 新标签排在列表最后，回到第一页才能看到
      await reloadFromFirstPage()
    } else {
      await request.post('/admin/tag/update', { id: editingId.value, name })
      ElMessage.success('标签已更新')
      await loadTags()
    }
    dialogVisible.value = false
  } catch {
    // 重名等业务失败由 request 拦截器弹出提示，这里保持弹窗打开方便修改
  } finally {
    submitting.value = false
  }
}

async function removeTag(row: AdminTagItem) {
  const usage =
    row.articleCount > 0
      ? `该标签正被 ${row.articleCount} 篇文章使用，删除后这些文章会同时移除该标签。`
      : '该标签还没有被任何文章使用。'

  try {
    await ElMessageBox.confirm(`${usage}删除后无法恢复，确定删除「${row.name}」吗？`, '删除标签', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    // 用户点了取消
    return
  }

  busyId.value = row.id
  try {
    await request.post('/admin/tag/delete', null, { params: { id: row.id } })
    ElMessage.success('标签已删除')
    // 删掉当前页最后一条时回退一页，避免停在空页
    if (tags.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await loadTags()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    busyId.value = null
  }
}

onMounted(loadTags)
</script>

<template>
  <div class="admin-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">标签管理</h1>
      </div>

      <div class="admin-page-actions">
        <el-button :loading="loading" @click="loadTags">刷新</el-button>
        <el-button type="primary" @click="openCreate">新建标签</el-button>
      </div>
    </header>

    <section class="admin-panel toolbar">
      <!-- 输入框和搜索按钮作为一个整体换行，窄屏时不会拆散 -->
      <div class="search-group">
        <el-input
          v-model="keywordInput"
          class="keyword-input"
          placeholder="搜索标签名称"
          clearable
          size="large"
          @keyup.enter="applySearch"
          @clear="clearSearch"
        >
          <template #prefix>
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
          </template>
        </el-input>

        <el-button type="primary" size="large" class="search-button" @click="applySearch">
          搜索
        </el-button>
      </div>
    </section>

    <section class="admin-panel list-panel">
      <el-table v-loading="loading" :data="tags" style="width: 100%">
        <el-table-column label="标签" min-width="220">
          <template #default="{ row }">
            <span class="tag-chip">{{ row.name }}</span>
          </template>
        </el-table-column>

        <el-table-column label="文章数" width="140">
          <template #default="{ row }">
            <span class="tag-count">{{ row.articleCount }} 篇</span>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            <span class="time-text">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="170" align="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" :disabled="rowDisabled(row.id)" @click="openEdit(row)">
                编辑
              </el-button>

              <el-button
                link
                type="danger"
                :loading="busyId === row.id"
                :disabled="rowDisabled(row.id)"
                @click="removeTag(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="admin-state">
            <p>当前筛选下还没有标签，先建一个吧？</p>
            <el-button type="primary" @click="openCreate">新建标签</el-button>
          </div>
        </template>
      </el-table>

      <footer v-if="total > PAGE_SIZE" class="list-footer">
        <el-pagination
          :current-page="page"
          :page-size="PAGE_SIZE"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="changePage"
        />
      </footer>
    </section>

    <!-- 标签只有名称一个字段，用弹窗维护比单独开页面更顺手 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="420"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent
      >
        <el-form-item label="标签名称" prop="name">
          <el-input
            v-model="form.name"
            maxlength="50"
            placeholder="例如：Vue3、MySQL"
            show-word-limit
            @keyup.enter="submitForm"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="submitting" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">
          {{ editingId === null ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
}

.search-group {
  display: flex;
  flex: 1 1 320px;
  gap: 10px;
  min-width: 260px;
  max-width: 430px;
}

.keyword-input {
  flex: 1;
  min-width: 0;
}

.search-icon {
  width: 16px;
  height: 16px;
  color: var(--accent-brown);
}

.search-button {
  flex: 0 0 auto;
  padding: 0 22px;
}

.list-panel {
  padding: 6px 6px 12px;
}

.tag-chip {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 999px;
  color: #7a5436;
  font-size: 13px;
  background: #ecdec5;
}

.tag-count {
  color: var(--text-muted);
  font-size: 13px;
}

.time-text {
  color: var(--text-muted);
  font-size: 13px;
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}

.list-footer {
  padding-top: 16px;
}

/* 弹窗和后台面板保持同一套圆角/底色，不额外引入新风格 */
:deep(.el-dialog) {
  border-radius: 22px;
  background: var(--panel-bg);
}

:deep(.el-dialog__title) {
  color: var(--text-strong);
  font-weight: 600;
}
</style>
