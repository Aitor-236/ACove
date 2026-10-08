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

/** 原图体积上限：再大就不在浏览器里解码了，避免页面卡死（后端 multipart 上限 5MB，裁剪后的图远小于它） */
const SOURCE_MAX_SIZE = 20 * 1024 * 1024
/** 裁剪取景框（16:9 横图，和首页头图与后台缩略图一致）与导出图宽度 */
const CROP_VIEW_WIDTH = 320
const CROP_VIEW_HEIGHT = 180
const CROP_OUTPUT_WIDTH = 1600
const CROP_MAX_SCALE = 4

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
/** 已经裁好但还没上传的头图，以及它的本地预览地址 */
const pendingHeroFile = ref<File | null>(null)
const pendingHeroPreview = ref('')
const uploading = ref(false)

/** 最近一次选择的原图，用于"重新裁剪" */
const sourceHeroFile = ref<File | null>(null)
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

/** 已保存的头图加 /api 前缀访问 */
const savedHeroUrl = computed(() => (settings.value.heroImage ? `/api${settings.value.heroImage}` : ''))
/** 头图显示：选了新图先看新图，没选就显示当前已保存的那张 */
const heroPreviewUrl = computed(() => pendingHeroPreview.value || savedHeroUrl.value)

/** 1 倍时把图片等比放大到刚好盖住取景框的比例 */
const cropBaseScale = computed(() => {
  const { width, height } = cropNatural.value
  if (!width || !height) return 1
  return Math.max(CROP_VIEW_WIDTH / width, CROP_VIEW_HEIGHT / height)
})

/** 图片渲染到取景框里的尺寸和位置 */
const cropImageStyle = computed(() => {
  const scale = cropBaseScale.value * cropScale.value
  const width = cropNatural.value.width * scale
  const height = cropNatural.value.height * scale
  return {
    width: `${width}px`,
    height: `${height}px`,
    left: `${(CROP_VIEW_WIDTH - width) / 2 + cropOffset.value.x}px`,
    top: `${(CROP_VIEW_HEIGHT - height) / 2 + cropOffset.value.y}px`
  }
})

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

async function onHeroChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  // 清空 value，同一个文件连选两次也能触发 change
  input.value = ''
  if (!file) return

  if (!IMAGE_TYPES.includes(file.type)) {
    ElMessage.warning('头图仅支持 png / jpg / webp / gif 图片')
    return
  }
  if (file.size > SOURCE_MAX_SIZE) {
    ElMessage.warning('原图不能超过 20MB，请先压缩一下再上传')
    return
  }

  // 头图是 16:9 的横图，选好图先进裁剪框选一下再上传
  await openHeroCrop(file)
}

function clearPendingHero() {
  if (pendingHeroPreview.value) {
    URL.revokeObjectURL(pendingHeroPreview.value)
    pendingHeroPreview.value = ''
  }
  pendingHeroFile.value = null
}

/* ---------- 裁剪头图 ---------- */

/** 读原图尺寸并打开裁剪框选，选好后原始文件留着给"重新裁剪"用 */
async function openHeroCrop(file: File) {
  const url = URL.createObjectURL(file)
  try {
    const image = await loadImage(url)
    releaseCropUrl()
    sourceHeroFile.value = file
    cropImageUrl.value = url
    cropNatural.value = { width: image.naturalWidth, height: image.naturalHeight }
    resetCrop()
    cropVisible.value = true
  } catch {
    URL.revokeObjectURL(url)
    ElMessage.error('图片读取失败，换一张试试')
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

function releaseCropUrl() {
  if (cropImageUrl.value) {
    URL.revokeObjectURL(cropImageUrl.value)
    cropImageUrl.value = ''
  }
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
  const maxX = Math.max(0, (width - CROP_VIEW_WIDTH) / 2)
  const maxY = Math.max(0, (height - CROP_VIEW_HEIGHT) / 2)
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
  const centerX =
    (CROP_VIEW_WIDTH / 2 - ((CROP_VIEW_WIDTH - prevWidth) / 2 + cropOffset.value.x)) / prevScale
  const centerY =
    (CROP_VIEW_HEIGHT / 2 - ((CROP_VIEW_HEIGHT - prevHeight) / 2 + cropOffset.value.y)) / prevScale

  cropScale.value = next
  const scale = cropBaseScale.value * next
  const width = cropNatural.value.width * scale
  const height = cropNatural.value.height * scale
  cropOffset.value = {
    x: CROP_VIEW_WIDTH / 2 - centerX * scale - (CROP_VIEW_WIDTH - width) / 2,
    y: CROP_VIEW_HEIGHT / 2 - centerY * scale - (CROP_VIEW_HEIGHT - height) / 2
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

/** 把取景框里看到的那块画到 canvas 上，导出一张 16:9 横图作为待上传文件 */
async function confirmCrop() {
  const image = cropImageRef.value
  const { width: naturalWidth, height: naturalHeight } = cropNatural.value
  if (!image || !naturalWidth || !naturalHeight) return

  cropRendering.value = true
  try {
    const scale = cropBaseScale.value * cropScale.value
    const left = (CROP_VIEW_WIDTH - naturalWidth * scale) / 2 + cropOffset.value.x
    const top = (CROP_VIEW_HEIGHT - naturalHeight * scale) / 2 + cropOffset.value.y
    const sourceWidth = CROP_VIEW_WIDTH / scale
    const sourceHeight = CROP_VIEW_HEIGHT / scale
    const sourceX = Math.min(Math.max(-left / scale, 0), Math.max(0, naturalWidth - sourceWidth))
    const sourceY = Math.min(
      Math.max(-top / scale, 0),
      Math.max(0, naturalHeight - sourceHeight)
    )

    // 小图不放大，避免导出一张糊的图
    const outputWidth = Math.max(320, Math.min(CROP_OUTPUT_WIDTH, Math.round(sourceWidth)))
    const outputHeight = Math.round((outputWidth * CROP_VIEW_HEIGHT) / CROP_VIEW_WIDTH)
    const canvas = document.createElement('canvas')
    canvas.width = outputWidth
    canvas.height = outputHeight
    const context = canvas.getContext('2d')
    if (!context) throw new Error('当前浏览器不支持裁剪')

    // 透明图（png / webp）落在白底上，不会变成黑块
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, outputWidth, outputHeight)
    context.imageSmoothingQuality = 'high'
    context.drawImage(
      image,
      sourceX,
      sourceY,
      sourceWidth,
      sourceHeight,
      0,
      0,
      outputWidth,
      outputHeight
    )

    const blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, 'image/jpeg', 0.92)
    )
    if (!blob) throw new Error('裁剪导出失败')

    applyPendingHero(new File([blob], 'hero-cropped.jpg', { type: 'image/jpeg' }))
    cropVisible.value = false
    ElMessage.success('已裁剪，点「保存头图」提交')
  } catch {
    ElMessage.error('裁剪失败，换一张图片试试')
  } finally {
    cropRendering.value = false
  }
}

function applyPendingHero(file: File) {
  clearPendingHero()
  pendingHeroFile.value = file
  pendingHeroPreview.value = URL.createObjectURL(file)
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
onBeforeUnmount(() => {
  clearPendingHero()
  releaseCropUrl()
})
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
              <el-button v-if="sourceHeroFile" link @click="openHeroCrop(sourceHeroFile)">
                重新裁剪
              </el-button>
              <el-button v-if="pendingHeroFile" link @click="clearPendingHero">取消选择</el-button>
              <el-button v-else-if="settings.heroImage" link @click="clearHero">移除头图</el-button>
            </div>

            <p v-if="pendingHeroFile" class="card-tip">已裁剪好 16:9 头图，点「保存头图」提交</p>
          </div>
        </div>
      </section>
    </template>

    <!-- 选好原图先在这里框选成 16:9 的横图，再走上传流程 -->
    <el-dialog
      v-model="cropVisible"
      title="裁剪头图"
      width="400"
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
          alt="待裁剪的头图"
          draggable="false"
        />
      </div>

      <div class="crop-controls">
        <p class="crop-hint">拖动图片调整位置，滑块或滚轮缩放，框内就是最终头图（16:9）</p>
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

/* ---------- 裁剪头图 ---------- */

.crop-stage {
  position: relative;
  width: 320px;
  height: 180px;
  margin: 0 auto;
  overflow: hidden;
  border-radius: 12px;
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
