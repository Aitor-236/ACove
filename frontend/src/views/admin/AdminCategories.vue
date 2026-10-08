<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'

/** 后台分类项（来自 GET /admin/category/list） */
interface AdminCategoryItem {
  id: number
  name: string
  slug: string
  /** 该分类下的文章数，草稿和已发布都算 */
  articleCount: number
}

const categories = ref<AdminCategoryItem[]>([])
const loading = ref(false)
const router = useRouter()
/** 正在删除的分类ID，用来只禁用这一行的按钮 */
const busyId = ref<number | null>(null)

const dialogVisible = ref(false)
const submitting = ref(false)
/** null 表示新建，否则表示正在编辑这个分类 */
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = ref({ categoryName: '', categoryIdentifier: '' })

const dialogTitle = computed(() => (editingId.value === null ? '新建分类' : '编辑分类'))

const rules: FormRules<typeof form> = {
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

function rowDisabled(id: number) {
  return busyId.value !== null && busyId.value !== id
}

async function loadCategories() {
  loading.value = true
  try {
    const res = (await request.get('/admin/category/list')) as { data: AdminCategoryItem[] }
    categories.value = res.data
  } catch {
    // 错误提示由 request 拦截器统一处理
    categories.value = []
  } finally {
    loading.value = false
  }
}

/** 打开弹窗前先复位表单和上一次的校验提示 */
function openDialog(id: number | null, name: string, slug: string) {
  editingId.value = id
  form.value = { categoryName: name, categoryIdentifier: slug }
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function openCreate() {
  openDialog(null, '', '')
}

function openEdit(row: AdminCategoryItem) {
  openDialog(row.id, row.name, row.slug)
}

/** 点分类直接去后台文章列表看这个分类的文章，在那边可以正常编辑 / 发布。 */
function goCategoryArticles(row: AdminCategoryItem) {
  void router.push({ path: '/admin/articles', query: { category: row.slug } })
}

/**
 * 整行都可以点进该分类的文章：判定箱不再只有分类名那一小块。
 * 操作列里的「编辑 / 删除」自己处理点击，这里直接跳过。
 */
function onRowClick(row: AdminCategoryItem, _column: unknown, event: Event) {
  const target = event.target as HTMLElement | null
  if (target?.closest('button, a')) return
  goCategoryArticles(row)
}

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      categoryName: form.value.categoryName.trim(),
      categoryIdentifier: form.value.categoryIdentifier.trim()
    }

    if (editingId.value === null) {
      await request.post('/admin/category/create', payload)
      ElMessage.success('分类已创建')
    } else {
      await request.post('/admin/category/update', { id: editingId.value, ...payload })
      ElMessage.success('分类已更新')
    }
    dialogVisible.value = false
    await loadCategories()
  } catch {
    // 标识重复等业务失败由 request 拦截器弹出提示，这里保持弹窗打开方便修改
  } finally {
    submitting.value = false
  }
}

async function removeCategory(row: AdminCategoryItem) {
  // 分类下还有文章时后端会拒绝删除（外键 RESTRICT），这里用同一个口径先挡一下
  if (row.articleCount > 0) {
    ElMessage.warning(`「${row.name}」下还有 ${row.articleCount} 篇文章，请先把它们改到其它分类`)
    return
  }

  try {
    await ElMessageBox.confirm(`删除后无法恢复，确定删除分类「${row.name}」吗？`, '删除分类', {
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
    await request.post('/admin/category/delete', null, { params: { id: row.id } })
    ElMessage.success('分类已删除')
    await loadCategories()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    busyId.value = null
  }
}

onMounted(loadCategories)
</script>

<template>
  <div class="admin-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">分类管理</h1>
      </div>

      <div class="admin-page-actions">
        <el-button :loading="loading" @click="loadCategories">刷新</el-button>
        <el-button type="primary" @click="openCreate">新建分类</el-button>
      </div>
    </header>

    <section class="admin-panel list-panel">
      <el-table
        v-loading="loading"
        :data="categories"
        style="width: 100%"
        @row-click="onRowClick"
      >
        <el-table-column label="分类" min-width="200">
          <template #default="{ row }">
            <button
              type="button"
              class="category-chip"
              title="查看这个分类下的文章"
              @click="goCategoryArticles(row)"
            >
              {{ row.name }}
            </button>
          </template>
        </el-table-column>

        <el-table-column label="英文标识" min-width="180">
          <template #default="{ row }">
            <code class="category-slug">{{ row.slug }}</code>
          </template>
        </el-table-column>

        <el-table-column label="文章数" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="goCategoryArticles(row)">
              {{ row.articleCount }} 篇
            </el-button>
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
                @click="removeCategory(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="admin-state">
            <p>还没有分类，先建一个吧？</p>
            <el-button type="primary" @click="openCreate">新建分类</el-button>
          </div>
        </template>
      </el-table>
    </section>

    <!-- 分类只有名称和英文标识两个字段，用弹窗维护比单独开页面更顺手 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="440"
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
        <el-form-item label="分类名称" prop="categoryName">
          <el-input
            v-model="form.categoryName"
            maxlength="50"
            placeholder="例如：前端"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="英文标识" prop="categoryIdentifier">
          <el-input
            v-model="form.categoryIdentifier"
            maxlength="50"
            placeholder="例如：frontend"
            show-word-limit
            @keyup.enter="submitForm"
          />
          <p class="form-tip">用于前台地址栏筛选，改名不影响已发布文章</p>
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

.category-chip {
  display: inline-block;
  padding: 4px 12px;
  border: none;
  border-radius: 999px;
  color: #7a5436;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  background: #ecdec5;
}

/* 整行是跳转入口，鼠标移到行上给出手型光标 */
:deep(.el-table__row) {
  cursor: pointer;
}

.category-slug {
  padding: 2px 8px;
  border-radius: 8px;
  color: var(--text-body);
  font-size: 12px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  background: var(--panel-alt-bg);
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}

.form-tip {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
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
