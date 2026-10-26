// Run: node --test frontend/tests/session-revocation.test.mjs
// Exercise the real profile SFC with real Vue and Vue Router; API and storage are isolated.
import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import { test } from 'node:test'
const require = createRequire(new URL('../package.json', import.meta.url))
const vue = require('vue')
const routerApi = require('vue-router')
const { parse, compileScript } = require('@vue/compiler-sfc')
const ts = require('typescript')
const calls = []
const messages = []
let fail = false
const values = new Map()
globalThis.localStorage = {
  getItem: key => values.get(key) ?? null,
  setItem: (key, value) => values.set(key, value),
  removeItem: key => values.delete(key)
}
globalThis.window = { dispatchEvent() {} }
const request = {
  async get() { return { data: { id: 7, username: 'alice', email: 'alice@example.com', avatar: '' } } },
  async post(url, payload) {
    calls.push([url, { ...payload }])
    if (fail) throw Error('wrong password')
    return { data: null }
  }
}
globalThis.__sessionTest = {
  vue, router: routerApi, request,
  element: { ElMessage: { success: message => messages.push(message) } }
}
const { descriptor } = parse(readFileSync(new URL('../src/views/admin/AdminProfile.vue', import.meta.url), 'utf8'))
const source = compileScript(descriptor, { id: 'session-test' }).content
const js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
const mocked = js.replace(/import\s+([\s\S]*?)\s+from\s+['"]([^'"]+)['"];?/g, (_, bindings, specifier) => {
  const dep = specifier === 'vue' ? 'vue' : specifier === 'vue-router' ? 'router' : specifier === 'element-plus' ? 'element' : 'request'
  return `const ${bindings.startsWith('{') ? bindings.replace(/\bas\b/g, ':') : bindings} = globalThis.__sessionTest.${dep};`
})
const Profile = (await import(`data:text/javascript;base64,${Buffer.from(mocked).toString('base64')}`)).default
Profile.render = () => null
const renderer = vue.createRenderer({
  createElement: () => ({}), createText: () => ({}), createComment: () => ({}),
  insert() {}, remove() {}, setText() {}, setElementText() {}, patchProp() {},
  parentNode: () => null, nextSibling: () => null
})
const settle = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await vue.nextTick() } }
async function mount() {
  values.clear(); calls.length = 0; messages.length = 0; fail = false
  const router = routerApi.createRouter({ history: routerApi.createMemoryHistory(), routes: [
    { path: '/admin/profile', component: Profile },
    { path: '/login', component: { render: () => null } }
  ] })
  await router.push('/admin/profile?section=password#form')
  const app = renderer.createApp({ render: () => vue.h(routerApi.RouterView) })
  app.use(router); app.mount({}); await settle()
  values.set('token', 'old-token'); values.set('siteName', 'Custom site')
  const state = app._instance.subTree.component.subTree.component.setupState
  state.passwordFormRef = { validate: async () => true, clearValidate() {} }
  state.passwordForm = { currentPassword: 'old', newPassword: 'new', confirmPassword: 'new' }
  return { router, state, close: () => app.unmount() }
}
test('successful password change clears session and redirects through login with return location', async () => {
  const ctx = await mount()
  await ctx.state.changePassword(); await settle()
  assert.deepEqual(calls, [['/admin/user/password', { currentPassword: 'old', newPassword: 'new' }]])
  for (const key of ['token', 'username', 'email']) assert.equal(values.has(key), false)
  assert.equal(values.get('siteName'), 'Custom site')
  assert.equal(ctx.router.currentRoute.value.path, '/login')
  assert.equal(ctx.router.currentRoute.value.query.redirect, '/admin/profile?section=password#form')
  assert.equal(messages.length, 1)
  assert.equal(ctx.state.savingPassword, false)
  ctx.close()
})
test('failed password change preserves token, form and current route', async () => {
  const ctx = await mount(); fail = true
  await ctx.state.changePassword()
  assert.equal(values.get('token'), 'old-token')
  assert.equal(ctx.state.passwordForm.currentPassword, 'old')
  assert.equal(ctx.router.currentRoute.value.path, '/admin/profile')
  assert.equal(messages.length, 0)
  assert.equal(ctx.state.savingPassword, false)
  ctx.close()
})
test('invalid form never calls password endpoint or clears the session', async () => {
  const ctx = await mount()
  ctx.state.passwordFormRef.validate = async () => false
  await ctx.state.changePassword()
  assert.equal(calls.length, 0); assert.equal(values.get('token'), 'old-token')
  assert.equal(ctx.router.currentRoute.value.path, '/admin/profile')
  ctx.close()
})

// Exercise the real axios response interceptor with isolated router and storage.
let rejectResponse
const mockRouter = {
  currentRoute: { value: { path: '/admin/profile', fullPath: '/admin/profile?section=password#form' } },
  replace: target => { calls.push(target); return Promise.resolve() }
}
globalThis.__requestSessionTest = {
  axios: { create: () => ({ interceptors: {
    request: { use() {} }, response: { use(_success, rejected) { rejectResponse = rejected } }
  } }) },
  element: { ElMessage: { error: message => messages.push(message) } },
  router: mockRouter
}
const requestSource = readFileSync(new URL('../src/utils/request.ts', import.meta.url), 'utf8')
const requestJs = ts.transpileModule(requestSource, { compilerOptions: {
  target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext
} }).outputText.replace(/import\s+([\s\S]*?)\s+from\s+['"]([^'"]+)['"];?/g, (_, bindings, specifier) => {
  const dep = specifier === 'axios' ? 'axios' : specifier === 'element-plus' ? 'element' : 'router'
  return `const ${bindings.startsWith('{') ? bindings.replace(/\bas\b/g, ':') : bindings} = globalThis.__requestSessionTest.${dep};`
})
await import(`data:text/javascript;base64,${Buffer.from(requestJs).toString('base64')}`)
test('revoked session HTTP 401 clears identity and keeps the original full route', async () => {
  values.set('token', 'old'); values.set('username', 'alice'); values.set('email', 'alice@example.com')
  calls.length = 0
  const error = { response: { status: 401 } }
  await assert.rejects(rejectResponse(error), value => value === error)
  for (const key of ['token', 'username', 'email']) assert.equal(values.has(key), false)
  assert.deepEqual(calls, [{ path: '/login', query: { redirect: '/admin/profile?section=password#form' } }])
})
test('a delayed 401 received on login preserves its existing redirect', async () => {
  calls.length = 0
  mockRouter.currentRoute.value = { path: '/login', fullPath: '/login?redirect=%2Fadmin%2Fprofile' }
  await assert.rejects(rejectResponse({ response: { status: 401 } }))
  assert.equal(calls.length, 0)
})
