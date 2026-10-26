// Run: node --test frontend/tests/article-editor-state.test.mjs
// Compile the real SFC script and mount it with Vue Router; no DOM or backend required.
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import { test } from 'node:test'
const require = createRequire(new URL('../package.json', import.meta.url))
const vue = require('vue')
const routerApi = require('vue-router')
const { parse, compileScript } = require('@vue/compiler-sfc')
const ts = require('typescript')
const { nextTick, h, createRenderer } = vue
const { createRouter, createMemoryHistory, RouterView } = routerApi
const calls = []
let confirm = true
let failUnpublish = false
let delayed = null
let delayedUnpublish = null
const detail = (id) => ({ id: Number(id), title: `Article ${id}`, summary: 'Summary', content: 'Content', categoryId: 10, categoryName: 'Original', categorySlug: 'original', tags: ['old'], status: 'published', createdAt: '2026-10-01', updatedAt: '2026-10-02', publishedAt: '2026-10-02' })
const request = {
  async get(url, config) {
    calls.push(['get', url, config])
    if (url === '/category/list') return { data: [
      { id: 10, name: 'Original', slug: 'original', articleCount: 1 },
      { id: 20, name: 'Original', slug: 'another-original', articleCount: 1 }
    ] }
    if (url === '/admin/article/list') return { data: { records: [], total: 0 } }
    if (url === '/admin/tag/list') return { data: { records: [] } }
    if (delayed && url === '/admin/article/1') return delayed.promise
    return { data: detail(url.split('/').at(-1)) }
  },
  async post(url, payload, config) {
    calls.push(['post', url, JSON.parse(JSON.stringify(payload)), config])
    if (url.endsWith('/create')) return { data: { id: 99 } }
    if (url.endsWith('/unpublish')) {
      if (failUnpublish) throw Error('simulated failure')
      if (delayedUnpublish) return delayedUnpublish
      return { data: { status: 'draft', updatedAt: '2026-10-26', publishedAt: null } }
    }
    return { data: { updatedAt: '2026-10-26' } }
  }
}
globalThis.window = { addEventListener() {}, removeEventListener() {} }
globalThis.document = { addEventListener() {}, removeEventListener() {} }
globalThis.__editorTest = {
  vue, router: routerApi, request,
  element: { ElMessage: { error() {}, success() {}, warning() {} }, ElMessageBox: { confirm: async () => { if (!confirm) throw Error('cancel') } } }
}
async function compile(source) {
  const js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
  const mocked = js.replace(/import\s+([\s\S]*?)\s+from\s+['"]([^'"]+)['"];?/g, (_, bindings, specifier) => {
    const dep = specifier === 'vue' ? 'vue' : specifier === 'vue-router' ? 'router' : specifier === 'element-plus' ? 'element' : specifier.includes('useAutoSave') ? 'autoSave' : specifier.includes('request') ? 'request' : null
    if (!dep) return `const ${bindings.startsWith('{') ? bindings.replace(/\bas\b/g, ':') : bindings} = {};`
    return `const ${bindings.startsWith('{') ? bindings.replace(/\bas\b/g, ':') : bindings} = globalThis.__editorTest.${dep};`
  })
  return import(`data:text/javascript;base64,${Buffer.from(mocked).toString('base64')}`)
}
globalThis.__editorTest.autoSave = await compile(readFileSync(new URL('../src/composables/useAutoSave.ts', import.meta.url), 'utf8'))
const { descriptor } = parse(readFileSync(new URL('../src/views/admin/AdminArticleEdit.vue', import.meta.url), 'utf8'))
const Editor = (await compile(compileScript(descriptor, { id: 'article-editor-test' }).content)).default
Editor.render = () => null
const renderer = createRenderer({
  createElement: () => ({}), createText: () => ({}), createComment: () => ({}),
  insert() {}, remove() {}, setText() {}, setElementText() {}, patchProp() {},
  parentNode: () => null, nextSibling: () => null
})
const settle = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick() } }
async function mount(path = '/articles/1/edit') {
  calls.length = 0; confirm = true; failUnpublish = false
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/articles/new', component: Editor },
    { path: '/articles/:id/edit', component: Editor },
    { path: '/admin/articles/:id/edit', component: Editor },
    { path: '/admin/articles', component: { render: () => null } }
  ] })
  await router.push(path)
  const root = { render: () => h(RouterView) }
  const app = renderer.createApp(root)
  app.use(router); app.mount({}); await settle()
  const state = () => app._instance.subTree.component.subTree.component.setupState
  return { router, state, close: () => app.unmount() }
}

test('existing → new resets identity, fields and state; saving creates another article', async () => {
  const ctx = await mount()
  const instance = ctx.state()
  await ctx.router.push('/articles/new'); await settle()
  const s = ctx.state()
  assert.equal(s, instance, 'the same component must be reused')
  assert.equal(s.articleId, null); assert.equal(s.status, 'draft'); assert.equal(s.editing, true)
  assert.deepEqual({ ...s.form }, { title: '', summary: '', content: '', categoryId: null, tags: [] })
  assert.deepEqual({ ...s.meta }, { createdAt: '', updatedAt: '', publishedAt: '' })
  assert.equal(s.dirty, false); assert.equal(s.metaDirty, false); assert.equal(s.autoSavePending, false)
  s.formRef = { validate: async () => true, clearValidate() {} }
  Object.assign(s.form, { title: 'New', categoryId: 20, tags: ['new'] })
  await nextTick(); await s.saveDraft(); await settle()
  assert.equal(s.articleId, 99); assert.equal(s.form.title, 'New'); assert.equal(s.editing, true)
  assert.equal(ctx.router.currentRoute.value.path, '/admin/articles/99/edit')
  assert.equal(calls.filter(c => c[1] === '/admin/article/create').length, 1)
  assert.ok(calls.filter(c => c[1] === '/admin/article/update').every(c => c[2].id === 99))
  ctx.close()
})

test('cancelled navigation keeps unsaved metadata; confirmed switching loads the next article', async () => {
  const ctx = await mount(); const s = ctx.state(); s.startEditing()
  s.form.categoryId = 20; s.form.tags = ['unsaved']; await nextTick()
  confirm = false; await ctx.router.push('/articles/2/edit'); await settle()
  assert.equal(s.articleId, 1); assert.equal(s.form.categoryId, 20); assert.equal(s.metaDirty, true)
  confirm = true; await ctx.router.push('/articles/2/edit'); await settle()
  assert.equal(s.articleId, 2); assert.equal(s.form.title, 'Article 2'); assert.equal(s.editing, false)
  ctx.router.back(); await new Promise(resolve => setImmediate(resolve)); await settle()
  assert.equal(s.articleId, 1)
  ctx.router.forward(); await new Promise(resolve => setImmediate(resolve)); await settle()
  assert.equal(s.articleId, 2)
  ctx.close()
})

test('late detail success or failure cannot overwrite or redirect the new page', async () => {
  for (const rejectOld of [false, true]) {
    let resolve, reject
    delayed = { promise: new Promise((yes, no) => { resolve = yes; reject = no }) }
    const ctx = await mount()
    await ctx.router.push('/articles/new'); await settle()
    if (rejectOld) reject(Error('old failure')); else resolve({ data: detail(1) })
    await settle()
    assert.equal(ctx.state().articleId, null); assert.equal(ctx.state().form.title, '')
    assert.equal(ctx.state().hydrating, false); assert.equal(ctx.router.currentRoute.value.path, '/articles/new')
    ctx.close(); delayed = null
  }
})

test('unpublish preserves text, metadata, pending changes and manual-save behavior', async () => {
  const ctx = await mount(); const s = ctx.state(); s.startEditing()
  Object.assign(s.form, { categoryId: 20, tags: ['unsaved'], title: 'Changed text' })
  await nextTick()
  const before = JSON.parse(JSON.stringify(s.form))
  const reads = calls.filter(c => c[0] === 'get').length
  await s.unpublishArticle()
  assert.deepEqual({ ...s.form }, before); assert.equal(s.status, 'draft'); assert.equal(s.editing, true)
  assert.equal(s.meta.publishedAt, ''); assert.equal(s.meta.updatedAt, '2026-10-26')
  assert.equal(s.dirty, true); assert.equal(s.metaDirty, true); assert.equal(s.autoSavePending, true)
  assert.equal(calls.filter(c => c[0] === 'get').length, reads)
  assert.equal(s.savedMeta.categoryId, 10)
  s.formRef = { validate: async () => true }
  await s.saveDraft()
  const update = calls.filter(c => c[1] === '/admin/article/update').at(-1)
  assert.equal(update[2].id, 1); assert.equal(update[2].categoryId, 20)
  assert.deepEqual(update[2].tags, ['unsaved']); assert.equal(s.metaDirty, false)
  ctx.close()
})

test('cancelled or failed unpublish preserves content and published state', async () => {
  for (const cancelled of [true, false]) {
    const ctx = await mount(); const s = ctx.state(); s.startEditing()
    s.form.tags = ['unsaved']; await nextTick()
    confirm = !cancelled; failUnpublish = !cancelled
    await s.unpublishArticle()
    assert.equal(s.status, 'published'); assert.deepEqual([...s.form.tags], ['unsaved'])
    assert.equal(s.dirty, true); assert.equal(s.metaDirty, true); assert.equal(s.unpublishing, false)
    assert.equal(calls.filter(c => c[1] === '/admin/article/unpublish').length, cancelled ? 0 : 1)
    ctx.close()
  }
})


test('late unpublish response cannot change the next article', async () => {
  const ctx = await mount(); const s = ctx.state()
  let resolve
  delayedUnpublish = new Promise(yes => { resolve = yes })
  const operation = s.unpublishArticle()
  await settle()
  await ctx.router.push('/articles/2/edit'); await settle()
  resolve({ data: { status: 'draft', updatedAt: 'old response', publishedAt: null } })
  await operation
  assert.equal(s.articleId, 2); assert.equal(s.status, 'published')
  assert.equal(s.meta.updatedAt, '2026-10-02'); assert.equal(s.unpublishing, false)
  delayedUnpublish = null; ctx.close()
})


test('same-name categories remain distinct by ID in selection, dirty state and save payload', async () => {
  const ctx = await mount(); const s = ctx.state()
  assert.equal(s.form.categoryId, 10); assert.equal(s.metaDirty, false)
  assert.equal(s.categoryLabel(s.categories[0]), 'Original（original）')
  assert.equal(s.categoryLabel(s.categories[1]), 'Original（another-original）')
  s.startEditing(); s.form.categoryId = 20; await nextTick()
  assert.equal(s.metaDirty, true); assert.equal(s.selectedCategoryName, 'Original')
  s.formRef = { validate: async () => true }
  await s.saveDraft()
  const payload = calls.filter(c => c[1] === '/admin/article/update').at(-1)[2]
  assert.equal(payload.categoryId, 20); assert.equal('categoryName' in payload, false)
  assert.equal(s.savedMeta.categoryId, 20); assert.equal(s.metaDirty, false)
  ctx.close()
})


test('admin article category chips and filters target the exact same-name category slug', async () => {
  const { descriptor } = parse(readFileSync(new URL('../src/views/admin/AdminArticles.vue', import.meta.url), 'utf8'))
  const List = (await compile(compileScript(descriptor, { id: 'admin-article-list-test' }).content)).default
  List.render = () => null
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/admin/articles', component: List }] })
  await router.push('/admin/articles')
  const app = renderer.createApp({ render: () => h(RouterView) })
  app.use(router); app.mount({}); await settle()
  const s = app._instance.subTree.component.subTree.component.setupState
  assert.equal(s.categoryLabel(s.categories[1]), 'Original（another-original）')
  s.filterByCategory({ categoryName: 'Original', categorySlug: 'another-original' })
  await new Promise(resolve => setImmediate(resolve)); await settle()
  assert.equal(s.categorySlug, 'another-original')
  assert.equal(router.currentRoute.value.query.category, 'another-original')
  assert.equal(calls.filter(c => c[1] === '/admin/article/list').at(-1)[2].params.category, 'another-original')
  app.unmount()
})
