// Run: node --test frontend/tests/page-metadata.test.mjs
// Mount real App/detail scripts with Vue Router and a minimal document head.
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
const nodes = []
const find = (selector) => {
  const [, tag, key, , value] = /^(meta|link)\[([^=~]+)(~?)="([^\"]+)"\]$/.exec(selector) || []
  return nodes.find(node => node.tag === tag && node[key] === value) || null
}
const initial = { tag: 'meta', property: 'og:site_name', content: '来自服务端的站名' }
nodes.push(initial)
globalThis.document = {
  title: '服务器文章 | 来自服务端的站名',
  querySelector: find,
  head: { querySelector: find, append: node => nodes.push(node) },
  createElement: tag => ({ tag, setAttribute(k, v) { this[k] = v }, remove() { nodes.splice(nodes.indexOf(this), 1) } })
}
globalThis.window = { location: { origin: 'https://example.com' }, addEventListener() {}, removeEventListener() {} }
let resolveSettings
let settingsPromise = new Promise(resolve => { resolveSettings = resolve })
let failArticle = false
const request = { get: async (url) => {
  if (url === '/site/settings') return settingsPromise
  if (url.startsWith('/article/detail/')) {
    if (failArticle) throw Error('not public')
    return { data: { id: 2, title: '文章标题', summary: '文章摘要', content: '' } }
  }
  if (url.startsWith('/gallery/detail/')) return { data: {
    id: 3, name: '图库标题', description: '图库摘要', coverImage: '/uploads/gallery/photo.webp', images: []
  } }
  throw Error('unexpected request ' + url)
} }
globalThis.__metadataTest = { vue, router: routerApi, request,
  element: {}, icons: {}, markdown: {}, codeBlock: {}, markdownImages: {}, imageUrls: {} }
async function compile(source) {
  const js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
  const mocked = js.replace(/import\s+([\s\S]*?)\s+from\s+['"]([^'"]+)['"];?/g, (_, bindings, specifier) => {
    const key = specifier === 'vue' ? 'vue' : specifier === 'vue-router' ? 'router'
      : specifier === 'element-plus' ? 'element' : specifier.startsWith('@element-plus') ? 'icons'
      : specifier.split('/').at(-1)
    return `const ${bindings.startsWith('{') ? bindings.replace(/\bas\b/g, ':') : bindings} = globalThis.__metadataTest[${JSON.stringify(key)}] || {};`
  })
  return import(`data:text/javascript;base64,${Buffer.from(mocked).toString('base64')}`)
}
for (const name of ['useSiteSettings', 'usePageMetadata']) {
  globalThis.__metadataTest[name] = await compile(readFileSync(new URL(`../src/composables/${name}.ts`, import.meta.url), 'utf8'))
}
async function component(name) {
  const { descriptor } = parse(readFileSync(new URL(`../src/${name}.vue`, import.meta.url), 'utf8'))
  const result = (await compile(compileScript(descriptor, { id: name }).content)).default
  result.render = () => null
  return result
}
const App = await component('App')
const Article = await component('views/ArticleDetail')
const Gallery = await component('views/GalleryDetail')
App.render = () => h(RouterView)
const renderer = createRenderer({
  createElement: () => ({}), createText: () => ({}), createComment: () => ({}),
  insert() {}, remove() {}, setText() {}, setElementText() {}, patchProp() {},
  parentNode: () => null, nextSibling: () => null
})
const settle = async () => { for (let i = 0; i < 12; i++) { await Promise.resolve(); await nextTick() } }
const meta = key => find(`meta[property="${key}"]`)?.content

test('server branding survives slow settings; details and navigation update all metadata', async () => {
  const empty = { render: () => null }
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', component: empty }, { path: '/articles/:id', component: Article },
    { path: '/gallery/:id', component: Gallery }, { path: '/login', component: empty }
  ] })
  await router.push('/articles/2')
  const app = renderer.createApp(App)
  app.use(router); app.mount({}); await settle()
  assert.equal(document.title, '文章标题 | 来自服务端的站名')
  resolveSettings({ data: { siteName: '后台新站名', heroText: '站点简介', heroImage: '/uploads/hero/a.jpg', siteIcon: '' } })
  await settle()
  assert.equal(document.title, '文章标题 | 后台新站名')
  assert.equal(meta('og:title'), document.title)
  assert.equal(meta('og:description'), '文章摘要')
  await router.push('/gallery/3'); await settle()
  assert.equal(document.title, '图库标题 | 后台新站名')
  assert.equal(meta('og:image'), 'https://example.com/api/uploads/gallery/photo.webp')
  await router.push('/'); await settle()
  assert.equal(document.title, '后台新站名')
  assert.equal(meta('og:description'), '站点简介')
  assert.equal(meta('og:image'), 'https://example.com/api/uploads/hero/a.jpg')
  failArticle = true
  await router.push('/articles/9'); await settle()
  assert.equal(document.title, '后台新站名')
  assert.equal(find('meta[name="robots"]').content, 'noindex, nofollow')
  assert.equal(find('link[rel="canonical"]'), null)
  await router.push('/missing'); await settle()
  assert.equal(find('meta[name="robots"]').content, 'noindex, nofollow')
  app.unmount()
})
