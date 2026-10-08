<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import request from '@/utils/request'

/** 个人资料（来自 GET /admin/user/profile） */
interface ProfileInfo {
  id: number
  username: string
  email: string
  /** 头像相对地址，空字符串表示还没设置过头像 */
  avatar: string
}

const AVATAR_TYPES = ['image/png', 'image/jpeg', 'image/webp', 'image/gif']

/** 原图体积上限：再大就不在浏览器里解码了，避免页面卡死（后端 multipart 上限是 5MB，裁剪后的图远小于它） */
const SOURCE_MAX_SIZE = 20 * 1024 * 1024
/** 原图超过这个体积或边长就先进裁剪步骤，小图直接上传原文件 */
const CROP_TRIGGER_SIZE = 1024 * 1024
const CROP_TRIGGER_EDGE = 1024

/** 裁剪取景框的边长（正方形，界面上显示成圆形）和导出图边长 */
const CROP_VIEW = 280
const CROP_OUTPUT_SIZE = 512
const CROP_MAX_SCALE = 4

const loading = ref(false)
const profile = ref<ProfileInfo | null>(null)

const usernameFormRef = ref<FormInstance>()
const emailFormRef = ref<FormInstance>()
const usernameForm = ref({ username: '' })
const emailForm = ref({ email: '' })
const savingUsername = ref(false)
const savingEmail = ref(false)

const fileInputRef = ref<HTMLInputElement | null>(null)
/** 已经选好但还没上传的文件，以及它的本地预览地址 */
const pendingFile = ref<File | null>(null)
const pendingPreview = ref('')
/** 待上传的图是不是裁剪出来的，用来换一句更准确的提示 */
const pendingFromCrop = ref(false)
const uploading = ref(false)

/** 最近一次选择的原始文件，用于"重新裁剪" */
const sourceFile = ref<File | null>(null)
/** 裁剪弹窗状态 */
const cropVisible = ref(false)
const cropImageRef = ref<HTMLImageElement | null>(null)
const cropImageUrl = ref('')
const cropNatural = ref({ width: 0, height: 0 })
/** 用户缩放倍数，1 表示"刚铺满取景框"，最大 CROP_MAX_SCALE */
const cropScale = ref(1)
/** 图片中心相对取景框中心的偏移（CSS 像素），拖动时改它 */
const cropOffset = ref({ x: 0, y: 0 })
const cropRendering = ref(false)

/** 1 倍时把图片等比放大到刚好盖住取景框的比例 */
const cropBaseScale = computed(() => {
  const { width, height } = cropNatural.value
  if (!width || !height) return 1
  return Math.max(CROP_VIEW / width, CROP_VIEW / height)
})

/** 图片渲染到取景框里的尺寸和位置 */
const cropImageStyle = computed(() => {
  const scale = cropBaseScale.value * cropScale.value
  const width = cropNatural.value.width * scale
  const height = cropNatural.value.height * scale
  return {
    width: `${width}px`,
    height: `${height}px`,
    left: `${(CROP_VIEW - width) / 2 + cropOffset.value.x}px`,
    top: `${(CROP_VIEW - height) / 2 + cropOffset.value.y}px`
  }
})

const usernameInitial = computed(
  () => (profile.value?.username ?? '').trim().charAt(0).toUpperCase() || 'A'
)
const savedAvatarUrl = computed(() => (profile.value?.avatar ? `/api${profile.value.avatar}` : ''))
/** 预览优先用刚选的文件，其次是已保存的头像 */
const avatarPreviewUrl = computed(() => pendingPreview.value || savedAvatarUrl.value)
/** 头像卡片下面那行提示，跟着当前状态走 */
const avatarTip = computed(() => {
  if (!pendingFile.value) {
    return ''
  }
  if (pendingFromCrop.value) {
    return '已裁剪好方图，点「上传头像」保存'
  }
  return `已选择「${pendingFile.value.name}」，点「上传头像」保存`
})

const usernameRules: FormRules<typeof usernameForm> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 50, message: '用户名不能超过 50 个字符', trigger: 'blur' }
  ]
}

const emailRules: FormRules<typeof emailForm> = {
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
    { max: 100, message: '邮箱不能超过 100 个字符', trigger: 'blur' }
  ]
}

async function loadProfile() {
  loading.value = true
  try {
    const res = (await request.get('/admin/user/profile')) as { data: ProfileInfo }
    applyProfile(res.data)
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 保存成功后同步表单、localStorage 和侧栏账号区块 */
function applyProfile(data: ProfileInfo) {
  profile.value = data
  usernameForm.value = { username: data.username }
  emailForm.value = { email: data.email }
  localStorage.setItem('username', data.username)
  if (data.email) {
    localStorage.setItem('email', data.email)
  }
  window.dispatchEvent(new Event('profile-updated'))
}

async function saveUsername() {
  const valid = await usernameFormRef.value?.validate().catch(() => false)
  if (!valid) return

  const username = usernameForm.value.username.trim()
  if (username === profile.value?.username) {
    ElMessage.info('用户名没有变化')
    return
  }

  savingUsername.value = true
  try {
    const res = (await request.post('/admin/user/profile/update', { username })) as {
      data: ProfileInfo
    }
    applyProfile(res.data)
    ElMessage.success('用户名已更新')
  } catch {
    // 重名等业务失败由 request 拦截器弹出提示，保留输入方便修改
  } finally {
    savingUsername.value = false
  }
}

async function saveEmail() {
  const valid = await emailFormRef.value?.validate().catch(() => false)
  if (!valid) return

  const email = emailForm.value.email.trim()
  if (email === profile.value?.email) {
    ElMessage.info('邮箱地址没有变化')
    return
  }

  savingEmail.value = true
  try {
    const res = (await request.post('/admin/user/profile/update', { email })) as {
      data: ProfileInfo
    }
    applyProfile(res.data)
    ElMessage.success('邮箱地址已更新')
  } catch {
    // 格式或重复问题由 request 拦截器弹出提示，保留输入方便修改
  } finally {
    savingEmail.value = false
  }
}

function pickFile() {
  fileInputRef.value?.click()
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  // 清空 value，同一个文件连选两次也能触发 change
  input.value = ''
  if (!file) return

  if (!AVATAR_TYPES.includes(file.type)) {
    ElMessage.warning('头像仅支持 png / jpg / webp / gif 图片')
    return
  }
  if (file.size > SOURCE_MAX_SIZE) {
    ElMessage.warning('原图不能超过 20MB，请先压缩一下再上传')
    return
  }

  void prepareFile(file)
}

/** 判断要不要先裁剪：体积大或边长超过阈值都先进裁剪弹窗 */
async function prepareFile(file: File) {
  const size = await measureImage(file)
  const tooLarge =
    file.size > CROP_TRIGGER_SIZE ||
    size.width > CROP_TRIGGER_EDGE ||
    size.height > CROP_TRIGGER_EDGE

  if (tooLarge) {
    await openCrop(file)
    return
  }

  applyPending(file, false)
}

/** 打开裁剪弹窗：先把图读成 object URL，交给取景框和 canvas 用 */
async function openCrop(file: File) {
  const url = URL.createObjectURL(file)
  try {
    const image = await loadImage(url)
    releaseCropUrl()
    sourceFile.value = file
    cropImageUrl.value = url
    cropNatural.value = { width: image.naturalWidth, height: image.naturalHeight }
    resetCrop()
    cropVisible.value = true
  } catch {
    URL.revokeObjectURL(url)
    ElMessage.error('图片读取失败，换一张试试')
  }
}

/** 读原图尺寸，读完立刻释放临时 URL */
async function measureImage(file: File) {
  const url = URL.createObjectURL(file)
  try {
    const image = await loadImage(url)
    return { width: image.naturalWidth, height: image.naturalHeight }
  } catch {
    return { width: 0, height: 0 }
  } finally {
    URL.revokeObjectURL(url)
  }
}

function loadImage(url: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('图片读取失败'))
    image.src = url
  })
}

function applyPending(file: File, fromCrop: boolean) {
  revokePreview()
  sourceFile.value = file
  pendingFile.value = file
  pendingFromCrop.value = fromCrop
  pendingPreview.value = URL.createObjectURL(file)
}

function revokePreview() {
  if (pendingPreview.value) {
    URL.revokeObjectURL(pendingPreview.value)
    pendingPreview.value = ''
  }
  pendingFromCrop.value = false
}

function releaseCropUrl() {
  if (cropImageUrl.value) {
    URL.revokeObjectURL(cropImageUrl.value)
    cropImageUrl.value = ''
  }
}

function clearPending() {
  revokePreview()
  pendingFile.value = null
}

function resetCrop() {
  cropScale.value = 1
  cropOffset.value = { x: 0, y: 0 }
}

/** 把偏移限制在"图片始终盖住取景框"的范围内，拖不出空白边 */
function clampOffset() {
  const scale = cropBaseScale.value * cropScale.value
  const width = cropNatural.value.width * scale
  const height = cropNatural.value.height * scale
  const maxX = Math.max(0, (width - CROP_VIEW) / 2)
  const maxY = Math.max(0, (height - CROP_VIEW) / 2)
  cropOffset.value = {
    x: Math.min(maxX, Math.max(-maxX, cropOffset.value.x)),
    y: Math.min(maxY, Math.max(-maxY, cropOffset.value.y))
  }
}

/** 缩放时让取景框中心对应的那块图保持不动，放大后才不会跑偏 */
function handleZoom(value: number | number[]) {
  const next = Array.isArray(value) ? value[0] : value
  const prev = cropScale.value
  if (typeof next !== 'number' || !Number.isFinite(next)) return
  if (next === prev || !cropNatural.value.width) return

  const prevScale = cropBaseScale.value * prev
  const prevWidth = cropNatural.value.width * prevScale
  const prevHeight = cropNatural.value.height * prevScale
  const centerX = (CROP_VIEW / 2 - ((CROP_VIEW - prevWidth) / 2 + cropOffset.value.x)) / prevScale
  const centerY = (CROP_VIEW / 2 - ((CROP_VIEW - prevHeight) / 2 + cropOffset.value.y)) / prevScale

  cropScale.value = next
  const scale = cropBaseScale.value * next
  const width = cropNatural.value.width * scale
  const height = cropNatural.value.height * scale
  cropOffset.value = {
    x: CROP_VIEW / 2 - centerX * scale - (CROP_VIEW - width) / 2,
    y: CROP_VIEW / 2 - centerY * scale - (CROP_VIEW - height) / 2
  }
  clampOffset()
}

let dragging = false
let dragStart = { x: 0, y: 0, offsetX: 0, offsetY: 0 }

function onPointerDown(event: PointerEvent) {
  if (!cropImageUrl.value) return
  dragging = true
  dragStart = {
    x: event.clientX,
    y: event.clientY,
    offsetX: cropOffset.value.x,
    offsetY: cropOffset.value.y
  }
  ;(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
  event.preventDefault()
}

function onPointerMove(event: PointerEvent) {
  if (!dragging) return
  cropOffset.value = {
    x: dragStart.offsetX + (event.clientX - dragStart.x),
    y: dragStart.offsetY + (event.clientY - dragStart.y)
  }
  clampOffset()
}

function onPointerUp(event: PointerEvent) {
  if (!dragging) return
  dragging = false
  ;(event.currentTarget as HTMLElement).releasePointerCapture?.(event.pointerId)
}

function onWheelZoom(event: WheelEvent) {
  handleZoom(Math.min(CROP_MAX_SCALE, Math.max(1, cropScale.value - event.deltaY * 0.002)))
}

/** 把取景框里看到的那块画到 canvas 上，导出一张方图作为待上传文件 */
async function confirmCrop() {
  const image = cropImageRef.value
  const { width: naturalWidth, height: naturalHeight } = cropNatural.value
  if (!image || !naturalWidth || !naturalHeight) return

  cropRendering.value = true
  try {
    const scale = cropBaseScale.value * cropScale.value
    const left = (CROP_VIEW - naturalWidth * scale) / 2 + cropOffset.value.x
    const top = (CROP_VIEW - naturalHeight * scale) / 2 + cropOffset.value.y
    const sourceSize = CROP_VIEW / scale
    const sourceX = Math.min(Math.max(-left / scale, 0), Math.max(0, naturalWidth - sourceSize))
    const sourceY = Math.min(Math.max(-top / scale, 0), Math.max(0, naturalHeight - sourceSize))

    // 小图不放大，避免导出一张糊的图
    const outputSize = Math.max(64, Math.min(CROP_OUTPUT_SIZE, Math.round(sourceSize)))
    const canvas = document.createElement('canvas')
    canvas.width = outputSize
    canvas.height = outputSize
    const context = canvas.getContext('2d')
    if (!context) throw new Error('当前浏览器不支持裁剪')

    // 透明图（png / webp）落在白底上，不会变成黑块
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, outputSize, outputSize)
    context.imageSmoothingQuality = 'high'
    context.drawImage(image, sourceX, sourceY, sourceSize, sourceSize, 0, 0, outputSize, outputSize)

    const blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, 'image/jpeg', 0.92)
    )
    if (!blob) throw new Error('裁剪导出失败')

    applyPending(new File([blob], 'avatar-cropped.jpg', { type: 'image/jpeg' }), true)
    cropVisible.value = false
    ElMessage.success('已裁剪，点「上传头像」保存')
  } catch {
    ElMessage.error('裁剪失败，换一张图片试试')
  } finally {
    cropRendering.value = false
  }
}

async function uploadAvatar() {
  const file = pendingFile.value
  if (!file) return

  const formData = new FormData()
  formData.append('file', file)

  uploading.value = true
  try {
    // 上传比普通请求慢，单独放宽超时（默认 5 秒）
    const res = (await request.post('/admin/user/avatar', formData, { timeout: 20000 })) as {
      data: ProfileInfo
    }
    clearPending()
    applyProfile(res.data)
    ElMessage.success('头像已更新')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    uploading.value = false
  }
}

onMounted(loadProfile)
onBeforeUnmount(() => {
  revokePreview()
  releaseCropUrl()
})
</script>

<template>
  <div class="admin-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">个人管理</h1>
      </div>

      <div class="admin-page-actions">
        <el-button :loading="loading" @click="loadProfile">刷新</el-button>
      </div>
    </header>

    <section v-loading="loading" class="admin-panel profile-card">
      <div class="card-head">
        <h2 class="card-title">头像</h2>
      </div>

      <div class="avatar-row">
        <div class="avatar-preview">
          <img v-if="avatarPreviewUrl" :src="avatarPreviewUrl" alt="当前头像" />
          <span v-else>{{ usernameInitial }}</span>
        </div>

        <div class="avatar-actions">
          <input
            ref="fileInputRef"
            class="file-input"
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            @change="onFileChange"
          />

          <div class="avatar-buttons">
            <el-button @click="pickFile">选择图片</el-button>
            <el-button
              type="primary"
              :loading="uploading"
              :disabled="!pendingFile"
              @click="uploadAvatar"
            >
              上传头像
            </el-button>
            <el-button v-if="sourceFile" link @click="openCrop(sourceFile)">重新裁剪</el-button>
            <el-button v-if="pendingFile" link @click="clearPending">取消选择</el-button>
          </div>

          <p class="card-tip">{{ avatarTip }}</p>
        </div>
      </div>
    </section>

    <section v-loading="loading" class="admin-panel profile-card">
      <div class="card-head">
        <h2 class="card-title">用户名</h2>
      </div>

      <el-form
        ref="usernameFormRef"
        class="profile-form"
        :model="usernameForm"
        :rules="usernameRules"
        label-position="top"
        @submit.prevent
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="usernameForm.username"
            maxlength="50"
            placeholder="请输入新的用户名"
            show-word-limit
            @keyup.enter="saveUsername"
          />
        </el-form-item>
      </el-form>

      <div class="card-footer">
        <el-button type="primary" :loading="savingUsername" @click="saveUsername">
          保存用户名
        </el-button>
      </div>
    </section>

    <section v-loading="loading" class="admin-panel profile-card">
      <div class="card-head">
        <h2 class="card-title">邮箱地址</h2>
      </div>

      <el-form
        ref="emailFormRef"
        class="profile-form"
        :model="emailForm"
        :rules="emailRules"
        label-position="top"
        @submit.prevent
      >
        <el-form-item label="邮箱地址" prop="email">
          <el-input
            v-model="emailForm.email"
            maxlength="100"
            placeholder="请输入新的邮箱地址"
            show-word-limit
            @keyup.enter="saveEmail"
          />
        </el-form-item>
      </el-form>

      <div class="card-footer">
        <el-button type="primary" :loading="savingEmail" @click="saveEmail">
          保存邮箱地址
        </el-button>
      </div>
    </section>

    <!-- 原图偏大时先在这里裁成方图，再走上传流程 -->
    <el-dialog
      v-model="cropVisible"
      title="裁剪头像"
      width="360"
      :close-on-click-modal="false"
      @closed="releaseCropUrl"
    >
      <div
        class="crop-stage"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerUp"
        @wheel.prevent="onWheelZoom"
      >
        <img
          ref="cropImageRef"
          class="crop-image"
          :src="cropImageUrl"
          :style="cropImageStyle"
          alt="待裁剪的图片"
          draggable="false"
        />
      </div>

      <div class="crop-controls">
        <p class="crop-hint">拖动图片调整位置，滑块或滚轮缩放，圆圈里就是最终头像</p>
        <el-slider
          :model-value="cropScale"
          :min="1"
          :max="CROP_MAX_SCALE"
          :step="0.01"
          :show-tooltip="false"
          @input="handleZoom"
        />
      </div>

      <template #footer>
        <el-button :disabled="cropRendering" @click="resetCrop">重置</el-button>
        <el-button :disabled="cropRendering" @click="cropVisible = false">取消</el-button>
        <el-button type="primary" :loading="cropRendering" @click="confirmCrop">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile-card {
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

.avatar-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 22px;
}

.avatar-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 96px;
  height: 96px;
  overflow: hidden;
  border: 1px solid var(--panel-border);
  border-radius: 50%;
  color: #fdf9f2;
  font-size: 34px;
  font-weight: 600;
  background: linear-gradient(135deg, #cfa986, #8a5a3b);
}

.avatar-preview img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}

.avatar-buttons {
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

.profile-form {
  max-width: 460px;
}

.profile-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  max-width: 460px;
}

/* ---------- 裁剪头像 ---------- */

.crop-stage {
  position: relative;
  width: 280px;
  height: 280px;
  margin: 0 auto;
  overflow: hidden;
  border-radius: 50%;
  background: var(--panel-alt-bg);
  box-shadow:
    0 0 0 1px rgba(138, 90, 59, 0.18) inset,
    0 0 0 8px rgba(138, 90, 59, 0.06);
  cursor: grab;
  touch-action: none;
}

.crop-stage:active {
  cursor: grabbing;
}

.crop-image {
  position: absolute;
  max-width: none;
  user-select: none;
  -webkit-user-drag: none;
}

.crop-controls {
  margin-top: 18px;
  padding: 0 6px;
}

.crop-hint {
  margin: 0 0 6px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
}

:deep(.el-dialog) {
  border-radius: 22px;
  background: var(--panel-bg);
}

:deep(.el-dialog__title) {
  color: var(--text-strong);
  font-weight: 600;
}
</style>
