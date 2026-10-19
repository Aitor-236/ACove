<script setup lang="ts">
import { useRoute } from 'vue-router'
import { Collection, Document, HomeFilled, Picture, User } from '@element-plus/icons-vue'

const route = useRoute()

// 底部全局导航配置：每个路由的长度一致。
// 登录页（/login）刻意不放进导航，只能手动输入路由访问。
const navItems = [
  { to: '/', label: '主页', icon: HomeFilled },
  { to: '/articles', label: '文章', icon: Document },
  { to: '/gallery', label: '图库', icon: Picture },
  { to: '/projects', label: '开源项目', icon: Collection },
  { to: '/about', label: '个人简介', icon: User }
]

function isActive(path: string) {
  if (path === '/') return route.path === path
  return route.path === path || route.path.startsWith(`${path}/`)
}
</script>

<template>
  <nav class="dock" aria-label="全局导航">
    <router-link
      v-for="item in navItems"
      :key="item.to"
      :to="item.to"
      class="dock-item"
      :class="{ 'is-active': isActive(item.to) }"
    >
      <span class="dock-icon" aria-hidden="true">
        <el-icon><component :is="item.icon" /></el-icon>
      </span>
      <span class="dock-label">{{ item.label }}</span>
    </router-link>
  </nav>
</template>

<style scoped>
.dock {
  position: fixed;
  bottom: 26px;
  left: 50%;
  z-index: 100;
  display: flex;
  gap: 8px;
  max-width: calc(100vw - 24px);
  padding: 10px;
  transform: translateX(-50%);
  border: 1px solid rgba(var(--accent-brown-rgb), 0.14);
  border-radius: var(--radius-panel, 6px);
  background: #fffdf9;
}

.dock-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 86px;
  height: 70px;
  gap: 5px;
  color: rgba(74, 54, 41, 0.72);
  transition: color 0.25s ease;
}

.dock-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  font-size: 20px;
  line-height: 1;
}

.dock-label {
  font-size: 12px;
  white-space: nowrap;
}

.dock-item:hover {
  color: var(--accent-brown, #9b6846);
}

.dock-item.is-active {
  color: var(--accent-brown, #9b6846);
  background: transparent;
}

.dock-item.is-active .dock-label,
.dock-item:hover .dock-label {
  text-decoration: underline;
  text-decoration-thickness: 2px;
  text-underline-offset: 5px;
}

/* 前台导航项变多后，窄屏改成等分自适应，避免溢出 */
@media (max-width: 640px) {
  .dock {
    width: calc(100% - 24px);
  }

  .dock-item {
    flex: 1 1 0;
    width: auto;
    min-width: 0;
  }

  .dock-label {
    font-size: 11px;
  }
}
</style>
