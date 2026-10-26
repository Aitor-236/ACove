import assert from 'node:assert/strict'
import { test } from 'node:test'
import { compileView, mountView, settle } from './helpers/view-harness.mjs'

globalThis.window = { scrollY: 0, scrollTo({ top }) { this.scrollY = top } }
globalThis.requestAnimationFrame = callback => { callback(); return 0 }

async function setup(path) {
  const calls = []
  const component = await compileView('Articles', { async get(url, config) {
    if (url !== '/article/list') return { data: [] }
    const params = { ...config.params }
    calls.push(params)
    return { data: { records: [{ id: params.page, categorySlug: params.category }], total: 30 } }
  } })
  const context = await mountView(component, path, '/articles', [
    { path: '/articles/:id', component: { render: () => null } }
  ])
  return { ...context, get state() { return context.state }, calls }
}

test('direct URL and refresh restore category, tag, keyword and page', async () => {
  const path = '/articles?category=second&tag=Vue&keyword=Vue%203&page=3'
  for (let attempt = 0; attempt < 2; attempt++) {
    const ctx = await setup(path)
    assert.equal(ctx.state.activeCategorySlug, 'second')
    assert.equal(ctx.state.activeTagName, 'Vue')
    assert.equal(ctx.state.keywordInput, 'Vue 3')
    assert.equal(ctx.state.page, 3)
    assert.equal(ctx.calls.length, 1)
    assert.deepEqual(ctx.calls[0], { page: 3, size: 6, category: 'second', tag: 'Vue', keyword: 'Vue 3' })
    ctx.close()
  }
})

test('return from detail reloads the same list page before restoring its own scroll position', async () => {
  const ctx = await setup('/articles?keyword=Vue&page=3')
  window.scrollY = 640
  await ctx.router.push('/articles/18')
  window.scrollY = 0
  ctx.router.back()
  await new Promise(resolve => setImmediate(resolve))
  await settle()
  assert.equal(ctx.state.page, 3)
  assert.equal(ctx.state.keywordInput, 'Vue')
  assert.equal(ctx.calls.at(-1).page, 3)
  assert.equal(window.scrollY, 640)
  ctx.state.selectCategory('another')
  await settle()
  assert.equal(ctx.state.page, 1)
  assert.equal(ctx.router.currentRoute.value.query.page, undefined)
  assert.equal(window.scrollY, 0, 'new filters do not inherit the old list scroll')
  ctx.close()
})

test('search and paging create navigable URLs and removed query parameters clear old filters', async () => {
  const ctx = await setup('/articles?category=one&tag=Vue')
  ctx.state.keywordInput = '  状态恢复  '
  ctx.state.applySearch()
  await settle()
  assert.equal(ctx.router.currentRoute.value.query.keyword, '状态恢复')
  ctx.state.changePage(2)
  await settle()
  assert.equal(ctx.router.currentRoute.value.query.page, '2')
  ctx.router.back()
  await new Promise(resolve => setImmediate(resolve))
  await settle()
  assert.equal(ctx.state.page, 1)
  assert.equal(ctx.state.appliedKeyword, '状态恢复')
  await ctx.router.push('/articles')
  await settle()
  assert.equal(ctx.state.activeCategorySlug, '')
  assert.equal(ctx.state.activeTagName, '')
  assert.equal(ctx.state.keywordInput, '')
  assert.equal(ctx.state.page, 1)
  ctx.close()
})

test('invalid pages default to one and pages beyond the remaining results are corrected', async () => {
  for (const value of ['-1', '0', '2.5', 'oops', '9007199254740992']) {
    const ctx = await setup(`/articles?page=${value}`)
    assert.equal(ctx.state.page, 1)
    assert.equal(ctx.calls[0].page, 1)
    ctx.close()
  }
  const ctx = await setup('/articles?category=one&keyword=test&page=99')
  assert.equal(ctx.state.page, 5)
  assert.equal(ctx.router.currentRoute.value.query.page, '5')
  assert.equal(ctx.calls.at(-1).page, 5)
  assert.equal(ctx.router.currentRoute.value.query.keyword, 'test')
  ctx.close()
})

test('submitting the same search retries a transient failure without changing the URL', async () => {
  let failing = true
  let attempts = 0
  const component = await compileView('Articles', { async get(url) {
    if (url !== '/article/list') return { data: [] }
    attempts++
    if (failing) throw Error('temporary failure')
    return { data: { records: [{ id: 1 }], total: 1 } }
  } })
  const ctx = await mountView(component, '/articles', '/articles')
  assert.ok(ctx.state.errorMessage)
  failing = false
  ctx.state.applySearch()
  await settle()
  assert.equal(attempts, 2)
  assert.equal(ctx.state.errorMessage, '')
  assert.equal(ctx.state.articles[0].id, 1)
  assert.equal(ctx.router.currentRoute.value.fullPath, '/articles')
  ctx.close()
})
