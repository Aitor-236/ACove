<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import request from '@/utils/request'

/** 某一天的访问量（GET /admin/visit/overview 与 /trend 里的每一项） */
interface VisitDailyItem {
  /** ISO 日期，形如 2026-09-29 */
  date: string
  /** 浏览量 */
  pv: number
  /** 独立访客数 */
  uv: number
}

/** 概览：今日 / 昨日 + 对比增幅；增幅为 null 表示昨日没有数据、算不出来 */
interface VisitOverviewData {
  today: VisitDailyItem
  yesterday: VisitDailyItem
  pvDelta: number
  uvDelta: number
  pvGrowth: number | null
  uvGrowth: number | null
}

/** 趋势图可选的天数，和后端 /admin/visit/trend 的上限（90）保持一致 */
const RANGE_OPTIONS = [7, 30, 90]

/** 底部明细表固定展示最近多少天（图表切 30/90 天时它也不变） */
const DETAIL_DAYS = 7

/** 图表绘制区（SVG 的 viewBox 坐标系），数值按比例映射到这块区域 */
const CHART = { width: 760, height: 280, left: 46, right: 14, top: 18, bottom: 34 }

const EMPTY_DAY: VisitDailyItem = { date: '', pv: 0, uv: 0 }

const loading = ref(false)
const days = ref(30)
const overview = ref<VisitOverviewData | null>(null)
const trend = ref<VisitDailyItem[]>([])

const todayStats = computed<VisitDailyItem>(() => overview.value?.today ?? EMPTY_DAY)
const yesterdayStats = computed<VisitDailyItem>(() => overview.value?.yesterday ?? EMPTY_DAY)

const pvGrowth = computed(() => describeGrowth(overview.value?.pvGrowth ?? null, overview.value?.pvDelta ?? 0))
const uvGrowth = computed(() => describeGrowth(overview.value?.uvGrowth ?? null, overview.value?.uvDelta ?? 0))

const rangeSummary = computed(() => {
  const items = trend.value
  if (!items.length) {
    return { pv: 0, uv: 0, pvAvg: 0, uvAvg: 0, peak: null as VisitDailyItem | null }
  }
  const pv = items.reduce((sum, item) => sum + item.pv, 0)
  const uv = items.reduce((sum, item) => sum + item.uv, 0)
  const peak = items.reduce<VisitDailyItem | null>(
    (best, item) => (best === null || item.pv > best.pv ? item : best),
    null
  )
  return {
    pv,
    uv,
    pvAvg: Math.round((pv / items.length) * 10) / 10,
    uvAvg: Math.round((uv / items.length) * 10) / 10,
    peak
  }
})

const rangeSummaryText = computed(() => {
  const { pv, uv, pvAvg, uvAvg, peak } = rangeSummary.value
  if (!peak) {
    return '还没有访问数据'
  }
  return `区间合计 PV ${pv} / UV ${uv}，日均 PV ${pvAvg} / UV ${uvAvg}，PV 最高的一天是 ${formatDate(peak.date)}（${peak.pv}）`
})

/** Y 轴刻度：0 / 1 / 2 / 3 档，数值取整，避免出现小数刻度 */
const axis = computed(() => {
  const maxValue = Math.max(0, ...trend.value.map((item) => Math.max(item.pv, item.uv)))
  const step = Math.max(1, niceStep(maxValue / 3))
  return { step, max: step * 3 }
})

const yTicks = computed(() => {
  const { step, max } = axis.value
  return [0, step, step * 2, max].map((value) => ({ value, y: yOf(value) }))
})

const bars = computed(() => {
  const items = trend.value
  if (!items.length) return []
  const slot = plotWidth.value / items.length
  const width = Math.max(3, Math.min(28, slot * 0.62))
  return items.map((item, index) => {
    const baseY = CHART.top + plotHeight.value
    const y = yOf(item.pv)
    return {
      index,
      x: centerX(index) - width / 2,
      y,
      width,
      height: Math.max(item.pv > 0 ? 2 : 0, baseY - y),
      tooltip: `${formatDate(item.date)}：PV ${item.pv}`
    }
  })
})

const uvPoints = computed(() =>
  trend.value.map((item, index) => ({
    index,
    x: centerX(index),
    y: yOf(item.uv),
    tooltip: `${formatDate(item.date)}：UV ${item.uv}`
  }))
)

const uvPolyline = computed(() => uvPoints.value.map((point) => `${point.x},${point.y}`).join(' '))

/** X 轴最多标 6 个日期，90 天也不会挤成一团 */
const xLabels = computed(() => {
  const items = trend.value
  if (!items.length) return []
  const labelCount = Math.min(6, items.length)
  const indexes = new Set<number>()
  if (labelCount === 1) {
    indexes.add(0)
  } else {
    for (let i = 0; i < labelCount; i += 1) {
      indexes.add(Math.round((i * (items.length - 1)) / (labelCount - 1)))
    }
  }
  return [...indexes]
    .sort((a, b) => a - b)
    .map((index) => ({
      index,
      x: centerX(index),
      text: items[index]?.date.slice(5) ?? ''
    }))
})

/**
 * 表格：固定最近 7 天，日期倒序，并给出每天的 PV 环比
 * （环比拿的是完整趋势里的前一天，所以最上面那行也能算出增幅）
 */
const tableRows = computed(() =>
  trend.value
    .map((item, index) => {
      const previous = index > 0 ? (trend.value[index - 1] ?? null) : null
      const growth =
        previous && previous.pv > 0 ? round2(((item.pv - previous.pv) / previous.pv) * 100) : null
      return {
        date: formatDate(item.date),
        pv: item.pv,
        uv: item.uv,
        tone: growth === null ? 'none' : growth > 0 ? 'up' : growth < 0 ? 'down' : 'flat',
        growthText: growth === null ? '—' : `${growth > 0 ? '+' : ''}${growth.toFixed(2)}%`
      }
    })
    .slice(-DETAIL_DAYS)
    .reverse()
)

const plotWidth = computed(() => CHART.width - CHART.left - CHART.right)
const plotHeight = computed(() => CHART.height - CHART.top - CHART.bottom)

async function loadData() {
  loading.value = true
  try {
    const [overviewRes, trendRes] = await Promise.all([
      request.get('/admin/visit/overview'),
      request.get('/admin/visit/trend', { params: { days: days.value } })
    ])
    overview.value = (overviewRes as { data: VisitOverviewData }).data
    trend.value = (trendRes as { data: VisitDailyItem[] }).data
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 某个数值在图表里的 y 坐标 */
function yOf(value: number) {
  const ratio = Math.min(1, Math.max(0, value / axis.value.max))
  return CHART.top + plotHeight.value * (1 - ratio)
}

/** 第 index 根柱子的中心 x 坐标 */
function centerX(index: number) {
  const slot = plotWidth.value / Math.max(1, trend.value.length)
  return CHART.left + slot * index + slot / 2
}

/** 只保留能整分的刻度，比如 3 档 → 1 / 2 / 5 / 10… */
function niceStep(raw: number) {
  if (raw <= 1) return 1
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const normalized = raw / magnitude
  const nice = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10
  return nice * magnitude
}

function round2(value: number) {
  return Math.round(value * 100) / 100
}

/** 2026-09-29 → 2026-09-29（空值给占位符，避免卡片上出现空白） */
function formatDate(date: string) {
  return date || '—'
}

/** 把增幅翻译成卡片上那行文案 + 配色 */
function describeGrowth(growth: number | null, delta: number) {
  if (growth === null) {
    return { text: '昨日没有数据，暂不对比', tone: 'none' as const }
  }
  if (growth === 0) {
    return { text: '与昨日持平', tone: 'flat' as const }
  }
  const percent = `${growth > 0 ? '+' : ''}${growth.toFixed(2)}%`
  const deltaText = `${delta > 0 ? '+' : ''}${delta}`
  return {
    text: `较昨日 ${percent}（${deltaText}）`,
    tone: growth > 0 ? ('up' as const) : ('down' as const)
  }
}

onMounted(loadData)
</script>

<template>
  <div class="admin-page">
    <header class="admin-page-header">
      <div>
        <h1 class="admin-page-title">访问统计</h1>
      </div>

      <div class="admin-page-actions">
        <el-button :loading="loading" @click="loadData">刷新</el-button>
      </div>
    </header>

    <section class="stat-grid" v-loading="loading">
      <article class="stat-card">
        <p class="stat-label">今日 PV</p>
        <p class="stat-value">{{ todayStats.pv }}</p>
        <p class="stat-delta" :class="`is-${pvGrowth.tone}`">{{ pvGrowth.text }}</p>
      </article>

      <article class="stat-card">
        <p class="stat-label">今日 UV</p>
        <p class="stat-value">{{ todayStats.uv }}</p>
        <p class="stat-delta" :class="`is-${uvGrowth.tone}`">{{ uvGrowth.text }}</p>
      </article>

      <article class="stat-card">
        <p class="stat-label">昨日 PV</p>
        <p class="stat-value">{{ yesterdayStats.pv }}</p>
        <p class="stat-hint">{{ formatDate(yesterdayStats.date) }}</p>
      </article>

      <article class="stat-card">
        <p class="stat-label">昨日 UV</p>
        <p class="stat-value">{{ yesterdayStats.uv }}</p>
        <p class="stat-hint">{{ formatDate(yesterdayStats.date) }}</p>
      </article>
    </section>

    <section class="admin-panel chart-panel">
      <header class="panel-header">
        <div>
          <h2 class="panel-title">最近 {{ days }} 天</h2>
          <p class="panel-subtitle">{{ rangeSummaryText }}</p>
        </div>

        <el-radio-group v-model="days" @change="loadData">
          <el-radio-button v-for="option in RANGE_OPTIONS" :key="option" :value="option">
            {{ option }} 天
          </el-radio-button>
        </el-radio-group>
      </header>

      <div class="chart-body" v-loading="loading">
        <svg
          class="chart"
          :viewBox="`0 0 ${CHART.width} ${CHART.height}`"
          role="img"
          aria-label="每日 PV 与 UV 趋势"
        >
          <g v-for="tick in yTicks" :key="`y-${tick.value}`">
            <line
              class="grid-line"
              :x1="CHART.left"
              :x2="CHART.width - CHART.right"
              :y1="tick.y"
              :y2="tick.y"
            />
            <text class="axis-label" :x="CHART.left - 10" :y="tick.y + 4" text-anchor="end">
              {{ tick.value }}
            </text>
          </g>

          <g v-for="label in xLabels" :key="`x-${label.index}`">
            <text class="axis-label" :x="label.x" :y="CHART.height - 10" text-anchor="middle">
              {{ label.text }}
            </text>
          </g>

          <rect
            v-for="bar in bars"
            :key="`bar-${bar.index}`"
            class="bar"
            :x="bar.x"
            :y="bar.y"
            :width="bar.width"
            :height="bar.height"
            rx="2"
          >
            <title>{{ bar.tooltip }}</title>
          </rect>

          <polyline class="uv-line" :points="uvPolyline" />

          <circle
            v-for="point in uvPoints"
            :key="`uv-${point.index}`"
            class="uv-dot"
            :cx="point.x"
            :cy="point.y"
            r="2.5"
          >
            <title>{{ point.tooltip }}</title>
          </circle>
        </svg>

        <div class="legend">
          <span class="legend-item"><i class="legend-bar" />PV（浏览量）</span>
          <span class="legend-item"><i class="legend-dot" />UV（访问人数）</span>
        </div>
      </div>
    </section>

    <section class="admin-panel table-panel">
      <header class="panel-header table-header">
        <div>
          <h2 class="panel-title">最近 {{ DETAIL_DAYS }} 天明细</h2>
          <p class="panel-subtitle">每天一行，PV 环比是相对前一天；图表切换天数不影响这里</p>
        </div>
      </header>

      <el-table v-loading="loading" :data="tableRows" style="width: 100%">
        <el-table-column prop="date" label="日期" min-width="150" />
        <el-table-column prop="pv" label="PV" min-width="110" />
        <el-table-column prop="uv" label="UV" min-width="110" />
        <el-table-column label="PV 环比" min-width="140">
          <template #default="{ row }">
            <span class="row-growth" :class="`is-${row.tone}`">{{ row.growthText }}</span>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
/* 今日 / 昨日四张卡片：每块内容各自一个框，不套外层框 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px;
}

.stat-card {
  padding: 16px 20px 18px;
  border: 1px solid var(--panel-border);
  border-radius: 22px;
  background: var(--panel-bg);
  box-shadow: var(--panel-shadow);
}

.stat-label {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.stat-value {
  margin: 8px 0 6px;
  color: var(--text-strong);
  font-size: 32px;
  font-weight: 600;
  line-height: 1.1;
}

.stat-delta,
.stat-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
}

.stat-hint {
  color: var(--text-muted);
}

.stat-delta.is-up,
.row-growth.is-up {
  color: var(--trend-up);
}

.stat-delta.is-down,
.row-growth.is-down {
  color: var(--trend-down);
}

.stat-delta.is-flat,
.row-growth.is-flat {
  color: var(--text-body);
}

.stat-delta.is-none,
.row-growth.is-none {
  color: var(--text-muted);
}

/* 趋势图面板 */
.chart-panel {
  padding: 18px 20px 12px;
}

.panel-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.panel-title {
  margin: 0 0 4px;
  color: var(--text-strong);
  font-size: 17px;
  font-weight: 600;
}

.panel-subtitle {
  margin: 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.chart-body {
  position: relative;
}

.chart {
  display: block;
  width: 100%;
  height: auto;
}

.grid-line {
  stroke: var(--panel-border);
  stroke-width: 1;
}

.axis-label {
  fill: var(--text-muted);
  font-size: 11px;
}

.bar {
  fill: var(--accent-brown-soft);
  opacity: 0.85;
}

.bar:hover {
  opacity: 1;
}

.uv-line {
  fill: none;
  stroke: var(--accent-brown);
  stroke-width: 2;
  stroke-linejoin: round;
  stroke-linecap: round;
}

.uv-dot {
  fill: var(--panel-bg);
  stroke: var(--accent-brown);
  stroke-width: 1.6;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin: 2px 0 6px;
  padding-left: 4px;
  color: var(--text-body);
  font-size: 12px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-bar {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  background: var(--accent-brown-soft);
}

.legend-dot {
  width: 12px;
  height: 12px;
  border: 2px solid var(--accent-brown);
  border-radius: 50%;
  background: var(--panel-bg);
}

/* 明细表 */
.table-panel {
  padding: 6px 6px 12px;
}

.table-header {
  padding: 12px 16px 6px;
  margin-bottom: 0;
}

.row-growth {
  font-size: 13px;
}
</style>
