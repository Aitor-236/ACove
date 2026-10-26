import { computed, readonly, ref } from 'vue'
import request from '@/utils/request'

interface SiteSettings {
  siteName: string
  siteIcon: string
  heroText: string
  heroImage: string
}
const initialName = document.querySelector<HTMLMetaElement>('meta[property="og:site_name"]')?.content || 'ACove'
const settings = ref<SiteSettings>({ siteName: initialName, siteIcon: '', heroText: '', heroImage: '' })
const siteName = computed(() => settings.value.siteName || 'ACove')
let pending: Promise<void> | undefined

/** Shared settings for the app title, favicon and login brand. */
export function useSiteSettings() {
  return { siteName, siteSettings: readonly(settings), loadSiteSettings }
}

export function loadSiteSettings(): Promise<void> {
  if (pending) return pending
  pending = request.get('/site/settings', { silent: true })
    .then((res) => { settings.value = (res as unknown as { data: SiteSettings }).data })
    .catch(() => { /* Keep the last settings when the server is unavailable. */ })
    .finally(() => { pending = undefined })
  return pending
}
