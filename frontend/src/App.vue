<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import DockNav from '@/components/DockNav.vue'

const route = useRoute()

// 登录页和后台管理都有独立布局，不显示前台底部导航
const showDock = computed(() => route.path !== '/login' && !route.path.startsWith('/admin'))
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
