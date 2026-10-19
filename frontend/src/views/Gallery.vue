<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Picture } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
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

const router = useRouter()
const galleries = ref<GalleryAlbum[]>([])
const loading = ref(true)
const error = ref('')

function imageSource(path: string | null) {
  if (!path) return ''
  return /^https?:\/\//i.test(path) ? path : `/api${path.startsWith('/') ? path : `/${path}`}`
}

async function loadGalleries() {
  loading.value = true
  error.value = ''
  try {
    const res = (await request.get('/gallery/list')) as { data: GalleryAlbum[] }
    galleries.value = res.data ?? []
  } catch {
    galleries.value = []
    error.value = '图库加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function openGallery(id: number) {
  void router.push({ name: 'GalleryDetail', params: { id } })
}

onMounted(loadGalleries)
</script>

<template>
  <div class="page-shell">
    <main class="page-container gallery-container">
      <header class="gallery-header">
        <div>
          <p class="gallery-eyebrow">COLLECTIONS</p>
          <h1 class="gallery-title">图库</h1>
        </div>
        <p v-if="!loading && !error" class="gallery-total">{{ galleries.length }} 个图库</p>
      </header>

      <section v-if="loading" class="gallery-state" aria-live="polite">
        <p>正在加载图库……</p>
      </section>

      <section v-else-if="error" class="gallery-state" role="alert">
        <p>{{ error }}</p>
        <el-button @click="loadGalleries">重新加载</el-button>
      </section>

      <section v-else-if="!galleries.length" class="gallery-state">
        <el-icon class="state-icon"><Picture /></el-icon>
        <p>这里还没有公开的图库。</p>
      </section>

      <div v-else class="gallery-grid">
        <button
          v-for="gallery in galleries"
          :key="gallery.id"
          type="button"
          class="gallery-card"
          :aria-label="`查看图库：${gallery.name}，共 ${gallery.imageCount} 张图片`"
          @click="openGallery(gallery.id)"
        >
          <span class="gallery-cover">
            <img
              v-if="gallery.coverImage"
              :src="imageSource(gallery.coverImage)"
              :alt="`${gallery.name}封面`"
              loading="lazy"
            />
            <span v-else class="cover-placeholder" aria-hidden="true">
              <el-icon><Picture /></el-icon>
            </span>
          </span>

          <span class="gallery-card-body">
            <span class="gallery-card-title">{{ gallery.name }}</span>
            <span class="image-count">{{ gallery.imageCount }} 张</span>
            <span v-if="gallery.description" class="gallery-card-description">
              {{ gallery.description }}
            </span>
          </span>
        </button>
      </div>
    </main>
  </div>
</template>

<style scoped>
.gallery-container {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.gallery-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
}

.gallery-eyebrow {
  margin: 0 0 9px;
  color: var(--accent-brown);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.2em;
}

.gallery-title {
  margin: 0;
  color: var(--text-strong);
  font-size: clamp(28px, 4vw, 38px);
  font-weight: 650;
  letter-spacing: 0.04em;
}

.gallery-total {
  margin: 0 0 4px;
  color: var(--text-muted);
  font-size: 13px;
  white-space: nowrap;
}

.gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 260px), 1fr));
  gap: 22px;
}

.gallery-card {
  display: block;
  min-width: 0;
  padding: 0;
  border: none;
  background: transparent;
  overflow: hidden;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.gallery-card:hover .gallery-card-title {
  color: var(--accent-brown);
  text-decoration: underline;
  text-underline-offset: 4px;
}

.gallery-card:focus-visible {
  outline: 2px solid var(--accent-brown);
  outline-offset: 3px;
}

.gallery-cover {
  position: relative;
  display: block;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  border-radius: var(--radius-panel, 6px);
  background: var(--panel-alt-bg);
}

.gallery-cover img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  color: var(--accent-brown-soft);
  font-size: 36px;
}

.image-count {
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1;
}

.gallery-card-body {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 15px 9px 10px;
}

.gallery-card-title {
  color: var(--text-strong);
  font-size: 17px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.gallery-card-description {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.65;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.gallery-state {
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

.gallery-state p {
  margin: 0;
}

.state-icon {
  color: var(--accent-brown-soft);
  font-size: 30px;
}

@media (max-width: 640px) {
  .gallery-header {
    align-items: flex-start;
  }

  .gallery-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .gallery-card {
    padding: 0;
  }

  .gallery-cover {
    border-radius: var(--radius-panel, 6px);
  }

  .gallery-card-body {
    padding: 11px 6px 7px;
  }

  .gallery-card-title {
    font-size: 14px;
  }

  .gallery-card-description {
    font-size: 12px;
  }
}
</style>
