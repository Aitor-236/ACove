<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CollectionTag, Document, EditPen, Folder, TrendCharts, User } from '@element-plus/icons-vue'
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
    title: '个人管理',
    items: [{ label: '个人资料', icon: User, to: '/admin/profile' }]
  },
  {
    title: '站点数据',
    items: [{ label: '访问统计', icon: TrendCharts, to: '/admin/visits' }]
  },
  {
    title: '预留功能',
    items: [
      { label: '用户管理', icon: User, disabled: true }
    ]
  }
]

const route = useRoute()
const router = useRouter()

/** 先用 localStorage 里的值渲染，进页面后再用接口返回的资料刷新 */
const username = ref(localStorage.getItem('username') || '管理员')
const avatar = ref('')
const avatarText = computed(() => username.value.trim().charAt(0).toUpperCase() || 'A')
const avatarUrl = computed(() => (avatar.value ? `/api${avatar.value}` : ''))

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

onMounted(() => {
  void loadAccount()
  // 个人管理页保存成功后广播，侧栏跟着换名字/头像
  window.addEventListener('profile-updated', loadAccount)
})

onBeforeUnmount(() => {
  window.removeEventListener('profile-updated', loadAccount)
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
    <aside class="admin-sidebar">
      <div class="brand">
        <div class="brand-logo">A</div>
        <div class="brand-text">
          <strong>Aitor Blog</strong>
          <span>管理后台</span>
        </div>
      </div>

      <nav class="admin-nav" aria-label="后台导航">
        <div v-for="group in navGroups" :key="group.title" class="nav-group">
          <p class="nav-group-title">{{ group.title }}</p>

          <template v-for="item in group.items" :key="item.label">
            <router-link
              v-if="item.to && !item.disabled"
              :to="item.to"
              class="nav-item"
              :class="{ 'is-active': isActive(item) }"
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
          <router-link to="/" class="footer-link">返回前台</router-link>
          <button type="button" class="footer-link is-danger" @click="handleLogout">
            退出登录
          </button>
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
  line-height: 1.35;
}

.brand-text strong {
  color: var(--text-strong);
  font-size: 15px;
  font-weight: 600;
}

.brand-text span {
  color: var(--text-muted);
  font-size: 12px;
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

  .admin-sidebar {
    position: static;
    flex: none;
    width: 100%;
    height: auto;
  }

  .admin-nav {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 14px;
    padding: 16px 0;
  }

  .nav-group {
    flex-direction: row;
    flex-wrap: wrap;
    align-items: center;
  }

  .nav-group-title {
    width: 100%;
  }
}
</style>
