import assert from 'node:assert/strict'
import { test } from 'node:test'
import { compileView, deferred, mountView, settle } from './helpers/view-harness.mjs'

globalThis.window = { scrollY: 0, scrollTo() {}, history: { state: {} } }
globalThis.requestAnimationFrame = callback => { callback(); return 0 }

test('article filters keep the latest response, error and loading state', async () => {
  const pending = []
  const component = await compileView('Articles', { get(url, config) {
    if (url !== '/article/list') return Promise.resolve({ data: [] })
    const operation = deferred()
    pending.push({ ...operation, config })
    return operation.promise
  } })
  const ctx = await mountView(component, '/articles', '/articles')
  const s = ctx.state
  s.selectCategory('B')
  pending[0].resolve({ data: { records: [{ id: 1 }], total: 1 } })
  await settle()
  assert.equal(s.loading, true, 'an old response must not stop the current loading indicator')
  assert.deepEqual([...s.articles], [])
  pending[1].resolve({ data: { records: [{ id: 2 }], total: 2 } })
  await settle()
  assert.equal(s.articles[0].id, 2)
  assert.equal(s.total, 2)
  s.selectCategory('C')
  s.selectCategory('D')
  pending[3].resolve({ data: { records: [{ id: 4 }], total: 4 } })
  await settle()
  pending[2].reject(Error('obsolete request failed'))
  await settle()
  assert.equal(s.articles[0].id, 4)
  assert.equal(s.errorMessage, '')
  assert.equal(s.loading, false)
  assert.ok(pending.every(operation => operation.config.silent))
  ctx.close()
})

for (const [view, prefix, field, errorField, loader] of [
  ['ArticleDetail', '/articles', 'article', 'errorMessage', 'loadArticle'],
  ['GalleryDetail', '/gallery', 'gallery', 'error', 'loadGallery']
]) {
  test(`${view} ignores late results across route changes, retries and unmount`, async () => {
    const pending = []
    const component = await compileView(view, { get(url, config) {
      const operation = deferred()
      pending.push({ ...operation, url, config })
      return operation.promise
    } })
    const ctx = await mountView(component, `${prefix}/1`, `${prefix}/:id`)
    const s = ctx.state
    await ctx.router.push(`${prefix}/2`)
    await settle()
    pending[1].resolve({ data: { id: 2, images: [] } })
    await settle()
    pending[0].resolve({ data: { id: 1, images: [] } })
    await settle()
    assert.equal(s[field].id, 2)
    assert.equal(s[errorField], '')
    const retry = s[loader]('2')
    const latest = s[loader]('2')
    pending[2].reject(Error('obsolete retry failed'))
    await settle()
    assert.equal(s.loading, true)
    pending[3].resolve({ data: { id: 2, images: [] } })
    await Promise.all([retry, latest])
    assert.equal(s[errorField], '')
    const left = s[loader]('2')
    ctx.close()
    pending[4].resolve({ data: { id: 999, images: [] } })
    await left
    assert.equal(s[field], null, 'unmounted view must discard the response')
    assert.ok(pending.every(operation => operation.config.silent))
  })
}
