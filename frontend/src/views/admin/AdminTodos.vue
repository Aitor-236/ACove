<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'

/** 状态码：和库里存的 todo / doing / done 一一对应 */
type TodoStatus = 'todo' | 'doing' | 'done'

/** 后台待办项（来自 GET /admin/todo/list），结构同 VO */
interface AdminTodoItem {
  id: number
  title: string
  description: string
  status: TodoStatus
  /** 1 表示置顶，全站最多一条为 1 */
  isPinned: number
  createdAt: string | null
  updatedAt: string | null
}

/** 三个状态的中文名：主页卡片和这里共用同一套叫法 */
const STATUS_OPTIONS: { value: TodoStatus; label: string }[] = [
  { value: 'doing', label: '打磨中' },
  { value: 'todo', label: '酝酿中' },
  { value: 'done', label: '已完成' }
]

const STATUS_LABELS: Record<TodoStatus, string> = {
  doing: '打磨中',
  todo: '酝酿中',
  done: '已完成'
}

/** 分类筛选用全部 + 三种状态，和文章管理页的筛选按钮同一套写法 */
type StatusFilter = 'all' | TodoStatus

const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'doing', label: '打磨中' },
  { value: 'todo', label: '酝酿中' },
  { value: 'done', label: '已完成' }
]

const todos = ref<AdminTodoItem[]>([])
const statusFilter = ref<StatusFilter>('all')
const loading = ref(false)
/** 正在操作的行，用来只禁用这一行的按钮 */
const busyId = ref<number | null>(null)

/** 筛选在本地做：清单本身不大，不用再往接口上加参数 */
const visibleTodos = computed(() =>
  statusFilter.value === 'all'
    ? todos.value
    : todos.value.filter((todo) => todo.status === statusFilter.value)
)

const dialogVisible = ref(false)
const submitting = ref(false)
/** null 表示新建，否则表示正在编辑这个待办 */
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = ref<{ title: string; description: string; status: TodoStatus }>({
  title: '',
  description: '',
  status: 'todo'
})

const dialogTitle = computed(() => (editingId.value === null ? '新建待办' : '编辑待办'))

const rules: FormRules<typeof form> = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 200, message: '标题不能超过 200 个字符', trigger: 'blur' }
  ]
}

function formatDateTime(value?: string | null) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

function statusLabel(status: TodoStatus) {
  return STATUS_LABELS[status] ?? status
}

function rowDisabled(id: number) {
  return busyId.value !== null && busyId.value !== id
}

async function loadTodos() {
  loading.value = true
  try {
    // 后端已按「置顶优先 + 创建时间新→旧」排好，这里直接展示
    const res = (await request.get('/admin/todo/list')) as { data: AdminTodoItem[] }
    todos.value = res.data ?? []
  } catch {
    // 错误提示由 request 拦截器统一处理
    todos.value = []
  } finally {
    loading.value = false
  }
}

/** 打开弹窗前先复位表单和上一次的校验提示 */
function openDialog(id: number | null, todo?: AdminTodoItem) {
  editingId.value = id
  form.value = todo
    ? { title: todo.title, description: todo.description ?? '', status: todo.status }
    : { title: '', description: '', status: 'todo' }
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function openCreate() {
  openDialog(null)
}

function openEdit(row: AdminTodoItem) {
  openDialog(row.id, row)
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      title: form.value.title.trim(),
      description: form.value.description,
      status: form.value.status
    }
    if (editingId.value === null) {
      await request.post('/admin/todo/create', payload)
      ElMessage.success('待办已创建')
    } else {
      await request.post('/admin/todo/update', { id: editingId.value, ...payload })
      ElMessage.success('待办已更新')
    }
    dialogVisible.value = false
    await loadTodos()
  } catch {
    // 业务失败（标题为空等）由 request 拦截器弹提示，这里保持弹窗打开方便修改
  } finally {
    submitting.value = false
  }
}

/** 置顶 / 取消置顶：后端保证全站同时只有一条置顶 */
async function togglePin(row: AdminTodoItem) {
  const pinned = row.isPinned === 1
  busyId.value = row.id
  try {
    await request.post(pinned ? '/admin/todo/unpin' : '/admin/todo/pin', null, {
      params: { id: row.id }
    })
    ElMessage.success(pinned ? '已取消置顶' : '已置顶')
    await loadTodos()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    busyId.value = null
  }
}

/** 表格里的状态下拉没有 v-model，改完统一回来刷新列表 */
function onStatusChange(row: AdminTodoItem, value: unknown) {
  void changeStatus(row, value as TodoStatus)
}

async function changeStatus(row: AdminTodoItem, status: TodoStatus) {
  if (status === row.status) return

  busyId.value = row.id
  try {
    await request.post('/admin/todo/status', null, { params: { id: row.id, status } })
    ElMessage.success(`已改为「${statusLabel(status)}」`)
    await loadTodos()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    busyId.value = null
  }
}

async function removeTodo(row: AdminTodoItem) {
  try {
    await ElMessageBox.confirm(`删除后无法恢复，确定删除「${row.title}」吗？`, '删除待办', {
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
    await request.post('/admin/todo/delete', null, { params: { id: row.id } })
    ElMessage.success('待办已删除')
    await loadTodos()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    busyId.value = null
  }
}

onMounted(loadTodos)
</script>

<template>
  <div class="admin-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">待办清单</h1>
      </div>

      <div class="admin-page-actions">
        <el-button :loading="loading" @click="loadTodos">刷新</el-button>
        <el-button type="primary" @click="openCreate">新建待办</el-button>
      </div>
    </header>

    <section class="admin-panel toolbar">
      <el-radio-group v-model="statusFilter" size="large">
        <el-radio-button
          v-for="option in STATUS_FILTERS"
          :key="option.value"
          :value="option.value"
        >
          {{ option.label }}
        </el-radio-button>
      </el-radio-group>
    </section>

    <section class="admin-panel list-panel">
      <el-table v-loading="loading" :data="visibleTodos" style="width: 100%">
        <el-table-column label="待办" min-width="280">
          <template #default="{ row }">
            <div class="todo-cell">
              <span class="todo-title">{{ row.title }}</span>
              <span v-if="row.isPinned === 1" class="pin-chip">置顶</span>
            </div>
            <p v-if="row.description" class="todo-desc">{{ row.description }}</p>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-select
              :model-value="row.status"
              size="small"
              popper-class="admin-select-popper"
              :disabled="rowDisabled(row.id)"
              @change="onStatusChange(row, $event)"
            >
              <el-option
                v-for="option in STATUS_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            <span class="time-text">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="230" align="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button
                link
                type="primary"
                :disabled="rowDisabled(row.id)"
                @click="togglePin(row)"
              >
                {{ row.isPinned === 1 ? '取消置顶' : '置顶' }}
              </el-button>

              <el-button link type="primary" :disabled="rowDisabled(row.id)" @click="openEdit(row)">
                编辑
              </el-button>

              <el-button
                link
                type="danger"
                :loading="busyId === row.id"
                :disabled="rowDisabled(row.id)"
                @click="removeTodo(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="admin-state">
            <template v-if="statusFilter === 'all'">
              <p>还没有待办，先建一条吧？</p>
              <el-button type="primary" @click="openCreate">新建待办</el-button>
            </template>
            <template v-else>
              <p>这个状态下暂时没有待办。</p>
              <el-button @click="statusFilter = 'all'">查看全部</el-button>
            </template>
          </div>
        </template>
      </el-table>
    </section>

    <!-- 标题 / 描述 / 状态三个字段，用弹窗维护比单独开页面更顺手 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="520"
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
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="form.title"
            maxlength="200"
            placeholder="例如：把 Todo 前端页面做完"
            show-word-limit
            @keyup.enter="submitForm"
          />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="可选，写点上下文或备注"
          />
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" popper-class="admin-select-popper" class="status-select">
            <el-option
              v-for="option in STATUS_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
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

.list-panel {
  padding: 6px 6px 12px;
}

.todo-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.todo-title {
  min-width: 0;
  overflow: hidden;
  color: var(--text-strong);
  font-size: 13.5px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 置顶标记和前台卡片用同一个色，全站一眼能认出来 */
.pin-chip {
  flex: 0 0 auto;
  padding: 2px 8px;
  border-radius: 999px;
  color: #7a5436;
  font-size: 11px;
  background: #ecdec5;
}

.todo-desc {
  margin: 4px 0 0;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12.5px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.status-select {
  width: 100%;
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
