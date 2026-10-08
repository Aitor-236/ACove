<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import DockNav from '@/components/DockNav.vue'
import request from '@/utils/request'

const route = useRoute()

// 登录页和后台管理都有独立布局，不显示前台底部导航
const showDock = computed(() => route.path !== '/login' && !route.path.startsWith('/admin'))

/**
 * 网站名由后台「网站设置」维护，进站时取一次写进浏览器标题；
 * 拿不到就保留 index.html 里的默认标题。
 */
onMounted(async () => {
  try {
    const res = (await request.get('/site/settings')) as { data: { siteName: string } }
    if (res.data.siteName) {
      document.title = res.data.siteName
    }
  } catch {
    // 拿不到站点设置就用默认标题
  }
})
</script>

<template>
  <router-view />
  <DockNav v-if="showDock" />
</template>

<style>
:root {
  /* 浅色米白 + 棕色：整体主题变量，前台页面统一复用 */
  --bg-cream: #f6f1e7;
  --accent-brown: #8a5a3b;
  --accent-brown-soft: #b98a5e;
  --text-strong: #3f2e22;
  --text-body: rgba(74, 54, 41, 0.78);
  --text-muted: rgba(74, 54, 41, 0.58);
  --panel-bg: #fffdf9;
  --panel-alt-bg: #f8f2e7;
  --panel-border: rgba(138, 90, 59, 0.14);
  --panel-shadow: 0 18px 44px rgba(120, 88, 58, 0.12);
  /* 访问统计里涨 / 跌的文字色，别在组件里写死颜色 */
  --trend-up: #4a7a52;
  --trend-down: #b4553c;
  /* Todo 三个状态的点 / 标签色，前台卡片和后台列表共用 */
  --status-doing: #8a5a3b;
  --status-todo: #b98a5e;
  --status-done: #4a7a52;
  /* 正文代码块：深棕底 + 暖色 token，规则在 styles/markdown.css */
  --code-bg: #3f2e22;
  --code-tabs-bg: #34251b;
  --code-border: rgba(63, 46, 34, 0.4);
  --code-fg: #f6f1e7;
  --code-muted: rgba(246, 241, 231, 0.6);
  --code-comment: #a4958a;
  --code-keyword: #e8a468;
  --code-string: #b9cd93;
  --code-number: #e3c07c;
  --code-title: #f0d3a4;
  --code-type: #dcb98e;
  --code-attr: #e0b183;
  --code-deletion: #e08f7f;
}

* {
  box-sizing: border-box;
}

html,
body,
#app {
  min-height: 100%;
}

body {
  margin: 0;
  color: var(--text-strong);
  font-family:
    system-ui,
    -apple-system,
    'Segoe UI',
    Roboto,
    'PingFang SC',
    'Hiragino Sans GB',
    'Microsoft YaHei',
    sans-serif;
  -webkit-font-smoothing: antialiased;
  background: var(--bg-cream);
}

a {
  color: inherit;
  text-decoration: none;
}

/* ---------- 前台通用外壳与框样式 ---------- */
/* 各页面直接复用这三个类，不要再各自复制一份；后台用的是 styles/admin.css */

/* 页面外壳：实色底 + 给底部悬浮导航留出空间 */
.page-shell {
  min-height: 100vh;
  padding: 44px 20px 160px;
  background: var(--bg-cream);
}

/* 内容版心 */
.page-container {
  width: min(1080px, 100%);
  margin: 0 auto;
}

/* 框：实色底 + 描边 + 圆角 + 投影，每块内容只有一层 */
.surface-panel {
  border: 1px solid var(--panel-border);
  border-radius: 26px;
  background: var(--panel-bg);
  box-shadow: var(--panel-shadow);
}
</style>
