// Run: node --test frontend/tests/login-site-branding.test.mjs
// Compile and render the real Login SFC and shared settings with an isolated router/API.
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import { test } from 'node:test'
const require = createRequire(new URL('../package.json', import.meta.url))
const vue = require('vue')
const routerApi = require('vue-router')
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const ts = require('typescript')
const { h, createRenderer, nextTick } = vue
const { createRouter, createMemoryHistory, RouterView } = routerApi
const storage = new Map()
globalThis.localStorage = { setItem: (key, value) => storage.set(key, value) }
globalThis.document = { querySelector: () => null }
let settingsRequest
const loginCalls = []
const request = {
  get(url) { assert.equal(url, '/site/settings'); return settingsRequest },
  async post(url, payload) {
    loginCalls.push([url, payload])
    return { code: 200, data: { token: 'test-token', username: 'tester', email: 'test@example.com' } }
  }
}
globalThis.__loginTest = { vue, router: routerApi, request, element: { ElMessage: { success() {} } } }
async function compile(source) {
  const js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
  const mocked = js.replace(/import\s+([\s\S]*?)\s+from\s+['"]([^'"]+)['"];?/g, (_, bindings, specifier) => {
    const key = specifier === 'vue' ? 'vue' : specifier === 'vue-router' ? 'router'
      : specifier === 'element-plus' ? 'element' : specifier.split('/').at(-1)
    return `const ${bindings.startsWith('{') ? bindings.replace(/\bas\b/g, ':') : bindings} = globalThis.__loginTest[${JSON.stringify(key)}];`
  })
  return import(`data:text/javascript;base64,${Buffer.from(mocked).toString('base64')}`)
}
globalThis.__loginTest.useSiteSettings = await compile(readFileSync(new URL('../src/composables/useSiteSettings.ts', import.meta.url), 'utf8'))
const { descriptor } = parse(readFileSync(new URL('../src/views/Login.vue', import.meta.url), 'utf8'))
const script = compileScript(descriptor, { id: 'login-branding-test' })
const Login = (await compile(script.content)).default
Login.render = (await compile(compileTemplate({
  source: descriptor.template.content, filename: 'Login.vue', id: 'login-branding-test',
  compilerOptions: { bindingMetadata: script.bindings }
}).code)).render
const node = (tag, text = '') => ({ tag, text, children: [], parent: null })
const renderer = createRenderer({
  createElement: tag => node(tag), createText: text => node('#text', text), createComment: text => node('#comment', text),
  insert(child, parent, anchor) {
    if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1)
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, child)
    child.parent = parent
  },
  remove(child) { if (child.parent) child.parent.children.splice(child.parent.children.indexOf(child), 1) },
  setText: (child, text) => { child.text = text },
  setElementText: (child, text) => { child.text = text; child.children = [] },
  patchProp: (child, key, _old, value) => { child[key] = value },
  parentNode: child => child.parent,
  nextSibling: child => child.parent?.children[child.parent.children.indexOf(child) + 1] ?? null
})
const settle = async () => {
  await new Promise(resolve => setImmediate(resolve))
  for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick() }
}
const find = (root, predicate) => predicate(root) ? root : root.children.map(child => find(child, predicate)).find(Boolean)
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
async function mount(query = '') {
  const pending = deferred()
  settingsRequest = pending.promise
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/login', component: Login }, { path: '/admin', component: { render: () => null } },
    { path: '/admin/articles', component: { render: () => null } }
  ] })
  await router.push('/login' + query)
  const root = node('root')
  const app = renderer.createApp({ render: () => h(RouterView) })
  const form = { setup(_props, { slots, expose }) { expose({ validate: async () => true }); return () => h('form', slots.default?.()) } }
  const slot = { setup: (_props, { slots }) => () => h('div', slots.default?.()) }
  app.component('el-form', form)
  for (const name of ['el-form-item', 'el-input', 'el-button']) app.component(name, slot)
  app.use(router); app.mount(root); await settle()
  return {
    root, router, pending, close: () => app.unmount(),
    state: app._instance.subTree.component.subTree.component.setupState,
    heading: () => find(root, child => child.tag === 'h1')?.text,
    logo: () => find(root, child => child.class === 'logo')?.text
  }
}
const settings = siteName => ({ data: { siteName, siteIcon: '', heroText: '', heroImage: '' } })

test('first-load settings failure keeps the default visible login brand', async () => {
  const ctx = await mount()
  ctx.pending.reject(Error('offline')); await settle()
  assert.equal(ctx.heading(), '登录 ACove'); assert.equal(ctx.logo(), 'A')
  ctx.close()
})

test('slow settings load renders the custom Chinese name and first character', async () => {
  const ctx = await mount()
  assert.equal(ctx.heading(), '登录 ACove')
  ctx.pending.resolve(settings('枫叶小站')); await settle()
  assert.equal(ctx.heading(), '登录 枫叶小站'); assert.equal(ctx.logo(), '枫')
  ctx.close()
})

test('English custom name uses an uppercase initial and failure preserves the last known brand', async () => {
  let ctx = await mount()
  assert.equal(ctx.heading(), '登录 枫叶小站')
  ctx.pending.resolve(settings('maple notes')); await settle()
  assert.equal(ctx.heading(), '登录 maple notes'); assert.equal(ctx.logo(), 'M')
  ctx.close()
  ctx = await mount()
  ctx.pending.reject(Error('offline')); await settle()
  assert.equal(ctx.heading(), '登录 maple notes'); assert.equal(ctx.logo(), 'M')
  ctx.close()
})

test('email login still stores the session and navigates to the requested redirect', async () => {
  const ctx = await mount('?redirect=/admin/articles')
  ctx.pending.resolve(settings('登录测试站')); await settle()
  Object.assign(ctx.state.loginForm, { account: ' user@example.com ', password: 'password-test' })
  await ctx.state.handleLogin(); await settle()
  assert.deepEqual(loginCalls.at(-1), ['/auth/login', { email: 'user@example.com', password: 'password-test' }])
  assert.equal(storage.get('token'), 'test-token'); assert.equal(storage.get('username'), 'tester')
  assert.equal(storage.get('email'), 'test@example.com')
  assert.equal(ctx.router.currentRoute.value.path, '/admin/articles')
  ctx.close()
})

test('username login keeps its payload and default admin destination', async () => {
  const ctx = await mount()
  ctx.pending.resolve(settings('登录测试站')); await settle()
  Object.assign(ctx.state.loginForm, { account: ' tester ', password: 'password-test' })
  await ctx.state.handleLogin(); await settle()
  assert.deepEqual(loginCalls.at(-1), ['/auth/login', { username: 'tester', password: 'password-test' }])
  assert.equal(ctx.router.currentRoute.value.path, '/admin')
  ctx.close()
})
