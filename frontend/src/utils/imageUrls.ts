export type ThumbnailWidth = 480 | 1600

/** Normalize the relative upload URLs stored by the backend for browser requests. */
export function imageSource(path: string | null | undefined): string {
  if (!path) return ''
  if (/^(?:https?:)?\/\//i.test(path) || /^(?:data|blob):/i.test(path)) return path
  if (path.startsWith('/api/')) return path
  return `/api${path.startsWith('/') ? path : `/${path}`}`
}

/** Resolve the deterministic WebP sidecar for an article or gallery image. */
export function thumbnailSource(path: string | null | undefined, width: ThumbnailWidth): string {
  const source = imageSource(path)
  const queryOrHash = source.search(/[?#]/)
  const pathname = queryOrHash < 0 ? source : source.slice(0, queryOrHash)
  const suffix = queryOrHash < 0 ? '' : source.slice(queryOrHash)

  if (!pathname.startsWith('/api/uploads/article/') && !pathname.startsWith('/api/uploads/gallery/')) {
    return source
  }

  const slash = pathname.lastIndexOf('/')
  const extension = pathname.lastIndexOf('.')
  if (extension <= slash) return source
  return `${pathname.slice(0, extension)}.w${width}.webp${suffix}`
}

export function thumbnailSrcset(path: string): string {
  return `${thumbnailSource(path, 480)} 480w, ${thumbnailSource(path, 1600)} 1600w`
}
