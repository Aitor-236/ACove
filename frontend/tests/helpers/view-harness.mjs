import { createRequire } from 'node:module'
import { readFileSync } from 'node:fs'

const require = createRequire(new URL('../../package.json', import.meta.url))
export const vue = require('vue')
export const routerApi = require('vue-router')
const { parse, compileScript } = require('@vue/compiler-sfc')
const ts = require('typescript')
const { createRenderer, h, nextTick } = vue
const { createRouter, createMemoryHistory, RouterView } = routerApi
let nextModule = 0

export const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

export async function settle() {
  for (let i = 0; i < 16; i++) { await Promise.resolve(); await nextTick() }
}

export async function compileView(name, request, extraDependencies = {}) {
  const { descriptor } = parse(readFileSync(new URL(`../../src/views/${name}.vue`, import.meta.url), 'utf8'))
  const source = compileScript(descriptor, { id: `view-test-${nextModule}` }).content
  const key = `__viewTest${nextModule++}`
  globalThis[key] = { vue, router: routerApi, request, ...extraDependencies }
  const js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
  const mocked = js.replace(/import\s+([\s\S]*?)\s+from\s+['"]([^'"]+)['"];?/g, (_, bindings, specifier) => {
    const dep = specifier === 'vue' ? 'vue' : specifier === 'vue-router' ? 'router' : specifier.includes('request') ? 'request' : specifier
    const binding = bindings.startsWith('{') ? bindings.replace(/\bas\b/g, ':') : bindings
    return `const ${binding} = globalThis[${JSON.stringify(key)}][${JSON.stringify(dep)}] ?? {};`
  })
  const component = (await import(`data:text/javascript;base64,${Buffer.from(mocked).toString('base64')}`)).default
  component.render = () => null
  return component
}

const renderer = createRenderer({
  createElement: () => ({}), createText: () => ({}), createComment: () => ({}),
  insert() {}, remove() {}, setText() {}, setElementText() {}, patchProp() {},
  parentNode: () => null, nextSibling: () => null
})

export async function mountView(component, path, pattern, otherRoutes = []) {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: pattern, component },
    ...otherRoutes,
    { path: '/elsewhere', component: { render: () => null } }
  ] })
  await router.push(path)
  const app = renderer.createApp({ render: () => h(RouterView) })
  app.use(router)
  app.mount({})
  await settle()
  const state = app._instance.subTree.component.subTree.component.setupState
  return { router, state, close: () => app.unmount() }
}
