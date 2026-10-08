<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Link } from '@element-plus/icons-vue'
import request from '@/utils/request'

/** 开源项目（来自 GET /project/list） */
interface ProjectItem {
  id: number
  name: string
  /** 项目地址，空串表示还没填 */
  url: string
  description: string
  /** 日志：最新更新内容 */
  updateLog: string
  /** 下一步（todo） */
  nextStep: string
}

const projects = ref<ProjectItem[]>([])
const loading = ref(true)
const error = ref('')

/** 地址展示时去掉协议前缀，只留 github.com/xxx 这种主干，太长时用 CSS 省略 */
function displayUrl(url: string) {
  return url.replace(/^https?:\/\//i, '')
}

async function loadProjects() {
  loading.value = true
  error.value = ''
  try {
    // 后端已按「创建时间新 → 旧」排好，这里直接渲染
    const res = (await request.get('/project/list')) as { data: ProjectItem[] }
    projects.value = res.data ?? []
  } catch {
    // 错误提示由 request 拦截器统一处理
    projects.value = []
    error.value = '开源项目加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

onMounted(loadProjects)
</script>

<template>
  <div class="page-shell">
    <div class="page-container projects-container">
      <header class="projects-head">
        <h1 class="projects-title">开源项目</h1>
      </header>

      <section v-if="loading" class="surface-panel projects-state">
        <p>正在加载……</p>
      </section>

      <section v-else-if="error" class="surface-panel projects-state">
        <p>{{ error }}</p>
        <el-button @click="loadProjects">重新加载</el-button>
      </section>

      <section v-else-if="!projects.length" class="surface-panel projects-state">
        <p>还没有开源项目，先去后台添加一个吧。</p>
      </section>

      <div v-else class="project-grid">
        <article v-for="project in projects" :key="project.id" class="surface-panel project-card">
          <header class="project-head">
            <h2 class="project-name">{{ project.name }}</h2>
            <a
              v-if="project.url"
              class="project-link"
              :href="project.url"
              :title="project.url"
              target="_blank"
              rel="noopener"
            >
              <el-icon><Link /></el-icon>
              <span>{{ displayUrl(project.url) }}</span>
            </a>
          </header>

          <p v-if="project.description" class="project-desc">{{ project.description }}</p>

          <div v-if="project.updateLog || project.nextStep" class="project-meta">
            <div v-if="project.updateLog" class="project-meta-item">
              <p class="project-meta-label">日志</p>
              <p class="project-meta-text">{{ project.updateLog }}</p>
            </div>
            <div v-if="project.nextStep" class="project-meta-item">
              <p class="project-meta-label">下一步</p>
              <p class="project-meta-text">{{ project.nextStep }}</p>
            </div>
          </div>
        </article>
      </div>
    </div>
  </div>
</template>

<style scoped>
.projects-container {
  display: flex;
  flex-direction: column;
  gap: 26px;
}

.projects-head {
  margin-bottom: 2px;
}

.projects-title {
  margin: 0;
  color: var(--text-strong);
  font-size: clamp(24px, 3vw, 32px);
  font-weight: 700;
  letter-spacing: 1px;
}

.projects-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 64px 24px;
  color: var(--text-muted);
  text-align: center;
}

.projects-state p {
  margin: 0;
}

.project-grid {
  /* 瀑布流：CSS 多列布局——列宽对齐、卡片高度各随内容，谁也不会被拉成一样高 */
  column-width: 320px;
  column-gap: 22px;
}

/* 一张卡片 = 一个项目，和全站面板同一套框样式，只是内容多两段小字 */
.project-card {
  /* 多列布局里卡片是「列内的一块」：占满当前列宽、内容多高就多高，
     并且不允许被拆到两列去（break-inside: avoid） */
  width: 100%;
  min-width: 0;
  margin-bottom: 22px;
  padding: 24px 22px;
  break-inside: avoid;
}

.project-head {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.project-name {
  margin: 0;
  color: var(--text-strong);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.5px;
  /* 名字可能是很长的英文 / 无空格串，强制在容器内换行，不要溢出卡片 */
  overflow-wrap: anywhere;
}

.project-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  min-width: 0;
  color: var(--accent-brown);
  font-size: 13px;
  transition: color 0.2s ease;
}

.project-link span {
  /* flex 子项默认 min-width: auto，不写这行省略号不会生效、地址会顶出去 */
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-link:hover {
  color: #6f4327;
}

.project-desc {
  /* 卡片不再是 flex 容器，段落间距改用 margin，保证和标题、日志之间都是 14px */
  margin: 14px 0 0;
  color: var(--text-body);
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;
  white-space: pre-line;
}

/* 日志 / 下一步：紧跟在正文后面，不往卡片底部推，卡片高度就是内容高度 */
.project-meta {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--panel-border);
}

.project-meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.project-meta-label {
  margin: 0;
  color: var(--accent-brown);
  font-size: 12px;
  letter-spacing: 2px;
}

.project-meta-text {
  margin: 0;
  color: var(--text-body);
  font-size: 13.5px;
  line-height: 1.6;
  overflow-wrap: anywhere;
  white-space: pre-line;
}
</style>
