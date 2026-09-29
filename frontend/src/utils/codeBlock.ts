/**
 * 代码组（多语言切换）的交互。
 *
 * 正文是 marked 渲染 + DOMPurify 清洗后的静态 HTML，再交给 v-html 挂到页面上，
 * 没法在里面直接写 Vue 组件，所以这里用事件委托：把 click / keydown 挂在
 * 持久存在的 .markdown-body 容器上，按 data-tab-index 切换标签和面板。
 */

/** 当前是不是代码组的标签 */
function findTab(target: EventTarget | null): HTMLElement | null {
  return (target as HTMLElement | null)?.closest?.('.code-group-tab') ?? null
}

/** 把某个标签设为选中：同步 is-active、aria-selected、tabindex 和面板显隐。 */
function activateTab(tab: HTMLElement) {
  const group = tab.closest('.code-group')
  const index = tab.dataset.tabIndex
  if (!group || index === undefined) return

  group.querySelectorAll<HTMLElement>('.code-group-tab').forEach((item) => {
    const active = item === tab
    item.classList.toggle('is-active', active)
    item.setAttribute('aria-selected', String(active))
    item.tabIndex = active ? 0 : -1
  })

  group.querySelectorAll<HTMLElement>('.code-group-panel').forEach((panel) => {
    panel.classList.toggle('is-active', panel.dataset.tabIndex === index)
  })
}

export function handleCodeGroupClick(event: MouseEvent) {
  const tab = findTab(event.target)
  if (!tab) return
  activateTab(tab)
}

/** 标签栏的键盘操作：左右方向键切换，Home / End 跳首尾（标准的 tablist 行为）。 */
export function handleCodeGroupKeydown(event: KeyboardEvent) {
  const tab = findTab(event.target)
  if (!tab) return

  const tabs = Array.from(tab.parentElement?.querySelectorAll<HTMLElement>('.code-group-tab') ?? [])
  const current = tabs.indexOf(tab)
  if (current < 0 || tabs.length < 2) return

  let next = current
  if (event.key === 'ArrowRight') next = (current + 1) % tabs.length
  else if (event.key === 'ArrowLeft') next = (current - 1 + tabs.length) % tabs.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = tabs.length - 1
  else return

  event.preventDefault()
  const target = tabs[next]
  if (!target) return
  activateTab(target)
  target.focus()
}
