import { onBeforeUnmount, onMounted, ref } from 'vue'

/** 自动保存的四种状态，页面按它显示提示文字 */
export type AutoSaveState = 'idle' | 'saving' | 'saved' | 'error'

export interface AutoSaveOptions {
  /** 真正落库的动作；抛错表示这次失败（会按 retries 重试，重试仍失败才算失败） */
  save: () => Promise<void>
  /** 改动停下来多久才保存（防抖），默认 1000ms */
  delay?: number
  /** 失败后的自动重试次数，默认 2（重试间隔 800ms、1600ms） */
  retries?: number
  /**
   * 返回 false 时不发请求、保留「有改动待保存」的状态，等下次触发再看。
   * 例：没进编辑态、标题为空、新建文章还没选分类。
   */
  enabled?: () => boolean
}

/** 重试的基准间隔，第 n 次重试等 800 * 2^(n-1) 毫秒 */
const RETRY_BASE_DELAY = 800

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * 把「防抖 + 串行 + 失败重试 + 状态 + 离开前 flush」收在一处，两个后台页共用。
 * 要点：
 * - 同一时间只发一个保存请求（单飞）；保存期间又改了内容，这一轮结束后立刻补一轮；
 * - 保存请求都带 silent，失败不弹 toast，由页面显示状态并提供重试；
 * - 页面切到后台标签页时自动 flush 一次。
 */
export function useAutoSave(options: AutoSaveOptions) {
  const delay = options.delay ?? 1000
  const retries = options.retries ?? 2

  const state = ref<AutoSaveState>('idle')
  const lastSavedAt = ref<Date | null>(null)
  /** 还有没有等待落库的改动（页面用它判断要不要拦离开） */
  const pending = ref(false)

  let timer: ReturnType<typeof setTimeout> | null = null
  let inFlight: Promise<boolean> | null = null
  /** 非响应式的同一份「有待保存改动」标记，给异步流程读写 */
  let pendingChanges = false
  /** 换文章 / 重置后用来丢弃旧请求的结果 */
  let token = 0

  function setPending(value: boolean) {
    pendingChanges = value
    pending.value = value
  }

  function clearTimer() {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  function isEnabled() {
    return options.enabled ? options.enabled() : true
  }

  /** 带重试地跑一次保存，返回是否成功 */
  async function attemptSave(): Promise<boolean> {
    for (let attempt = 0; ; attempt += 1) {
      try {
        await options.save()
        return true
      } catch {
        if (attempt >= retries) return false
        await sleep(RETRY_BASE_DELAY * 2 ** attempt)
      }
    }
  }

  /** 一轮保存：保存期间又出现改动就接着再存一次 */
  async function runLoop(myToken: number): Promise<boolean> {
    state.value = 'saving'
    for (;;) {
      setPending(false)
      const ok = await attemptSave()
      // 期间被 reset（换了文章）就丢弃这次结果，别去改新的页面状态
      if (myToken !== token) return true
      if (!ok) {
        setPending(true)
        state.value = 'error'
        return false
      }
      if (!pendingChanges) break
    }
    lastSavedAt.value = new Date()
    state.value = 'saved'
    return true
  }

  /** 有改动就排一次防抖保存（条件不满足时先记着，不发请求） */
  function schedule() {
    setPending(true)
    if (!isEnabled()) return
    clearTimer()
    timer = setTimeout(() => {
      void flush()
    }, delay)
  }

  /**
   * 取消防抖并立刻保存，返回「是否已经没有待保存的改动」。
   * 条件不满足（比如标题为空）或重试后仍失败时返回 false，调用方据此决定要不要拦用户。
   */
  async function flush(): Promise<boolean> {
    clearTimer()
    if (inFlight) {
      const ok = await inFlight
      if (!ok) return false
    }
    if (!pendingChanges) return true
    if (!isEnabled()) return false

    const myToken = token
    inFlight = runLoop(myToken)
    const ok = await inFlight
    inFlight = null
    return ok
  }

  /** 换了文章 / 手动保存过之后调用：清掉待保存标记和状态文字 */
  function reset() {
    token += 1
    clearTimer()
    inFlight = null
    setPending(false)
    state.value = 'idle'
    lastSavedAt.value = null
  }

  /** 页面切到后台标签页时尽量把内容落库（关标签页由页面自己的 beforeunload 拦） */
  function handleVisibilityChange() {
    if (document.visibilityState === 'hidden') {
      void flush()
    }
  }

  onMounted(() => document.addEventListener('visibilitychange', handleVisibilityChange))
  onBeforeUnmount(() => {
    document.removeEventListener('visibilitychange', handleVisibilityChange)
    token += 1
    clearTimer()
  })

  return { state, lastSavedAt, pending, schedule, flush, reset }
}

/** 状态文字：页面头部显示的「正在保存… / 已保存 15:32 / 保存失败 · 重试」 */
export function autoSaveStatusText(state: AutoSaveState, lastSavedAt: Date | null) {
  if (state === 'saving') return '正在保存…'
  if (state === 'error') return '保存失败 · 重试'
  if (state === 'saved' && lastSavedAt) {
    const hh = String(lastSavedAt.getHours()).padStart(2, '0')
    const mm = String(lastSavedAt.getMinutes()).padStart(2, '0')
    return `已保存 ${hh}:${mm}`
  }
  return ''
}
