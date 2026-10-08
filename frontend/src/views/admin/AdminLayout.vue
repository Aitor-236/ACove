<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  Collection,
  CollectionTag,
  Document,
  EditPen,
  Folder,
  List,
  Setting,
  TrendCharts,
  User
} from '@element-plus/icons-vue'
import '@/styles/admin.css'
import request from '@/utils/request'

/** 个人资料（来自 GET /admin/user/profile），用于侧栏账号区块 */
interface ProfileInfo {
  id: number
  username: string
  email: string
  /** 头像相对地址，空字符串表示还没设置过头像 */
  avatar: string
}

/** 左侧导航的一项，to 为空或 disabled 表示功能还没做，只占位不可点。 */
interface NavItem {
  label: string
  icon: Component
  to?: string
  disabled?: boolean
}

interface NavGroup {
  title: string
  items: NavItem[]
}

/**
 * 后台导航配置：后续新增后台功能时，往 groups 里加一项即可，
 * 布局和样式不用改。
 */
const navGroups: NavGroup[] = [
  {
    title: '内容管理',
    items: [
      { label: '文章列表', icon: Document, to: '/admin/articles' },
      { label: '新建文章', icon: EditPen, to: '/admin/articles/new' },
      { label: '分类管理', icon: Folder, to: '/admin/categories' },
      { label: '标签管理', icon: CollectionTag, to: '/admin/tags' }
    ]
  },
  {
    title: '待办事项',
    items: [{ label: '待办清单', icon: List, to: '/admin/todos' }]
  },
  {
    title: '开源项目',
    items: [{ label: '项目列表', icon: Collection, to: '/admin/projects' }]
  },
  {
    title: '个人管理',
    items: [{ label: '个人资料', icon: User, to: '/admin/profile' }]
  },
  {
    title: '站点设置',
    items: [{ label: '网站设置', icon: Setting, to: '/admin/site' }]
  },
  {
    title: '站点数据',
    items: [{ label: '访问统计', icon: TrendCharts, to: '/admin/visits' }]
  }
]

const route = useRoute()
const router = useRouter()

/** 窄屏下左侧导航收成一个下拉：用一个按钮展开 / 收起 */
const mobileNavOpen = ref(false)
const sidebarRef = ref<HTMLElement | null>(null)

/** 先用 localStorage 里的值渲染，进页面后再用接口返回的资料刷新 */
const username = ref(localStorage.getItem('username') || '管理员')
const avatar = ref('')
const avatarText = computed(() => username.value.trim().charAt(0).toUpperCase() || 'A')
const avatarUrl = computed(() => (avatar.value ? `/api${avatar.value}` : ''))

/** 网站名（来自 GET /site/settings），显示在后台左上角；默认 ACove */
const siteName = ref(localStorage.getItem('siteName') || 'ACove')
const brandInitial = computed(() => siteName.value.trim().charAt(0).toUpperCase() || 'A')

async function loadAccount() {
  try {
    const res = (await request.get('/admin/user/profile')) as { data: ProfileInfo }
    username.value = res.data.username || username.value
    avatar.value = res.data.avatar || ''
    localStorage.setItem('username', res.data.username)
    if (res.data.email) {
      localStorage.setItem('email', res.data.email)
    }
  } catch {
    // 错误提示由 request 拦截器统一处理，侧栏继续用 localStorage 里的值
  }
}

/** 网站名改了以后侧栏跟着换；接口拿不到就继续用默认值 */
async function loadSiteName() {
  try {
    const res = (await request.get('/site/settings')) as { data: { siteName: string } }
    if (res.data.siteName) {
      siteName.value = res.data.siteName
      localStorage.setItem('siteName', res.data.siteName)
    }
  } catch {
    // 错误提示由 request 拦截器统一处理，侧栏继续用 localStorage 里的值
  }
}

onMounted(() => {
  void loadAccount()
  void loadSiteName()
  // 个人管理 / 网站设置保存成功后广播，侧栏跟着换名字、头像和网站名
  window.addEventListener('profile-updated', loadAccount)
  window.addEventListener('site-settings-updated', loadSiteName)
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  window.removeEventListener('profile-updated', loadAccount)
  window.removeEventListener('site-settings-updated', loadSiteName)
  document.removeEventListener('click', handleDocumentClick)
})

function isActive(item: NavItem) {
  if (!item.to) return false
  // 新建页要和文章列表区分开，编辑页则归到文章列表。
  if (item.to === '/admin/articles/new') {
    return route.path === item.to
  }
  if (item.to === '/admin/articles') {
    return route.path === item.to || route.path.startsWith('/admin/articles/')
  }
  return route.path === item.to
}

/** 窄屏下拉按钮上显示的「当前页面」，找不到匹配项就退回兜底文案 */
const currentNav = computed(() => {
  for (const group of navGroups) {
    const active = group.items.find((item) => isActive(item))
    if (active) return active
  }
  return undefined
})
const currentNavLabel = computed(() => currentNav.value?.label ?? '后台导航')
const currentNavIcon = computed(() => currentNav.value?.icon ?? List)

function toggleMobileNav() {
  mobileNavOpen.value = !mobileNavOpen.value
}

function closeMobileNav() {
  mobileNavOpen.value = false
}

/** 下拉展开后，点到侧栏以外的位置就收起 */
function handleDocumentClick(event: MouseEvent) {
  if (!mobileNavOpen.value) return
  if (sidebarRef.value && !sidebarRef.value.contains(event.target as Node)) {
    closeMobileNav()
  }
}

// 跳转后自动把下拉收起（点当前页链接不换路由，靠链接自己的 click 关）
watch(() => route.path, closeMobileNav)

async function handleLogout() {
  try {
    await ElMessageBox.confirm('退出后需要重新登录才能进入后台，确定退出吗？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    // 用户点了取消
    return
  }

  localStorage.removeItem('token')
  localStorage.removeItem('username')
  localStorage.removeItem('email')
  ElMessage.success('已退出登录')
  void router.push('/login')
}
</script>

<template>
  <div class="admin-shell">
    <aside ref="sidebarRef" class="admin-sidebar">
      <div class="sidebar-head">
        <div class="brand">
          <div class="brand-logo">{{ brandInitial }}</div>
          <div class="brand-text">
            <strong>{{ siteName }}</strong>
            <span>管理后台</span>
          </div>
        </div>

        <button
          type="button"
          class="nav-toggle"
          :class="{ 'is-open': mobileNavOpen }"
          :aria-expanded="mobileNavOpen"
          aria-controls="admin-nav-panel"
          @click="toggleMobileNav"
        >
          <span class="nav-toggle-label">
            <el-icon><component :is="currentNavIcon" /></el-icon>
            {{ currentNavLabel }}
          </span>
          <el-icon class="nav-toggle-caret"><ArrowDown /></el-icon>
        </button>
      </div>

      <div id="admin-nav-panel" class="nav-panel" :class="{ 'is-open': mobileNavOpen }">
        <nav class="admin-nav" aria-label="后台导航">
          <div v-for="group in navGroups" :key="group.title" class="nav-group">
            <p class="nav-group-title">{{ group.title }}</p>

            <template v-for="item in group.items" :key="item.label">
              <router-link
                v-if="item.to && !item.disabled"
                :to="item.to"
                class="nav-item"
                :class="{ 'is-active': isActive(item) }"
                @click="closeMobileNav"
              >
                <span class="nav-icon" aria-hidden="true">
                  <el-icon><component :is="item.icon" /></el-icon>
                </span>
                <span class="nav-label">{{ item.label }}</span>
              </router-link>

              <span v-else class="nav-item is-disabled" aria-disabled="true">
                <span class="nav-icon" aria-hidden="true">
                  <el-icon><component :is="item.icon" /></el-icon>
                </span>
                <span class="nav-label">{{ item.label }}</span>
                <span class="nav-badge">开发中</span>
              </span>
            </template>
          </div>
        </nav>

        <div class="sidebar-footer">
          <div class="account">
            <span class="account-avatar">
              <img v-if="avatarUrl" :src="avatarUrl" alt="头像" />
              <template v-else>{{ avatarText }}</template>
            </span>
            <span class="account-name">{{ username }}</span>
          </div>

          <div class="footer-actions">
            <router-link to="/" class="footer-link" @click="closeMobileNav">返回前台</router-link>
            <button type="button" class="footer-link is-danger" @click="handleLogout">
              退出登录
            </button>
          </div>
        </div>
      </div>
    </aside>

    <main class="admin-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.admin-shell {
  --el-color-primary: #8a5a3b;
  --el-color-primary-light-3: #b98a5e;
  --el-color-primary-light-5: #cfa986;
  --el-color-primary-light-7: #e2cbaf;
  --el-color-primary-light-8: #ecdcc6;
  --el-color-primary-light-9: #f4ebdd;
  --el-color-primary-dark-2: #6f4730;

  display: flex;
  gap: 20px;
  min-height: 100vh;
  padding: 20px;
  /*
   * 这里必须用 clip 而不是 hidden：overflow-x: hidden 会把 overflow-y 算成 auto，
   * 于是这个 flex 容器自己变成滚动容器，左侧导航的 position: sticky 就失效、跟着页面一起滚出屏幕。
   * clip 同样能挡住横向溢出，但不会创建滚动容器。
   */
  overflow-x: clip;
  background: var(--bg-cream);
}

.admin-sidebar {
  position: sticky;
  top: 20px;
  display: flex;
  flex: 0 0 236px;
  flex-direction: column;
  align-self: flex-start;
  height: calc(100vh - 40px);
  padding: 22px 16px 18px;
  border: 1px solid var(--panel-border);
  border-radius: 24px;
  background: var(--panel-bg);
  box-shadow: var(--panel-shadow);
}

.brand {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  gap: 12px;
  padding: 0 6px 20px;
  border-bottom: 1px solid rgba(138, 90, 59, 0.16);
}

.brand-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  color: #fdf9f2;
  font-size: 20px;
  font-weight: 700;
  background: linear-gradient(135deg, #b98a5e, #8a5a3b);
  box-shadow: 0 10px 22px rgba(138, 90, 59, 0.28);
}

.brand-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.35;
}

.brand-text strong {
  overflow: hidden;
  color: var(--text-strong);
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-text span {
  color: var(--text-muted);
  font-size: 12px;
}

/* 侧栏顶部一行：宽屏只放品牌，窄屏右侧多一个下拉触发器 */
.sidebar-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 窄屏下拉触发器：宽屏直接显示完整侧栏导航，所以隐藏 */
.nav-toggle {
  display: none;
}

/* 导航 + 页脚容器；宽屏撑满余下高度，窄屏变下拉面板 */
.nav-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.admin-nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 22px;
  padding: 20px 0;
  overflow-y: auto;
}

.nav-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nav-group-title {
  margin: 0;
  padding: 0 10px;
  color: var(--text-muted);
  font-size: 12px;
  letter-spacing: 2px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border-radius: 14px;
  color: var(--text-body);
  font-size: 14px;
  background: var(--panel-alt-bg);
  transition:
    color 0.2s ease,
    background-color 0.2s ease,
    box-shadow 0.2s ease;
}

.nav-item:hover {
  color: var(--accent-brown);
  background: #fbf6ec;
}

.nav-item.is-active {
  color: #fdf9f2;
  background: linear-gradient(135deg, #b98a5e, #8a5a3b);
  box-shadow: 0 10px 22px rgba(138, 90, 59, 0.24);
}

.nav-item.is-disabled {
  color: rgba(74, 54, 41, 0.4);
  background: rgba(248, 242, 231, 0.6);
  cursor: not-allowed;
}

.nav-icon {
  display: inline-flex;
  align-items: center;
  font-size: 16px;
}

.nav-label {
  flex: 1;
}

.nav-badge {
  padding: 1px 8px;
  border-radius: 999px;
  color: var(--text-muted);
  font-size: 11px;
  background: #ecdec5;
}

.sidebar-footer {
  padding-top: 16px;
  border-top: 1px solid rgba(138, 90, 59, 0.16);
}

.account {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 6px 12px;
}

.account-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  overflow: hidden;
  border-radius: 10px;
  color: #fdf9f2;
  font-size: 14px;
  font-weight: 600;
  background: linear-gradient(135deg, #cfa986, #8a5a3b);
}

.account-avatar img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.account-name {
  overflow: hidden;
  color: var(--text-strong);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.footer-actions {
  display: flex;
  gap: 8px;
}

.footer-link {
  flex: 1;
  padding: 8px 0;
  border: none;
  border-radius: 12px;
  color: var(--text-body);
  font-size: 12px;
  text-align: center;
  cursor: pointer;
  background: var(--panel-alt-bg);
  transition:
    color 0.2s ease,
    background-color 0.2s ease;
}

.footer-link:hover {
  color: var(--accent-brown);
  background: #fbf6ec;
}

.footer-link.is-danger:hover {
  color: #c45656;
}

.admin-main {
  flex: 1;
  min-width: 0;
  padding-bottom: 12px;
}

@media (max-width: 900px) {
  .admin-shell {
    flex-direction: column;
    padding: 14px;
  }

  /* 窄屏：侧栏收成顶部一条，导航藏进下拉，不再横向铺一大片 */
  .admin-sidebar {
    position: relative;
    top: auto;
    flex: none;
    width: 100%;
    height: auto;
    padding: 14px 16px;
  }

  .brand {
    padding: 0;
    border-bottom: none;
  }

  .nav-toggle {
    display: inline-flex;
    flex-shrink: 0;
    align-items: center;
    gap: 8px;
    padding: 9px 14px;
    border: 1px solid var(--panel-border);
    border-radius: 14px;
    color: var(--text-strong);
    font-size: 13px;
    font-weight: 600;
    background: var(--panel-alt-bg);
    cursor: pointer;
    transition:
      color 0.2s ease,
      background-color 0.2s ease;
  }

  .nav-toggle:hover,
  .nav-toggle.is-open {
    color: var(--accent-brown);
    background: #fbf6ec;
  }

  .nav-toggle-label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    white-space: nowrap;
  }

  .nav-toggle-caret {
    transition: transform 0.2s ease;
  }

  .nav-toggle.is-open .nav-toggle-caret {
    transform: rotate(180deg);
  }

  /* 下拉面板：浮在侧栏下方，展开时才显示 */
  .nav-panel {
    display: none;
    position: absolute;
    top: calc(100% + 10px);
    right: 0;
    left: 0;
    z-index: 30;
    max-height: min(70vh, 520px);
    overflow-y: auto;
    padding: 18px 16px 16px;
    border: 1px solid var(--panel-border);
    border-radius: 22px;
    background: var(--panel-bg);
    box-shadow: var(--panel-shadow);
  }

  .nav-panel.is-open {
    display: flex;
  }

  .admin-nav {
    flex: none;
    flex-direction: column;
    flex-wrap: nowrap;
    gap: 18px;
    padding: 0;
    overflow: visible;
  }

  /* 账号 / 返回前台 / 退出登录贴在面板底部，不用滚到底才能点 */
  .sidebar-footer {
    position: sticky;
    bottom: 0;
    margin-top: 14px;
    background: var(--panel-bg);
  }

  .nav-group {
    flex-direction: column;
    flex-wrap: nowrap;
    align-items: stretch;
  }

  .nav-group-title {
    width: auto;
  }
}
</style>
