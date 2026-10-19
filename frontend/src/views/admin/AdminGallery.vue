<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import {
  Delete,
  EditPen,
  Picture,
  Plus,
  Upload
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { VueDraggable } from 'vue-draggable-plus'
import request from '@/utils/request'

interface GalleryAlbum {
  id: number
  name: string
  description: string | null
  coverImage: string | null
  imageCount: number
  createdAt: string
  updatedAt: string
}

interface GalleryImage {
  id: number
  url: string
  sortOrder: number
}

interface GalleryDetail extends GalleryAlbum {
  images: GalleryImage[]
}

const MAX_IMAGE_SIZE = 5 * 1024 * 1024
const ACCEPTED_IMAGE_TYPES = 'image/png,image/jpeg,image/webp,image/gif'

const albums = ref<GalleryAlbum[]>([])
const selectedGalleryId = ref<number | null>(null)
const detail = ref<GalleryDetail | null>(null)
const listLoading = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const submitting = ref(false)
const deletingGallery = ref(false)
const deletingImageId = ref<number | null>(null)
const uploading = ref(false)
const reordering = ref(false)
const uploadProgress = ref({ done: 0, total: 0 })
const fileInput = ref<HTMLInputElement | null>(null)

const selectedGallery = computed(
  () => albums.value.find((album) => album.id === selectedGalleryId.value) ?? null
)
const previewUrls = computed(() => detail.value?.images.map((image) => imageSource(image.url)) ?? [])

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = ref({ name: '', description: '' })
const dialogTitle = computed(() => (editingId.value === null ? '新建图库' : '编辑图库'))

const rules: FormRules<typeof form> = {
  name: [
    {
      validator: (_rule, value, callback) =>
        callback(String(value ?? '').trim() ? undefined : new Error('请输入图库名称')),
      trigger: 'blur'
    },
    { max: 200, message: '图库名称不能超过 200 个字符', trigger: 'blur' }
  ]
}

function imageSource(path: string | null) {
  if (!path) return ''
  return /^https?:\/\//i.test(path) ? path : `/api${path.startsWith('/') ? path : `/${path}`}`
}

async function loadGallery(id: number) {
  detailLoading.value = true
  detailError.value = ''
  try {
    const res = (await request.get(`/gallery/detail/${id}`)) as { data: GalleryDetail }
    if (selectedGalleryId.value === id) detail.value = res.data
  } catch {
    if (selectedGalleryId.value === id) {
      detail.value = null
      detailError.value = '图库内容加载失败，请稍后重试。'
    }
  } finally {
    if (selectedGalleryId.value === id) detailLoading.value = false
  }
}

async function loadAlbums() {
  listLoading.value = true
  try {
    const res = (await request.get('/admin/gallery/list')) as { data: GalleryAlbum[] }
    albums.value = res.data ?? []
    if (!albums.value.some((album) => album.id === selectedGalleryId.value)) {
      selectedGalleryId.value = albums.value[0]?.id ?? null
    }
    if (selectedGalleryId.value === null) {
      detail.value = null
      detailError.value = ''
      detailLoading.value = false
    } else {
      await loadGallery(selectedGalleryId.value)
    }
  } catch {
    albums.value = []
    selectedGalleryId.value = null
    detail.value = null
    detailError.value = ''
  } finally {
    listLoading.value = false
  }
}

function selectGallery(id: number) {
  if (uploading.value || reordering.value || deletingGallery.value) return
  if (selectedGalleryId.value === id) return
  selectedGalleryId.value = id
  detail.value = null
  detailError.value = ''
  void loadGallery(id)
}

function openDialog(album?: GalleryAlbum) {
  editingId.value = album?.id ?? null
  form.value = {
    name: album?.name ?? '',
    description: album?.description ?? ''
  }
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

async function submitAlbum() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      name: form.value.name.trim(),
      description: form.value.description.trim()
    }
    if (editingId.value === null) {
      const res = (await request.post('/admin/gallery/create', payload)) as { data: GalleryAlbum }
      selectedGalleryId.value = res.data.id
      ElMessage.success('图库已创建')
    } else {
      await request.post('/admin/gallery/update', { id: editingId.value, ...payload })
      ElMessage.success('图库已更新')
    }
    dialogVisible.value = false
    await loadAlbums()
  } catch {
    // 错误提示由 request 拦截器处理，保留弹窗方便修改。
  } finally {
    submitting.value = false
  }
}

async function removeGallery(album: GalleryAlbum) {
  try {
    await ElMessageBox.confirm(
      `删除「${album.name}」会同时移除其中 ${album.imageCount} 张图片，确定继续吗？`,
      '删除图库',
      { confirmButtonText: '删除图库', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  deletingGallery.value = true
  try {
    await request.post('/admin/gallery/delete', null, { params: { id: album.id } })
    ElMessage.success('图库已删除')
    if (selectedGalleryId.value === album.id) selectedGalleryId.value = null
    await loadAlbums()
  } catch {
    // 错误提示由 request 拦截器处理。
  } finally {
    deletingGallery.value = false
  }
}

function pickImages() {
  fileInput.value?.click()
}

async function onImageFilesSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (!files.length || selectedGalleryId.value === null) return

  const galleryId = selectedGalleryId.value
  const failedNames: string[] = []
  let uploadedCount = 0
  uploading.value = true
  uploadProgress.value = { done: 0, total: files.length }

  for (const file of files) {
    if (file.size > MAX_IMAGE_SIZE) {
      failedNames.push(`${file.name}（超过 5MB）`)
      uploadProgress.value.done += 1
      continue
    }

    const payload = new FormData()
    payload.append('file', file)
    try {
      await request.post(`/admin/gallery/${galleryId}/images/upload`, payload, { silent: true })
      uploadedCount += 1
    } catch {
      failedNames.push(file.name)
    } finally {
      uploadProgress.value.done += 1
    }
  }

  uploading.value = false
  if (uploadedCount) {
    ElMessage.success(`已上传 ${uploadedCount} 张图片`)
    await loadAlbums()
  }
  if (failedNames.length) {
    const shownNames = failedNames.slice(0, 3).join('、')
    const more = failedNames.length > 3 ? `，另有 ${failedNames.length - 3} 个文件` : ''
    ElMessage.warning(`以下文件未能上传：${shownNames}${more}`)
  }
}

async function saveImageOrder() {
  if (!detail.value || selectedGalleryId.value === null || reordering.value) return
  const galleryId = selectedGalleryId.value
  reordering.value = true
  try {
    await request.post('/admin/gallery/images/reorder', {
      galleryId,
      imageIds: detail.value.images.map((image) => image.id)
    })
    await loadAlbums()
  } catch {
    await loadGallery(galleryId)
  } finally {
    reordering.value = false
  }
}

async function removeImage(image: GalleryImage) {
  try {
    await ElMessageBox.confirm('确定从图库中删除这张图片吗？', '删除图片', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  deletingImageId.value = image.id
  try {
    await request.post('/admin/gallery/images/delete', null, { params: { id: image.id } })
    ElMessage.success('图片已删除')
    await loadAlbums()
  } catch {
    // 错误提示由 request 拦截器处理。
  } finally {
    deletingImageId.value = null
  }
}

onMounted(loadAlbums)
</script>

<template>
  <div class="admin-page gallery-admin-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">图库管理</h1>
      </div>
      <div class="admin-page-actions">
        <el-button :loading="listLoading" :disabled="uploading || reordering" @click="loadAlbums">
          刷新
        </el-button>
        <el-button type="primary" :disabled="uploading || reordering" @click="openDialog()">
          <el-icon><Plus /></el-icon>
          新建图库
        </el-button>
      </div>
    </header>

    <div class="gallery-admin-columns">
      <aside class="admin-panel album-panel">
        <div class="album-panel-header">
          <h2>图库列表</h2>
          <span>{{ albums.length }}</span>
        </div>

        <div v-if="listLoading && !albums.length" class="admin-state">
          <p>正在加载图库……</p>
        </div>
        <div v-else-if="!albums.length" class="admin-state album-empty">
          <el-icon><Picture /></el-icon>
          <p>还没有图库，先创建一个吧。</p>
        </div>
        <div v-else class="album-list">
          <article
            v-for="album in albums"
            :key="album.id"
            class="album-item"
            :class="{ 'is-active': album.id === selectedGalleryId }"
          >
            <button
              type="button"
              class="album-select"
              :disabled="uploading || reordering || deletingGallery"
              @click="selectGallery(album.id)"
            >
              <span class="album-name">{{ album.name }}</span>
              <span class="album-count">{{ album.imageCount }} 张图片</span>
            </button>
            <div class="album-actions">
              <el-button
                link
                type="primary"
                :disabled="deletingGallery || uploading || reordering"
                aria-label="编辑图库"
                @click="openDialog(album)"
              >
                <el-icon><EditPen /></el-icon>
              </el-button>
              <el-button
                link
                type="danger"
                :loading="deletingGallery && selectedGalleryId === album.id"
                :disabled="deletingGallery || uploading || reordering"
                aria-label="删除图库"
                @click="removeGallery(album)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </article>
        </div>
      </aside>

      <section class="admin-panel gallery-workspace">
        <template v-if="selectedGallery">
          <header class="workspace-header">
            <div class="workspace-title-group">
              <h2>{{ selectedGallery.name }}</h2>
              <p v-if="selectedGallery.description">{{ selectedGallery.description }}</p>
              <p v-else class="muted">还没有填写图库简介。</p>
            </div>
            <div class="workspace-actions">
              <el-button :disabled="uploading || reordering" @click="openDialog(selectedGallery)">
                编辑图库
              </el-button>
              <el-button
                type="primary"
                :loading="uploading"
                :disabled="detailLoading || reordering || !detail"
                @click="pickImages"
              >
                <el-icon><Upload /></el-icon>
                添加图片
              </el-button>
              <input
                ref="fileInput"
                class="visually-hidden"
                type="file"
                :accept="ACCEPTED_IMAGE_TYPES"
                multiple
                @change="onImageFilesSelected"
              />
            </div>
          </header>

          <p v-if="uploading" class="upload-status" role="status">
            正在上传图片 {{ uploadProgress.done }} / {{ uploadProgress.total }}……
          </p>

          <div v-if="detailLoading" class="admin-state workspace-state">
            <p>正在加载图片……</p>
          </div>
          <div v-else-if="detailError" class="admin-state workspace-state" role="alert">
            <p>{{ detailError }}</p>
            <el-button @click="loadGallery(selectedGallery.id)">重新加载</el-button>
          </div>
          <div v-else-if="!detail?.images.length" class="admin-state workspace-state">
            <el-icon><Picture /></el-icon>
            <p>图库还没有图片，点击「添加图片」开始上传。</p>
            <span>支持 PNG、JPG、WEBP、GIF，单张不超过 5MB。</span>
          </div>
          <div v-else class="image-manager">
            <VueDraggable
              v-model="detail.images"
              tag="div"
              class="image-manager-grid"
              :animation="180"
              ghost-class="is-ghost"
              handle=".drag-handle"
              filter=".image-actions"
              :prevent-on-filter="false"
              :delay="200"
              :delay-on-touch-only="true"
              :disabled="uploading || reordering || deletingImageId !== null || detailLoading"
              @end="saveImageOrder"
            >
              <article v-for="(image, index) in detail.images" :key="image.id" class="image-card">
                <el-image
                  class="managed-image"
                  :src="imageSource(image.url)"
                  :alt="`${selectedGallery.name}第 ${index + 1} 张图片`"
                  :preview-src-list="previewUrls"
                  :initial-index="index"
                  fit="cover"
                  preview-teleported
                >
                  <template #error>
                    <div class="image-error"><el-icon><Picture /></el-icon></div>
                  </template>
                </el-image>
                <div class="image-card-footer">
                  <span
                    class="drag-handle"
                    role="img"
                    :aria-label="`第 ${index + 1} 张图片的拖动手柄`"
                  >
                    <span class="drag-grip" aria-hidden="true"></span>
                  </span>
                  <div class="image-actions">
                    <el-button
                      link
                      type="danger"
                      :loading="deletingImageId === image.id"
                      :disabled="deletingImageId !== null || uploading || reordering"
                      aria-label="删除图片"
                      @click="removeImage(image)"
                    >
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                </div>
              </article>
            </VueDraggable>
          </div>
        </template>

        <div v-else class="admin-state workspace-state">
          <el-icon><Picture /></el-icon>
          <p>选择左侧图库开始管理，或先创建一个图库。</p>
        </div>
      </section>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
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
        <el-form-item label="图库名称" prop="name">
          <el-input
            v-model="form.name"
            maxlength="200"
            placeholder="例如：旅行记录"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="图库简介" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            placeholder="简单介绍这个图库"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="submitting" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAlbum">
          {{ editingId === null ? '创建图库' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.gallery-admin-page {
  gap: 20px;
}

.gallery-admin-columns {
  display: grid;
  grid-template-columns: minmax(230px, 285px) minmax(0, 1fr);
  align-items: start;
  gap: 18px;
}

.album-panel {
  padding: 18px 14px 14px;
}

.album-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 5px 14px;
  border-bottom: 1px solid var(--panel-border);
}

.album-panel-header h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 15px;
  font-weight: 600;
}

.album-panel-header > span {
  color: var(--text-muted);
  font-size: 12px;
}

.album-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding-top: 11px;
}

.album-item {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  padding: 4px 5px 4px 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-panel, 6px);
  transition: background-color 0.18s ease;
}

.album-item:hover {
  background: var(--panel-alt-bg);
}

.album-item.is-active {
  border-color: var(--panel-border);
  background: var(--panel-alt-bg);
}

.album-select {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 5px;
  padding: 7px 0;
  border: 0;
  color: inherit;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.album-select:disabled {
  cursor: not-allowed;
}

.album-name {
  overflow: hidden;
  color: var(--text-strong);
  font-size: 13px;
  font-weight: 550;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.album-count {
  color: var(--text-muted);
  font-size: 11px;
}

.album-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0;
}

.gallery-workspace {
  min-width: 0;
  min-height: 320px;
  padding: 22px;
}

.workspace-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 17px;
  border-bottom: 1px solid var(--panel-border);
}

.workspace-title-group {
  min-width: 0;
}

.workspace-title-group h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 19px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.workspace-title-group p {
  margin: 8px 0 0;
  color: var(--text-body);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-line;
}

.workspace-title-group p.muted {
  color: var(--text-muted);
}

.workspace-actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  clip-path: inset(50%);
}

.upload-status {
  margin: 13px 0 -1px;
  color: var(--accent-brown);
  font-size: 12px;
}

.image-manager-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 190px), 1fr));
  gap: 14px;
  padding-top: 18px;
}

.image-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--panel-border);
  border-radius: var(--radius-panel, 6px);
  background: var(--panel-bg);
}

.managed-image {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  background: var(--panel-alt-bg);
  cursor: zoom-in;
}

.image-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 5px;
  min-height: 42px;
  padding: 4px 8px 4px 11px;
  color: var(--text-muted);
  font-size: 11px;
}

.drag-handle {
  display: inline-flex;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-control, 4px);
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.drag-handle:active {
  cursor: grabbing;
}

.drag-grip {
  position: relative;
  display: inline-block;
  width: 4px;
  height: 4px;
  flex: 0 0 4px;
  border-radius: 50%;
  background: var(--accent-brown-soft);
  box-shadow:
    6px 0 var(--accent-brown-soft),
    0 6px var(--accent-brown-soft),
    6px 6px var(--accent-brown-soft);
}

:deep(.image-card.is-ghost) {
  opacity: 0.45;
  border-style: dashed;
  border-color: var(--accent-brown);
}

.image-actions {
  display: flex;
  align-items: center;
}

.image-actions :deep(.el-button) {
  margin-left: 0;
  padding: 6px;
}

.image-error {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  color: var(--text-faint);
  font-size: 28px;
}

.admin-state {
  color: var(--text-muted);
}

.album-empty,
.workspace-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 230px;
  padding: 28px 16px;
  text-align: center;
}

.album-empty .el-icon,
.workspace-state .el-icon {
  color: var(--accent-brown-soft);
  font-size: 29px;
}

.album-empty p,
.workspace-state p {
  margin: 0;
}

.workspace-state span {
  font-size: 12px;
}

:deep(.el-dialog) {
  border-radius: var(--radius-panel, 6px);
  background: var(--panel-bg);
}

:deep(.el-dialog__title) {
  color: var(--text-strong);
  font-weight: 600;
}

@media (max-width: 900px) {
  .gallery-admin-columns {
    grid-template-columns: minmax(0, 1fr);
  }

  .album-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 220px), 1fr));
  }

  .album-panel {
    padding-bottom: 12px;
  }
}

@media (max-width: 640px) {
  .gallery-workspace {
    padding: 17px 14px;
  }

  .workspace-header {
    flex-direction: column;
  }

  .workspace-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .image-manager-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .image-card-footer {
    padding-right: 4px;
    padding-left: 8px;
  }

  .image-actions :deep(.el-button) {
    padding: 5px;
  }
}
</style>
