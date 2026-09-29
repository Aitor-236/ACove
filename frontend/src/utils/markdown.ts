import { marked, type Tokens } from 'marked'
import DOMPurify from 'dompurify'
import { highlightCode, languageLabel, resolveLanguage } from './highlight'

/**
 * 正文和预览共用一份渲染规则：marked 解析 + 语法高亮 + DOMPurify 清洗。
 * 详情页和编辑页都调 renderMarkdown，避免两处规则漂移。
 *
 * 支持的代码块写法：
 * - ```java                → 单段代码，左上角显示语言名，并做语法高亮
 * - ```java title="X.java" → 用 title 覆盖左上角的标签文字
 * - ::: code-group 包住多段围栏 → 渲染成可切换语言的标签页
 */

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/**
 * 拆围栏信息串：`java title="X.java"` → { language, title }。
 * 第一个词不带 `=` 才算语言，所以 ``` title="x" 这种只有 title 的写法也认。
 */
function parseFenceInfo(info = '') {
  const trimmed = info.trim()
  const first = trimmed.split(/\s+/)[0] ?? ''
  const language = first.includes('=') ? '' : first
  const titleMatch = /(?:^|\s)title=(?:"([^"]*)"|'([^']*)')/.exec(trimmed)
  return { language, title: (titleMatch?.[1] ?? titleMatch?.[2] ?? '').trim() }
}

/** 单段代码 → <pre><code>；语言能识别就走高亮，认不出的按纯文本转义。 */
function renderCodeHtml(code: string, rawLanguage: string) {
  const resolved = resolveLanguage(rawLanguage)
  const languageClass = resolved ?? rawLanguage.trim().toLowerCase().replace(/[^\w-]/g, '')
  const classes = languageClass ? `hljs language-${languageClass}` : 'hljs'
  return `<pre><code class="${classes}">${highlightCode(code, rawLanguage)}</code></pre>`
}

/** 代码组里的标签文字：优先用 title，其次语言名。 */
function codeGroupTabLabel(rawLanguage: string, title: string, index: number) {
  return title || languageLabel(rawLanguage) || `代码 ${index + 1}`
}

marked.use({
  renderer: {
    /**
     * 代码围栏上写了语言（```java）时，额外包一层容器并在左上角显示语言名；
     * 没写语言的代码块保持原样，不加空标签。
     */
    code(token: Tokens.Code) {
      const { language, title } = parseFenceInfo(token.lang ?? '')
      const html = renderCodeHtml(token.text ?? '', language)
      if (!language && !title) return `${html}\n`
      const label = title || languageLabel(language)
      return `<div class="code-block"><span class="code-block-lang">${escapeHtml(label)}</span>${html}</div>\n`
    }
  },
  extensions: [
    {
      name: 'codeGroup',
      level: 'block',
      /** 让 marked 的块级分词直接跳到 `::: code-group` 起点 */
      start(src: string) {
        const index = src.search(/^ {0,3}:::[ \t]*code-group/m)
        return index < 0 ? undefined : index
      },
      /**
       * 把 `::: code-group ... :::` 之间的内容按普通块级语法再分词一次，
       * 里面通常是一串 fenced code。
       */
      tokenizer(src: string) {
        const match = /^ {0,3}:::[ \t]*code-group[^\n]*\n([\s\S]*?)\n {0,3}:::[ \t]*(?=\n|$)/.exec(
          src
        )
        if (!match) return undefined
        return {
          type: 'codeGroup',
          raw: match[0],
          tokens: this.lexer.blockTokens(match[1] ?? '', [])
        }
      },
      renderer(token) {
        const tokens = (token.tokens ?? []) as Tokens.Generic[]
        const blocks = tokens.filter((item) => item.type === 'code') as Tokens.Code[]
        // 容器里没有代码块（写法错了）时按普通内容渲染，不要吞掉正文
        if (!blocks.length) return this.parser.parse(tokens)

        const tabs = blocks
          .map((block, index) => {
            const { language, title } = parseFenceInfo(block.lang ?? '')
            const active = index === 0
            return `<button type="button" class="code-group-tab${active ? ' is-active' : ''}" role="tab" aria-selected="${active}" tabindex="${active ? 0 : -1}" data-tab-index="${index}">${escapeHtml(codeGroupTabLabel(language, title, index))}</button>`
          })
          .join('')

        const panels = blocks
          .map((block, index) => {
            const { language } = parseFenceInfo(block.lang ?? '')
            const active = index === 0
            return `<div class="code-group-panel${active ? ' is-active' : ''}" role="tabpanel" data-tab-index="${index}">${renderCodeHtml(block.text ?? '', language)}</div>`
          })
          .join('')

        // 容器里如果有代码块之外的内容，放到代码组后面，别被面板样式吃掉
        const rest = tokens.filter((item) => item.type !== 'code' && item.type !== 'space')
        const trailing = rest.length ? this.parser.parse(rest) : ''

        return `<div class="code-group"><div class="code-group-tabs" role="tablist" aria-label="切换代码语言">${tabs}</div>${panels}</div>\n${trailing}`
      }
    }
  ]
})

/** 清洗后的 HTML 里，src 分隔符一定是双引号，这里只补前缀、不碰其它地址。 */
const UPLOAD_IMAGE_SRC = /(<img\b[^>]*?\ssrc=")\/uploads\//gi

/** Markdown 原文 → 可直接 v-html 的 HTML。 */
export function renderMarkdown(source: string): string {
  const html = marked.parse(source, { async: false }) as string
  // 上传的图片在库里存的是 /uploads/... 相对地址（等于接口路径去掉 /api 前缀），
  // 渲染时统一补上 /api：开发由 vite、生产由 nginx 去掉前缀转发，
  // 这样正文里手写的 ![](/uploads/article/x.png) 也能正常显示。
  return DOMPurify.sanitize(html).replace(UPLOAD_IMAGE_SRC, '$1/api/uploads/')
}
