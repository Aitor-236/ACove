<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'

/** 后台开源项目（来自 GET /admin/project/list），结构同 VO */
interface AdminProjectItem {
  id: number
  name: string
  /** 项目地址，空串表示还没填 */
  url: string
  description: string
  /** 日志：最新更新内容 */
  updateLog: string
  /** 下一步（todo） */
  nextStep: string
}

const projects = ref<AdminProjectItem[]>([])
const loading = ref(false)
/** 正在操作的行，用来只禁用这一行的按钮 */
const busyId = ref<number | null>(null)

const dialogVisible = ref(false)
const submitting = ref(false)
/** null 表示新建，否则表示正在编辑这个项目 */
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = ref({
  name: '',
  url: '',
  description: '',
  updateLog: '',
  nextStep: ''
})

const dialogTitle = computed(() => (editingId.value === null ? '新建开源项目' : '编辑开源项目'))

const rules: FormRules<typeof form> = {
  name: [
    { required: true, message: '请输入项目名字', trigger: 'blur' },
    { max: 200, message: '项目名字不能超过 200 个字符', trigger: 'blur' }
  ],
  url: [{ max: 500, message: '项目地址不能超过 500 个字符', trigger: 'blur' }]
}

function rowDisabled(id: number) {
  return busyId.value !== null && busyId.value !== id
}

/** 表格里地址太长时只展示主干，点击仍然打开完整链接 */
function displayUrl(url: string) {
  return url.replace(/^https?:\/\//i, '')
}

async function loadProjects() {
  loading.value = true
  try {
    // 后端已按「创建时间新→旧」排好，这里直接展示
    const res = (await request.get('/admin/project/list')) as { data: AdminProjectItem[] }
    projects.value = res.data ?? []
  } catch {
    // 错误提示由 request 拦截器统一处理
    projects.value = []
  } finally {
    loading.value = false
  }
}

/** 打开弹窗前先复位表单和上一次的校验提示 */
function openDialog(id: number | null, project?: AdminProjectItem) {
  editingId.value = id
  form.value = project
    ? {
        name: project.name,
        url: project.url ?? '',
        description: project.description ?? '',
        updateLog: project.updateLog ?? '',
        nextStep: project.nextStep ?? ''
      }
    : { name: '', url: '', description: '', updateLog: '', nextStep: '' }
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function openCreate() {
  openDialog(null)
}

function openEdit(row: AdminProjectItem) {
  openDialog(row.id, row)
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      name: form.value.name.trim(),
      url: form.value.url.trim(),
      description: form.value.description,
      updateLog: form.value.updateLog,
      nextStep: form.value.nextStep
    }
    if (editingId.value === null) {
      await request.post('/admin/project/create', payload)
      ElMessage.success('项目已创建')
    } else {
      await request.post('/admin/project/update', { id: editingId.value, ...payload })
      ElMessage.success('项目已更新')
    }
    dialogVisible.value = false
    await loadProjects()
  } catch {
    // 业务失败（名字为空等）由 request 拦截器弹提示，这里保持弹窗打开方便修改
  } finally {
    submitting.value = false
  }
}

async function removeProject(row: AdminProjectItem) {
  try {
    await ElMessageBox.confirm(`删除后无法恢复，确定删除「${row.name}」吗？`, '删除开源项目', {
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
    await request.post('/admin/project/delete', null, { params: { id: row.id } })
    ElMessage.success('项目已删除')
    await loadProjects()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    busyId.value = null
  }
}

onMounted(loadProjects)
</script>

<template>
  <div class="admin-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">开源项目</h1>
      </div>

      <div class="admin-page-actions">
        <el-button :loading="loading" @click="loadProjects">刷新</el-button>
        <el-button type="primary" @click="openCreate">新建项目</el-button>
      </div>
    </header>

    <section class="admin-panel list-panel">
      <el-table v-loading="loading" :data="projects" style="width: 100%">
        <el-table-column label="项目" min-width="220">
          <template #default="{ row }">
            <span class="project-name">{{ row.name }}</span>
            <p v-if="row.description" class="project-desc">{{ row.description }}</p>
          </template>
        </el-table-column>

        <el-table-column label="地址" min-width="200">
          <template #default="{ row }">
            <a
              v-if="row.url"
              class="project-link"
              :href="row.url"
              :title="row.url"
              target="_blank"
              rel="noopener"
            >
              {{ displayUrl(row.url) }}
            </a>
            <span v-else class="cell-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="日志" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.updateLog">{{ row.updateLog }}</span>
            <span v-else class="cell-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="下一步" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.nextStep">{{ row.nextStep }}</span>
            <span v-else class="cell-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" align="right">
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
                @click="removeProject(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="admin-state">
            <p>还没有开源项目，先建一个吧？</p>
            <el-button type="primary" @click="openCreate">新建项目</el-button>
          </div>
        </template>
      </el-table>
    </section>

    <!-- 名字 / 地址 / 介绍 / 日志 / 下一步，用弹窗维护就够了 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560"
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
        <el-form-item label="项目名字" prop="name">
          <el-input
            v-model="form.name"
            maxlength="200"
            placeholder="例如：ACove"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="项目地址" prop="url">
          <el-input
            v-model="form.url"
            maxlength="500"
            placeholder="https://github.com/xxx/yyy"
          />
        </el-form-item>

        <el-form-item label="项目介绍" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="这个项目是做什么的"
          />
        </el-form-item>

        <el-form-item label="日志（最新更新内容）" prop="updateLog">
          <el-input
            v-model="form.updateLog"
            type="textarea"
            :rows="2"
            placeholder="最近一次更新了什么"
          />
        </el-form-item>

        <el-form-item label="下一步（todo）" prop="nextStep">
          <el-input
            v-model="form.nextStep"
            type="textarea"
            :rows="2"
            placeholder="接下来打算做什么"
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
.list-panel {
  padding: 6px 6px 12px;
}

.project-name {
  color: var(--text-strong);
  font-size: 13.5px;
  font-weight: 500;
  /* 长名字 / 无空格串在单元格里换行，不要把表格撑破 */
  overflow-wrap: anywhere;
}

.project-desc {
  margin: 4px 0 0;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12.5px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-link {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  color: var(--accent-brown);
  font-size: 13px;
  text-overflow: ellipsis;
  vertical-align: bottom;
  white-space: nowrap;
}

.project-link:hover {
  text-decoration: underline;
}

.cell-muted {
  color: var(--text-muted);
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
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
