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
  --accent-brown: #9b6846;
  --accent-brown-rgb: 155, 104, 70;
  --accent-brown-soft: #b98a5e;
  --text-strong: #3f2e22;
  --text-body: rgba(74, 54, 41, 0.78);
  --text-muted: rgba(74, 54, 41, 0.58);
  /* 最淡的一档正文色：后台正文编辑器（MarkdownSplitEditor）的行号、Markdown 语法标记用它 */
  --text-faint: rgba(74, 54, 41, 0.38);
  --panel-bg: #fffdf9;
  --panel-alt-bg: #f8f2e7;
  --panel-border: rgba(var(--accent-brown-rgb), 0.14);
  /* 比 --panel-border 重一点的描边，编辑器这类要看得见边界的地方用 */
  --panel-border-strong: rgba(var(--accent-brown-rgb), 0.22);
  --panel-shadow: none;
  --radius-control: 4px;
  --radius-panel: 6px;
  --el-border-radius-base: var(--radius-control);
  --el-border-radius-small: var(--radius-control);
  --el-border-radius-round: var(--radius-control);
  /* Element Plus 浮层挂到 body 后也继承这些变量，输入框的清晰描边不受影响。 */
  --el-box-shadow: none;
  --el-box-shadow-light: none;
  --el-box-shadow-lighter: none;
  --el-box-shadow-dark: none;
  /* 访问统计里涨 / 跌的文字色，别在组件里写死颜色 */
  --trend-up: #4a7a52;
  --trend-down: #b4553c;
  /* Todo 三个状态的点 / 标签色，前台卡片和后台列表共用 */
  --status-doing: var(--accent-brown);
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

/* ---------- 页面外壳与必要容器 ---------- */
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

/* 搜索、登录及独立 HTML 等需要明确边界的容器；普通内容使用留白排版。 */
.surface-panel {
  border: 1px solid var(--panel-border);
  border-radius: var(--radius-panel, 6px);
  background: var(--panel-bg);
  box-shadow: var(--panel-shadow);
}

/* 弹窗失去投影后靠描边分隔；遮罩和组件本身的进出动画继续保留。 */
.el-dialog,
.el-message-box {
  border: 1px solid var(--panel-border-strong);
  border-radius: var(--radius-panel);
}

.el-popper {
  --el-popper-border-radius: var(--radius-panel);
}

.el-notification {
  --el-notification-radius: var(--radius-panel);
}

.el-message {
  --el-message-border-radius: var(--radius-panel);
}

.el-image-viewer__btn {
  border-radius: var(--radius-control);
}

.el-image-viewer__actions {
  border-radius: var(--radius-panel);
}

.el-message.is-plain {
  border-color: var(--panel-border);
}

.el-button.el-button--primary {
  --el-button-text-color: var(--accent-brown);
  --el-button-bg-color: transparent;
  --el-button-border-color: transparent;
  --el-button-outline-color: var(--accent-brown);
  --el-button-hover-text-color: var(--text-strong);
  --el-button-hover-bg-color: transparent;
  --el-button-hover-border-color: transparent;
  --el-button-active-color: var(--text-strong);
  --el-button-active-bg-color: transparent;
  --el-button-active-border-color: transparent;
  --el-button-disabled-text-color: var(--text-muted);
  --el-button-disabled-bg-color: transparent;
  --el-button-disabled-border-color: transparent;
}

.el-button.el-button--primary:not(.is-disabled):hover,
.el-button.el-button--primary:not(.is-disabled):focus-visible {
  text-decoration: underline;
  text-underline-offset: 4px;
}

/* 固定列的渐暗边缘由组件单独声明，随全站面板一起改成清晰边界。 */
.el-table {
  --el-table-fixed-left-column: none;
  --el-table-fixed-right-column: none;
}

.el-table .el-table-fixed-column--left.is-last-column {
  border-right: 1px solid var(--el-table-border-color);
}

.el-table .el-table-fixed-column--right.is-first-column {
  border-left: 1px solid var(--el-table-border-color);
}

/* ---------- 前台 el-select 的下拉面板（popper） ---------- */
/*
 * 下拉面板被 teleport 到 body 上，跑出页面作用域、拿不到页面的 token，
 * 会退回 Element Plus 默认的蓝色和直角，所以给带 front-select-popper 的 popper
 * 单独补一套棕色主题（后台对应的是 styles/admin.css 里的 admin-select-popper）。
 * 用法：<el-select popper-class="front-select-popper">。
 */
.el-popper.front-select-popper {
  --el-color-primary: var(--accent-brown);
  --el-color-primary-light-3: #b98a5e;
  --el-color-primary-light-5: #cfa986;
  --el-color-primary-light-7: #e2cbaf;
  --el-color-primary-light-8: #ecdcc6;
  --el-color-primary-light-9: #f4ebdd;
  --el-color-primary-dark-2: #80583d;
  --el-bg-color-overlay: var(--panel-bg);
  --el-border-color-light: var(--panel-border);
  --el-fill-color-light: #f6efe3;
  --el-text-color-regular: var(--text-body);
  --el-box-shadow-light: none;
  --el-border-radius-base: var(--radius-control, 4px);
  --el-popper-border-radius: var(--radius-panel, 6px);
}

/* 选项行稍微高一点，手指点在窄屏上更好按 */
.el-popper.front-select-popper .el-select-dropdown__item {
  height: 40px;
  line-height: 40px;
}
</style>
