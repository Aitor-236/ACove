<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import MarkdownPreview from '@/components/MarkdownPreview.vue'
import request from '@/utils/request'

interface PublicProfilePage {
  mode: 'markdown' | 'html'
  markdownContent: string
  htmlUrl: string
  contactEmail: string
}

interface SiteOwner {
  username: string
  avatar: string
}

const loading = ref(true)
const loadError = ref(false)
const page = ref<PublicProfilePage>({
  mode: 'markdown',
  markdownContent: '',
  htmlUrl: '',
  contactEmail: '',
})
const owner = ref<SiteOwner>({ username: '站长', avatar: '' })
const ownerAvatarUrl = computed(() =>
  owner.value.avatar ? `/api${owner.value.avatar}` : '/avatar.svg',
)
const htmlFrameUrl = computed(() => (page.value.htmlUrl ? `/api${page.value.htmlUrl}` : ''))
const contactEmail = computed(() => page.value.contactEmail?.trim() || '')

let htmlResizeObserver: ResizeObserver | null = null

function resizeHtmlFrame(event: Event) {
  const frame = event.currentTarget as HTMLIFrameElement
  const document = frame.contentDocument
  if (!document) return

  htmlResizeObserver?.disconnect()
  const resize = () => {
    const body = document.body
    const root = document.documentElement
    const height = Math.max(
      body?.scrollHeight ?? 0,
      body?.offsetHeight ?? 0,
      root?.scrollHeight ?? 0,
      root?.offsetHeight ?? 0,
    )
    frame.style.height = `${Math.max(height, 1)}px`
  }

  resize()
  if (typeof ResizeObserver !== 'undefined') {
    htmlResizeObserver = new ResizeObserver(resize)
    if (document.body) htmlResizeObserver.observe(document.body)
    htmlResizeObserver.observe(document.documentElement)
  }
  document.addEventListener('load', resize, true)
  document.fonts?.ready.then(resize).catch(() => {})
}

async function loadProfilePage() {
  loading.value = true
  loadError.value = false
  try {
    const res = (await request.get('/site/profile-page')) as { data: PublicProfilePage }
    page.value = {
      mode: res.data.mode === 'html' ? 'html' : 'markdown',
      markdownContent: res.data.markdownContent || '',
      htmlUrl: res.data.htmlUrl || '',
      contactEmail: res.data.contactEmail || '',
    }
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function loadOwner() {
  try {
    const res = (await request.get('/site/owner')) as { data: SiteOwner }
    owner.value = {
      username: res.data.username || '站长',
      avatar: res.data.avatar || '',
    }
  } catch {
    // 站长资料加载失败时保留默认名称和头像。
  }
}

onMounted(() => {
  void Promise.all([loadProfilePage(), loadOwner()])
})

onBeforeUnmount(() => {
  htmlResizeObserver?.disconnect()
  htmlResizeObserver = null
})
</script>

<template>
  <div class="page-shell about-shell">
    <main class="page-container about-container">
      <div v-if="loading" class="surface-panel about-state">正在加载个人简介…</div>
      <section v-else-if="loadError" class="surface-panel about-state">
        <p>个人简介加载失败，请稍后重试。</p>
        <el-button type="primary" @click="loadProfilePage">重新加载</el-button>
      </section>

      <section v-else-if="page.mode === 'html'" class="surface-panel about-html-panel">
        <iframe
          v-if="htmlFrameUrl"
          :key="htmlFrameUrl"
          class="about-html-frame"
          :src="htmlFrameUrl"
          title="站长个人简介"
          sandbox="allow-same-origin allow-popups"
          referrerpolicy="no-referrer"
          @load="resizeHtmlFrame"
        ></iframe>
        <div v-else class="about-state">站长还没有发布 HTML 简介。</div>
      </section>

      <article v-else class="surface-panel about-markdown-panel">
        <header class="about-owner">
          <img :src="ownerAvatarUrl" :alt="`${owner.username} 的头像`" />
          <div class="about-owner-copy">
            <h1>{{ owner.username }}</h1>
            <p v-if="contactEmail" class="about-contact">
              <span>联系邮箱</span>
              <a :href="`mailto:${contactEmail}`">{{ contactEmail }}</a>
            </p>
          </div>
        </header>
        <div class="about-markdown-divider" aria-hidden="true"></div>
        <MarkdownPreview :source="page.markdownContent" empty-text="站长还没有填写个人简介。" />
      </article>
    </main>
  </div>
</template>

<style scoped>
.about-shell {
  padding-top: clamp(24px, 5vh, 54px);
}

.about-container {
  width: min(960px, 100%);
}

.about-markdown-panel {
  padding: clamp(24px, 5vw, 52px);
}

.about-owner {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 32px;
}

.about-owner img {
  width: clamp(76px, 12vw, 112px);
  height: clamp(76px, 12vw, 112px);
  flex: 0 0 auto;
  border: 1px solid var(--panel-border);
  border-radius: 50%;
  background: var(--panel-alt-bg);
  object-fit: cover;
}

.about-owner-copy h1 {
  margin: 4px 0 6px;
  color: var(--text-strong);
  font-size: clamp(25px, 4vw, 36px);
  line-height: 1.25;
}

.about-contact {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
  margin: 0;
  color: var(--text-muted);
  font-size: 14px;
}

.about-contact a {
  color: var(--accent-brown);
  overflow-wrap: anywhere;
}

.about-markdown-divider {
  height: 1px;
  margin-bottom: 28px;
  background: var(--panel-border);
}

.about-html-panel {
  overflow: hidden;
  padding: 0;
}

.about-html-frame {
  display: block;
  width: 100%;
  min-height: 320px;
  border: 0;
  background: var(--panel-bg, #fffdf9);
}

.about-state {
  padding: 48px 24px;
  color: var(--text-muted);
  text-align: center;
}

.about-state p {
  margin: 0 0 16px;
}

@media (max-width: 640px) {
  .about-markdown-panel {
    padding: 24px 20px;
  }

  .about-owner {
    gap: 16px;
  }
}
</style>
