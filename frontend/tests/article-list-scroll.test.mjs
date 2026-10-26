import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

// Enable Vue Router's browser scroll lifecycle before loading Vue or the router.
process.env.NODE_ENV = 'production'
globalThis.document = {
  createElement() { return {} }, querySelector() { return null },
  documentElement: { style: { scrollBehavior: '' } }
}
globalThis.history = { state: { position: 0 } }
globalThis.location = { protocol: 'http:', host: 'localhost', pathname: '/', search: '', hash: '' }
globalThis.window = {
  scrollY: 0, scrollX: 0, history: globalThis.history,
  scrollTo(arg, y) { this.scrollY = typeof arg === 'object' ? arg.top : y }
}
globalThis.requestAnimationFrame = callback => { callback(); return 0 }
const { compileView, vue, routerApi, settle } = await import('./helpers/view-harness.mjs')

test('real router scrollBehavior cannot override a fast list response restoring history position', async () => {
  const require = createRequire(new URL('../package.json', import.meta.url))
  const ts = require('typescript')
  const source = ts.createSourceFile('router.ts', readFileSync(new URL('../src/router/index.ts', import.meta.url), 'utf8'), ts.ScriptTarget.Latest, true)
  let method
  function visit(node) {
    if (ts.isMethodDeclaration(node) && node.name.getText(source) === 'scrollBehavior') method = node
    ts.forEachChild(node, visit)
  }
  visit(source)
  assert.ok(method)
  const scrollBehavior = new Function(`return ({${method.getText(source)}}).scrollBehavior`)()
  const component = await compileView('Articles', { async get(url) {
    return url === '/article/list' ? { data: { records: [{ id: 1 }], total: 30 } } : { data: [] }
  } })
  const { createRouter, createMemoryHistory, RouterView } = routerApi
  const router = createRouter({ history: createMemoryHistory(), scrollBehavior, routes: [
    { path: '/articles', component },
    { path: '/articles/:id', component: { render: () => null } }
  ] })
  const renderer = vue.createRenderer({
    createElement: () => ({}), createText: () => ({}), createComment: () => ({}),
    insert() {}, remove() {}, setText() {}, setElementText() {}, patchProp() {},
    parentNode: () => null, nextSibling: () => null
  })
  await router.push('/articles?page=3')
  const app = renderer.createApp({ render: () => vue.h(RouterView) })
  app.use(router)
  app.mount({})
  await settle()
  window.scrollY = 640
  await router.push('/articles/1')
  await settle()
  router.back()
  await settle()
  assert.equal(window.scrollY, 640)
  assert.equal(router.currentRoute.value.query.page, '3')
  app.unmount()
})
