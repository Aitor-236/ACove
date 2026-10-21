/** Open the preserved original when a generated Markdown image is clicked. */
export function handleMarkdownImageClick(event: MouseEvent, openPreview: (url: string) => void) {
  const target = event.target
  if (!(target instanceof Element)) return

  const image = target.closest<HTMLImageElement>('img[data-full-src]')
  const original = image?.dataset.fullSrc
  if (!image || !original) return

  event.preventDefault()
  openPreview(original)
}

export function handleMarkdownImageKeydown(event: KeyboardEvent, openPreview: (url: string) => void) {
  if (event.key !== 'Enter' && event.key !== ' ') return
  const target = event.target
  if (!(target instanceof HTMLImageElement)) return

  const original = target.dataset.fullSrc
  if (!original) return

  event.preventDefault()
  openPreview(original)
}

/** Fall back to the original if a sidecar is missing or could not be decoded. */
export function handleMarkdownImageError(event: Event) {
  const image = event.target
  if (!(image instanceof HTMLImageElement) || image.dataset.fullFallback === 'true') return

  const original = image.dataset.fullSrc
  if (!original) return

  image.dataset.fullFallback = 'true'
  image.removeAttribute('srcset')
  image.setAttribute('src', original)
}
