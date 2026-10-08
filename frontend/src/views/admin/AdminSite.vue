<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'

/** 站点设置（来自 GET /admin/site/settings） */
interface SiteSetting {
  siteName: string
  /** 头图相对地址，空字符串表示还没设置 */
  heroImage: string
  heroText: string
}

const IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/webp', 'image/gif']
/** 头图体积上限，和后端 multipart 的 5MB 保持一致 */
const IMAGE_MAX_SIZE = 5 * 1024 * 1024

const loading = ref(false)
/** 设置没读出来时不给空表单，直接提示重新加载，免得看着像"值没了" */
const loadError = ref(false)
const settings = ref<SiteSetting>({ siteName: '', heroImage: '', heroText: '' })

const siteNameFormRef = ref<FormInstance>()
const heroTextFormRef = ref<FormInstance>()
/** 每一项各管各的表单，进来先把当前值填进去，保存时也只提交这一项 */
const siteNameForm = ref({ siteName: '' })
const heroTextForm = ref({ heroText: '' })
const savingSiteName = ref(false)
const savingHeroText = ref(false)

const heroInputRef = ref<HTMLInputElement | null>(null)
/** 已经选好但还没上传的头图，以及它的本地预览地址 */
const pendingHeroFile = ref<File | null>(null)
const pendingHeroPreview = ref('')
const uploading = ref(false)

/** 已保存的头图加 /api 前缀访问 */
const savedHeroUrl = computed(() => (settings.value.heroImage ? `/api${settings.value.heroImage}` : ''))
/** 头图显示：选了新图先看新图，没选就显示当前已保存的那张 */
const heroPreviewUrl = computed(() => pendingHeroPreview.value || savedHeroUrl.value)

const siteNameRules: FormRules<typeof siteNameForm> = {
  siteName: [
    { required: true, message: '请输入网站名', trigger: 'blur' },
    { max: 50, message: '网站名不能超过 50 个字符', trigger: 'blur' }
  ]
}

const heroTextRules: FormRules<typeof heroTextForm> = {
  heroText: [{ max: 200, message: '首页文字不能超过 200 个字符', trigger: 'blur' }]
}

async function loadSettings() {
  loading.value = true
  loadError.value = false
  try {
    const res = (await request.get('/admin/site/settings')) as { data: SiteSetting }
    applySettings(res.data)
  } catch {
    // 错误提示由 request 拦截器统一处理，这里只把表单换成加载失败状态
    loadError.value = true
  } finally {
    loading.value = false
  }
}

/** 把接口返回的当前值填回各个表单，并让后台侧栏的网站名跟着刷新 */
function applySettings(data: SiteSetting) {
  settings.value = {
    siteName: data.siteName,
    heroImage: data.heroImage || '',
    heroText: data.heroText || ''
  }
  siteNameForm.value = { siteName: data.siteName }
  heroTextForm.value = { heroText: data.heroText || '' }
  localStorage.setItem('siteName', data.siteName)
  window.dispatchEvent(new Event('site-settings-updated'))
}

/** 只把当前这一项发给后端，其它设置保持不动 */
async function saveField(payload: Partial<SiteSetting>, message: string) {
  const res = (await request.post('/admin/site/settings/update', payload)) as { data: SiteSetting }
  applySettings(res.data)
  ElMessage.success(message)
}

async function saveSiteName() {
  const valid = await siteNameFormRef.value?.validate().catch(() => false)
  if (!valid) return

  savingSiteName.value = true
  try {
    await saveField({ siteName: siteNameForm.value.siteName.trim() }, '网站名已保存')
  } catch {
    // 业务失败由 request 拦截器弹出提示，保留输入方便修改
  } finally {
    savingSiteName.value = false
  }
}

async function saveHeroText() {
  const valid = await heroTextFormRef.value?.validate().catch(() => false)
  if (!valid) return

  savingHeroText.value = true
  try {
    await saveField({ heroText: heroTextForm.value.heroText.trim() }, '首页文字已保存')
  } catch {
    // 业务失败由 request 拦截器弹出提示，保留输入方便修改
  } finally {
    savingHeroText.value = false
  }
}

function pickHero() {
  heroInputRef.value?.click()
}

function onHeroChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  // 清空 value，同一个文件连选两次也能触发 change
  input.value = ''
  if (!file) return

  if (!IMAGE_TYPES.includes(file.type)) {
    ElMessage.warning('头图仅支持 png / jpg / webp / gif 图片')
    return
  }
  if (file.size > IMAGE_MAX_SIZE) {
    ElMessage.warning('头图不能超过 5MB，请先压缩一下再上传')
    return
  }

  clearPendingHero()
  pendingHeroFile.value = file
  pendingHeroPreview.value = URL.createObjectURL(file)
}

function clearPendingHero() {
  if (pendingHeroPreview.value) {
    URL.revokeObjectURL(pendingHeroPreview.value)
    pendingHeroPreview.value = ''
  }
  pendingHeroFile.value = null
}

async function uploadHero() {
  const file = pendingHeroFile.value
  if (!file) return

  const formData = new FormData()
  formData.append('file', file)

  uploading.value = true
  try {
    // 上传比普通请求慢，单独放宽超时（默认 5 秒）
    const res = (await request.post('/admin/site/hero-image', formData, { timeout: 20000 })) as {
      data: SiteSetting
    }
    clearPendingHero()
    applySettings(res.data)
    ElMessage.success('头图已更新')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    uploading.value = false
  }
}

async function clearHero() {
  if (!settings.value.heroImage) return

  try {
    await ElMessageBox.confirm('移除后首页会改用默认底色，确定移除当前头图吗？', '移除头图', {
      confirmButtonText: '移除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    // 用户点了取消
    return
  }

  try {
    await saveField({ heroImage: '' }, '头图已移除')
    clearPendingHero()
  } catch {
    // 错误提示由 request 拦截器统一处理
  }
}

onMounted(loadSettings)
onBeforeUnmount(clearPendingHero)
</script>

<template>
  <div class="admin-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">网站设置</h1>
      </div>

      <div class="admin-page-actions">
        <el-button :loading="loading" @click="loadSettings">刷新</el-button>
      </div>
    </header>

    <section v-if="loadError" class="admin-panel setting-card">
      <div class="admin-state">
        <p>设置加载失败</p>
        <el-button type="primary" :loading="loading" @click="loadSettings">重新加载</el-button>
      </div>
    </section>

    <template v-else>
      <section v-loading="loading" class="admin-panel setting-card">
        <div class="card-head">
          <h2 class="card-title">网站名</h2>
        </div>

        <el-form
          ref="siteNameFormRef"
          class="setting-form"
          :model="siteNameForm"
          :rules="siteNameRules"
          @submit.prevent
        >
          <el-form-item prop="siteName">
            <el-input
              v-model="siteNameForm.siteName"
              maxlength="50"
              placeholder="请输入网站名"
              show-word-limit
              @keyup.enter="saveSiteName"
            />
          </el-form-item>
        </el-form>

        <div class="card-footer">
          <el-button type="primary" :loading="savingSiteName" @click="saveSiteName">
            保存网站名
          </el-button>
        </div>
      </section>

      <section v-loading="loading" class="admin-panel setting-card">
        <div class="card-head">
          <h2 class="card-title">首页中间的文字</h2>
        </div>

        <el-form
          ref="heroTextFormRef"
          class="setting-form"
          :model="heroTextForm"
          :rules="heroTextRules"
          @submit.prevent
        >
          <el-form-item prop="heroText">
            <el-input
              v-model="heroTextForm.heroText"
              type="textarea"
              :rows="3"
              maxlength="200"
              placeholder="显示在首页头图正中间的那行文字，留空就用网站名"
              show-word-limit
            />
          </el-form-item>
        </el-form>

        <div class="card-footer">
          <el-button type="primary" :loading="savingHeroText" @click="saveHeroText">
            保存首页文字
          </el-button>
        </div>
      </section>

      <section v-loading="loading" class="admin-panel setting-card">
        <div class="card-head">
          <h2 class="card-title">首页头图</h2>
        </div>

        <div class="hero-row">
          <div class="hero-thumb">
            <img v-if="heroPreviewUrl" :src="heroPreviewUrl" alt="当前首页头图" />
            <span v-else>未设置头图</span>
          </div>

          <div class="hero-actions">
            <input
              ref="heroInputRef"
              class="file-input"
              type="file"
              accept="image/png,image/jpeg,image/webp,image/gif"
              @change="onHeroChange"
            />

            <div class="hero-buttons">
              <el-button @click="pickHero">选择图片</el-button>
              <el-button
                type="primary"
                :loading="uploading"
                :disabled="!pendingHeroFile"
                @click="uploadHero"
              >
                保存头图
              </el-button>
              <el-button v-if="pendingHeroFile" link @click="clearPendingHero">取消选择</el-button>
              <el-button v-else-if="settings.heroImage" link @click="clearHero">移除头图</el-button>
            </div>

            <p v-if="pendingHeroFile" class="card-tip">
              已选择「{{ pendingHeroFile.name }}」，点「保存头图」提交
            </p>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.setting-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 22px 24px;
}

.card-head {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.card-title {
  margin: 0;
  color: var(--text-strong);
  font-size: 16px;
  font-weight: 600;
}

.setting-form {
  max-width: 520px;
}

.setting-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  max-width: 520px;
}

.hero-row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 22px;
}

/* 当前头图：进来先看到已经保存的那张，选了新图则先显示新图 */
.hero-thumb {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 320px;
  height: 180px;
  overflow: hidden;
  border: 1px solid var(--panel-border);
  border-radius: 16px;
  color: var(--text-muted);
  font-size: 13px;
  background: var(--panel-alt-bg);
}

.hero-thumb img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}

.hero-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.file-input {
  display: none;
}

.card-tip {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
}
</style>
