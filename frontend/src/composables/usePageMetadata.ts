import { onBeforeUnmount, shallowRef, watchEffect } from 'vue'

interface PageMetadata {
  title?: string
  description?: string | null
  image?: string | null
  type?: 'article' | 'website'
  noindex?: boolean
}
export const detailMetadata = shallowRef<PageMetadata | null>(null)
let owner: symbol | undefined

/** App.vue combines the currently mounted detail page with shared site settings. */
export function usePageMetadata(read: () => PageMetadata) {
  const identity = Symbol('page-metadata')
  watchEffect(() => {
    owner = identity
    detailMetadata.value = read()
  })
  onBeforeUnmount(() => {
    if (owner === identity) {
      owner = undefined
      detailMetadata.value = null
    }
  })
}

export function updateMeta(attribute: 'name' | 'property', key: string, value: string) {
  let element = document.head.querySelector<HTMLMetaElement>(`meta[${attribute}="${key}"]`)
  if (!element) {
    element = document.createElement('meta')
    element.setAttribute(attribute, key)
    document.head.append(element)
  }
  element.content = value
}
