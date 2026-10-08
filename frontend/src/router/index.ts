import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

const routes = [
  // 默认主页
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue')
  },
  {
    path: '/articles',
    name: 'Articles',
    component: () => import('@/views/Articles.vue')
  },
  // 文章详情页，id 对应后端已发布文章ID
  {
    path: '/articles/:id',
    name: 'ArticleDetail',
    component: () => import('@/views/ArticleDetail.vue')
  },
  {
    path: '/gallery',
    name: 'Gallery',
    component: () => import('@/views/Gallery.vue')
  },
  {
    path: '/projects',
    name: 'Projects',
    component: () => import('@/views/Projects.vue')
  },
  {
    path: '/about',
    name: 'About',
    component: () => import('@/views/About.vue')
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  // 后台管理：左侧导航 + 子路由，后续新增后台功能往 children 里加即可
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    redirect: '/admin/articles',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'articles',
        name: 'AdminArticles',
        component: () => import('@/views/admin/AdminArticles.vue')
      },
      {
        path: 'articles/new',
        name: 'AdminArticleCreate',
        component: () => import('@/views/admin/AdminArticleEdit.vue')
      },
      {
        path: 'articles/:id/edit',
        name: 'AdminArticleEdit',
        component: () => import('@/views/admin/AdminArticleEdit.vue')
      },
      {
        path: 'categories',
        name: 'AdminCategories',
        component: () => import('@/views/admin/AdminCategories.vue')
      },
      {
        path: 'tags',
        name: 'AdminTags',
        component: () => import('@/views/admin/AdminTags.vue')
      },
      {
        path: 'todos',
        name: 'AdminTodos',
        component: () => import('@/views/admin/AdminTodos.vue')
      },
      {
        path: 'projects',
        name: 'AdminProjects',
        component: () => import('@/views/admin/AdminProjects.vue')
      },
      {
        path: 'profile',
        name: 'AdminProfile',
        component: () => import('@/views/admin/AdminProfile.vue')
      },
      {
        path: 'site',
        name: 'AdminSite',
        component: () => import('@/views/admin/AdminSite.vue')
      },
      {
        path: 'visits',
        name: 'AdminVisits',
        component: () => import('@/views/admin/AdminVisits.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    // 前进/后退时恢复浏览器记住的滚动位置，普通跳转回到顶部
    return savedPosition ?? { top: 0 }
  }
})

// 后台页面需要登录态，没登录就回登录页并记住原本要去的地址
router.beforeEach((to) => {
  const requiresAuth = to.matched.some((record) => record.meta?.requiresAuth)
  if (!requiresAuth || localStorage.getItem('token')) {
    return true
  }

  ElMessage.warning('请先登录后再进入后台管理')
  return { path: '/login', query: { redirect: to.fullPath } }
})

/**
 * 前台页面浏览上报：后台访问统计的 PV / UV 就来自这里。
 * 只报前台页面（后台和登录页不算访问），用 sendBeacon 发出去就走，
 * 不等响应、不弹提示，上报失败也不影响页面。
 */
function reportVisit() {
  if (navigator.sendBeacon) {
    navigator.sendBeacon('/api/visit/report')
    return
  }
  fetch('/api/visit/report', { method: 'POST', keepalive: true }).catch(() => {})
}

router.afterEach((to) => {
  const path = to.path
  if (path.startsWith('/admin') || path.startsWith('/login')) {
    return
  }
  reportVisit()
})

export default router
