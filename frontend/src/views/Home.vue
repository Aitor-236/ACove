<script lang="ts">
// 模块级变量：记住离开首页时的滚动位置，从文章详情返回时用来恢复。
// 首页的滚动发生在 .home-page 这个整屏容器里，浏览器/路由的 savedPosition 管不到它。
let savedScrollTop = 0
</script>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import request from '@/utils/request'

/** 文章卡片类型（来自 GET /article/list） */
interface ArticleItem {
  id: number
  title: string
  summary: string
  categoryName: string
  /** 首页动态列表显示更新时间和详情页保持一致 */
  updatedAt: string | null
  readingMinutes: number
}

function formatDate(value?: string | null) {
  // 和详情页保持同样的粒度，卡片上多出的时分不影响现有布局
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

/** 站长资料（来自 GET /site/owner）：首页只展示级别最高的账号，也就是站点持有者 */
interface SiteOwner {
  username: string
  avatar: string
}

/** 站点设置（来自 GET /site/settings）：网站名、首页头图、首页中间的文字 */
interface SiteSettings {
  siteName: string
  /** 头图相对地址，空字符串表示还没设置 */
  heroImage: string
  heroText: string
}

const latestArticles = ref<ArticleItem[]>([])
/** 已发布文章总数（来自 GET /article/list 的 total） */
const articleTotal = ref(0)
/** 画作数量：画廊页目前用页面内静态数组且为空，后端接口就绪后替换这里 */
const artworkTotal = ref(0)
const articlesLoading = ref(true)
const articlesError = ref('')
/** 站长名字：接口回来之前先用默认值，避免首页闪一下空白 */
const ownerName = ref('ACove')
/** 站长头像相对地址，空字符串表示还没设置头像 */
const ownerAvatar = ref('')
/** 没有自定义头像就退回 public/avatar.svg 那张占位图 */
const ownerAvatarUrl = computed(() =>
  ownerAvatar.value ? `/api${ownerAvatar.value}` : '/avatar.svg'
)
/** 站点设置：网站名 + 首页中间文字 + 头图，都由后台「网站设置」维护 */
const siteName = ref('ACove')
const heroImage = ref('')
const heroText = ref('')
/** 头图正中间那行字：没单独配就用网站名 */
const heroTitle = computed(() => heroText.value.trim() || siteName.value.trim() || 'ACove')
/** 首页头图背景：统一压暗层（保证白字可读）+ 底部向页面底色过渡，有自定义头图时再叠一层图 */
const heroBackground = computed(() => {
  const layers = [
    'linear-gradient(to bottom, rgba(246, 241, 231, 0) 55%, var(--bg-cream, #f6f1e7) 100%)',
    'linear-gradient(rgba(63, 46, 34, 0.55), rgba(63, 46, 34, 0.55))'
  ]
  if (heroImage.value) {
    layers.push(`url('/api${heroImage.value}')`)
  }
  return { backgroundImage: layers.join(', ') }
})
/** 整屏滚动容器，用来保存 / 恢复滚动位置 */
const pageRef = ref<HTMLElement | null>(null)

async function loadLatestArticles() {
  articlesLoading.value = true
  articlesError.value = ''
  try {
    const res = (await request.get('/article/list', {
      params: { page: 1, size: 3 }
    })) as {
      data: { records: ArticleItem[]; total: number }
    }
    latestArticles.value = res.data.records
    articleTotal.value = res.data.total ?? res.data.records.length
  } catch {
    latestArticles.value = []
    articlesError.value = '文章加载失败，请稍后重试。'
  } finally {
    articlesLoading.value = false
  }
}

async function loadSiteOwner() {
  try {
    const res = (await request.get('/site/owner')) as { data: SiteOwner }
    if (res.data.username) {
      ownerName.value = res.data.username
    }
    ownerAvatar.value = res.data.avatar || ''
  } catch {
    // 拿不到站长资料就继续用默认头像和名字，不影响首页其它内容
  }
}

async function loadSiteSettings() {
  try {
    const res = (await request.get('/site/settings')) as { data: SiteSettings }
    siteName.value = res.data.siteName || siteName.value
    heroImage.value = res.data.heroImage || ''
    heroText.value = res.data.heroText || ''
  } catch {
    // 拿不到站点设置就用内置默认值，不影响首页其它内容
  }
}

onMounted(async () => {
  await Promise.all([loadLatestArticles(), loadSiteOwner(), loadSiteSettings()])
  // 文章渲染完成后恢复滚动位置；内容高度可能还要再稳定一两帧，
 await nextTick()
  // 所以设置后校验一次，没到位就再等一帧重试，避免被截断到较小的值
  for (let attempt = 0; attempt < 6; attempt += 1) {
    const el = pageRef.value
    if (!el || savedScrollTop <= 0) break
    el.scrollTop = savedScrollTop
    if (Math.abs(el.scrollTop - savedScrollTop) < 2) break
    await new Promise((resolve) => requestAnimationFrame(() => resolve(null)))
  }
})

onBeforeRouteLeave(() => {
  savedScrollTop = pageRef.value?.scrollTop ?? 0
})
</script>

<template>
  <div ref="pageRef" class="home-page">
    <!-- 上 2/5：主视觉铺满整幅横向区域，不套框 -->
    <header class="home-hero" :style="heroBackground">
      <h1 class="hero-title">{{ heroTitle }}</h1>
    </header>

    <!-- 下 3/5：左侧工具栏 + 右侧主体内容 -->
    <div class="home-body">
      <!-- 工具栏：每块小组件各自成一个框，目前先放头像/名字/数据一块 -->
      <aside class="tool-panel" aria-label="工具栏">
        <section class="widget-card surface-panel profile-card">
          <img class="profile-avatar" :src="ownerAvatarUrl" :alt="`${ownerName} 的头像`" />
          <p class="profile-name">{{ ownerName }}</p>
          <dl class="profile-stats">
            <div class="profile-stat">
              <dt>文章</dt>
              <dd>{{ articleTotal }}</dd>
            </div>
            <div class="profile-stat">
              <dt>图片</dt>
              <dd>{{ artworkTotal }}</dd>
            </div>
          </dl>
        </section>
      </aside>

      <main class="content-panel">
        <div v-if="articlesLoading" class="section-state">
          <span class="state-spinner" aria-hidden="true"></span>
          <p>正在加载动态…</p>
        </div>

        <div v-else-if="articlesError" class="section-state">
          <p>{{ articlesError }}</p>
        </div>

        <div v-else-if="latestArticles.length" class="feed-list">
          <router-link
            v-for="article in latestArticles"
            :key="article.id"
            class="feed-item"
            :to="`/articles/${article.id}`"
          >
            <span class="card-tag">{{ article.categoryName }}</span>
            <h3>{{ article.title }}</h3>
            <p>{{ article.summary }}</p>
            <footer class="card-footer">
              <time :datetime="article.updatedAt || undefined">
                {{ formatDate(article.updatedAt) }}
              </time>
            </footer>
          </router-link>
        </div>

        <div v-else class="section-state">
          <p>还没有更新内容。</p>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  /* 固定一屏大小的滚动容器：滚动发生在整页上，滚动条暂时隐藏 */
  height: 100vh;
  height: 100dvh;
  padding: 0 0 148px;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: none;
  -ms-overflow-style: none;
  background: var(--bg-cream, #f6f1e7);
}

.home-page::-webkit-scrollbar {
  display: none;
}

/* 上 2/5：整幅横向铺满的主视觉，不套框、不圆角 */
.home-hero {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 55vh;
  overflow: hidden;
  /* 底色打底；压暗层、底部过渡和自定义头图由 :style 里的 heroBackground 提供 */
  background-color: #4a3728;
  background-position: center;
  background-size: cover;
  background-repeat: no-repeat;
}

.hero-title {
  margin: 0;
  color: #ffffff;
  font-size: clamp(28px, 4.5vw, 52px);
  font-weight: 700;
  letter-spacing: 2px;
  text-align: center;
  text-shadow: 0 2px 16px rgba(40, 28, 20, 0.55);
}

.home-body {
  width: min(1200px, 100%);
  margin: 0 auto;
  padding: clamp(20px, 2.6vh, 34px) clamp(16px, 3vw, 46px) 0;
  display: grid;
  grid-template-columns: minmax(210px, 280px) minmax(0, 1fr);
  gap: clamp(18px, 2vw, 26px);
  min-height: 0;
}

.tool-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

/* 第一块内容：头像 + 名字 + 文章/图片数量 */
.profile-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 20px 18px;
  text-align: center;
}

.profile-avatar {
  width: 96px;
  height: 96px;
  border: 1px solid var(--panel-border, rgba(138, 90, 59, 0.14));
  border-radius: 50%;
  background: #f2e8d6;
  object-fit: cover;
}

.profile-name {
  margin: 14px 0 0;
  color: var(--text-strong, #3f2e22);
  font-size: 18px;
  font-weight: 600;
}

.profile-stats {
  display: flex;
  justify-content: center;
  gap: 28px;
  width: 100%;
  margin: 18px 0 0;
  padding-top: 16px;
  border-top: 1px solid rgba(138, 90, 59, 0.14);
}

.profile-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
}

.profile-stat dt {
  color: var(--text-muted, rgba(74, 54, 41, 0.58));
  font-size: 12px;
}

.profile-stat dd {
  margin: 0;
  color: var(--text-strong, #3f2e22);
  font-size: 18px;
  font-weight: 600;
}

.content-panel {
  display: flex;
  flex-direction: column;
  gap: 26px;
  min-width: 0;
}

/* 每条动态单独一个框，底色/描边与左侧工具栏框完全一致 */
.feed-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.feed-item {
  display: flex;
  flex-direction: column;
  padding: 20px 22px;
  border: 1px solid var(--panel-border, rgba(138, 90, 59, 0.14));
  border-radius: 20px;
  background: var(--panel-bg, #fffdf9);
  box-shadow: 0 10px 24px rgba(120, 88, 58, 0.09);
  transition:
    transform 0.25s ease,
    box-shadow 0.25s ease,
    border-color 0.25s ease,
    background-color 0.25s ease;
}

.feed-item:hover {
  transform: translateY(-2px);
  border-color: rgba(138, 90, 59, 0.34);
  background: #fbf6ec;
  box-shadow: 0 16px 32px rgba(120, 88, 58, 0.14);
}

.card-tag {
  align-self: flex-start;
  padding: 4px 12px;
  border-radius: 999px;
  color: #7a5436;
  font-size: 12px;
  background: #ecdec5;
}

.feed-item h3 {
  margin: 14px 0 8px;
  color: var(--text-strong, #3f2e22);
  font-size: 17px;
  line-height: 1.5;
}

.feed-item p {
  margin: 0;
  color: var(--text-body, rgba(74, 54, 41, 0.78));
  font-size: 13.5px;
  line-height: 1.75;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
  padding-top: 16px;
  color: var(--text-muted, rgba(74, 54, 41, 0.58));
  font-size: 12.5px;
}

.section-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 20px;
  border: 1px dashed rgba(138, 90, 59, 0.22);
  border-radius: 20px;
  background: var(--panel-bg, #fffdf9);
  text-align: center;
}

.state-spinner {
  width: 26px;
  height: 26px;
  border: 2px solid rgba(138, 90, 59, 0.22);
  border-top-color: var(--accent-brown, #8a5a3b);
  border-radius: 50%;
  animation: state-spin 0.8s linear infinite;
}

@keyframes state-spin {
  to {
    transform: rotate(360deg);
  }
}

.section-state p {
  margin: 0;
  color: var(--text-muted, rgba(74, 54, 41, 0.58));
  font-size: 14px;
}

@media (max-width: 860px) {
  .home-page {
    padding-bottom: 132px;
  }

  .home-hero {
    height: 40vh;
  }

  /* 窗口不够宽就整栏隐藏工具栏，不把它挪到内容上方：
     工具栏以后会有多个小组件，堆在内容前面会一直挤占首屏 */
  .tool-panel {
    display: none;
  }

  .home-body {
    grid-template-columns: 1fr;
  }
}
</style>
