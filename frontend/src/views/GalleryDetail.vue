<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowLeft, Picture } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import request from '@/utils/request'

interface GalleryImage {
  id: number
  url: string
  sortOrder: number
}

interface GalleryDetail {
  id: number
  name: string
  description: string | null
  coverImage: string | null
  imageCount: number
  createdAt: string
  updatedAt: string
  images: GalleryImage[]
}

const route = useRoute()
const gallery = ref<GalleryDetail | null>(null)
const loading = ref(true)
const error = ref('')

const previewUrls = computed(() => gallery.value?.images.map((image) => imageSource(image.url)) ?? [])

function imageSource(path: string | null) {
  if (!path) return ''
  return /^https?:\/\//i.test(path) ? path : `/api${path.startsWith('/') ? path : `/${path}`}`
}

async function loadGallery() {
  const id = String(route.params.id ?? '')
  loading.value = true
  error.value = ''
  gallery.value = null
  try {
    const res = (await request.get(`/gallery/detail/${encodeURIComponent(id)}`)) as {
      data: GalleryDetail
    }
    gallery.value = res.data
  } catch {
    error.value = '图库详情加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

watch(() => route.params.id, loadGallery, { immediate: true })
</script>

<template>
  <div class="page-shell">
    <main class="page-container gallery-detail-container">
      <router-link class="back-link" to="/gallery">
        <el-icon><ArrowLeft /></el-icon>
        返回图库
      </router-link>

      <section v-if="loading" class="detail-state" aria-live="polite">
        <p>正在加载图库……</p>
      </section>

      <section v-else-if="error" class="detail-state" role="alert">
        <p>{{ error }}</p>
        <el-button @click="loadGallery">重新加载</el-button>
      </section>

      <template v-else-if="gallery">
        <header class="detail-header">
          <div>
            <h1 class="detail-title">{{ gallery.name }}</h1>
            <p v-if="gallery.description" class="detail-description">
              {{ gallery.description }}
            </p>
          </div>
          <p class="detail-count">{{ gallery.imageCount }} 张图片</p>
        </header>

        <section v-if="!gallery.images.length" class="detail-state">
          <el-icon class="state-icon"><Picture /></el-icon>
          <p>这个图库暂时还没有图片。</p>
        </section>

        <div v-else class="image-grid">
          <el-image
            v-for="(image, index) in gallery.images"
            :key="image.id"
            class="gallery-image"
            :src="imageSource(image.url)"
            :alt="`${gallery.name}中的第 ${index + 1} 张图片`"
            :preview-src-list="previewUrls"
            :initial-index="index"
            fit="cover"
            preview-teleported
          >
            <template #error>
              <div class="image-error"><el-icon><Picture /></el-icon></div>
            </template>
          </el-image>
        </div>
      </template>
    </main>
  </div>
</template>

<style scoped>
.gallery-detail-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.back-link {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 7px;
  color: var(--accent-brown);
  font-size: 13px;
}

.back-link:hover {
  color: var(--text-strong);
}

.detail-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 0 2px 4px;
}

.detail-title {
  margin: 0;
  color: var(--text-strong);
  font-size: clamp(26px, 4vw, 36px);
  font-weight: 650;
  overflow-wrap: anywhere;
}

.detail-description {
  max-width: 720px;
  margin: 11px 0 0;
  color: var(--text-body);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-line;
}

.detail-count {
  margin: 0 0 4px;
  color: var(--text-muted);
  font-size: 13px;
  white-space: nowrap;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 240px), 1fr));
  gap: 18px;
}

.gallery-image {
  width: 100%;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  border: 1px solid var(--panel-border);
  border-radius: var(--radius-panel, 6px);
  background: var(--panel-alt-bg);
  cursor: zoom-in;
  transition: border-color 0.2s ease;
}

.gallery-image:hover {
  border-color: var(--panel-border-strong);
}

.image-error {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  color: var(--text-faint);
  font-size: 30px;
}

.detail-state {
  display: flex;
  min-height: 220px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px 20px;
  color: var(--text-muted);
  text-align: center;
}

.detail-state p {
  margin: 0;
}

.state-icon {
  color: var(--accent-brown-soft);
  font-size: 30px;
}

@media (max-width: 640px) {
  .detail-header {
    align-items: flex-start;
  }

  .image-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .gallery-image {
    border-radius: var(--radius-panel, 6px);
  }
}
</style>
