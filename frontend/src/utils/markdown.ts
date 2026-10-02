import { marked, type Tokens } from 'marked'
import DOMPurify from 'dompurify'
import { highlightCode, languageLabel, resolveLanguage } from './highlight'

/**
 * 正文和预览共用一份渲染规则：marked 解析 + 语法高亮 + DOMPurify 清洗。
 * 详情页和编辑页都调 renderMarkdown，避免两处规则漂移。
 *
 * 支持的写法：
 * - ```java                → 单段代码，左上角显示语言名，并做语法高亮
 * - ```java title="X.java" → 用 title 覆盖左上角的标签文字
 * - ::: code-group 包住多段围栏 → 渲染成可切换语言的标签页
 * - | a | b | 加一行 | --- | 分隔 → GFM 表格（列对齐写 :--- / :---: / ---:）
 * - 编辑器里敲一次回车就是一次换行（breaks: true），不用再空一行
 */

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** 围栏代码块的开头 / 结尾行（``` 或 ~~~），里面的内容一律不做表格规整 */
const FENCE_LINE = /^\s{0,3}(```|~~~)/
const FULL_WIDTH_PIPE = /\uFF5C/g
const FULL_WIDTH_DASH = /[\u2014\u2013\uFF0D\uFE63]/g
const SEPARATOR_CELL = /^\s*:?-+:?\s*$/

/**
 * 拆一行表格：至少两列才算表格行，否则返回 null（正文、普通代码行一律不碰）。
 * 全角竖线当半角用，所以 `｜ a ｜ b ｜` 也能认出来。
 */
function tableCells(line: string): string[] | null {
  const normalized = line
    .replace(FULL_WIDTH_PIPE, '|')
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
  if (!normalized.includes('|')) return null
  const cells = normalized.split('|')
  return cells.length >= 2 ? cells : null
}

/** 是不是表格的分隔行（| --- | :---: |） */
function isSeparatorRow(line: string): boolean {
  const cells = tableCells(line)
  return cells !== null && cells.every((cell) => SEPARATOR_CELL.test(cell))
}

/**
 * 手写表格时常见、但 marked 不认的几种写法，渲染前先规整（只动像表格的行）：
 * 1. 全角竖线 `｜`（中文输入法下最容易打出来）→ `|`；
 * 2. 分隔行里的全角破折号 `——` → `---`（先按全角竖线替换后判断确实是分隔行才换，正文里的破折号不动）；
 * 3. 忘了写分隔行（`|你好|你好|` 直接跟数据行，中间可能还空了一行）→ 自动补一行 `| --- | --- |`，
 *    表头取第一行；这条只认"每行首尾都是竖线"的连续行，普通文字不会被误判成表格；
 * 4. 整块缩进的表格（表头行和分隔行缩进一致）→ 去掉这层缩进，否则 4 空格缩进会被当成代码块。
 * 围栏代码块里的内容一律原样保留。
 */
function normalizeTables(source: string): string {
  return splitFences(source)
    .map((chunk) =>
      chunk.fence
        ? chunk.lines.join('\n')
        : dedentTables(insertMissingSeparatorRows(halfWidthRows(chunk.lines))).join('\n')
    )
    .join('\n')
}

/** 把正文切成「围栏代码块」和「普通文本」两类片段，表格规整只对普通文本做 */
function splitFences(source: string): Array<{ fence: boolean; lines: string[] }> {
  const chunks: Array<{ fence: boolean; lines: string[] }> = []
  let fence = false
  for (const line of source.split('\n')) {
    if (FENCE_LINE.test(line)) fence = !fence
    const last = chunks[chunks.length - 1]
    if (last && last.fence === fence) last.lines.push(line)
    else chunks.push({ fence, lines: [line] })
  }
  return chunks
}

/** 1. 全角竖线 → 半角；分隔行里的全角破折号 → --- */
function halfWidthRows(lines: string[]): string[] {
  return lines.map((line) => {
    if (!tableCells(line)) return line
    const half = line.replace(FULL_WIDTH_PIPE, '|')
    const dashed = half.replace(FULL_WIDTH_DASH, '-')
    return isSeparatorRow(dashed) ? dashed : half
  })
}

/** 这行明显是想写表格：首尾都是竖线，且至少两列 */
function isWrappedPipeRow(line: string): boolean {
  const trimmed = line.replace(FULL_WIDTH_PIPE, '|').trim()
  if (!trimmed.startsWith('|') || !trimmed.endsWith('|') || trimmed.length < 3) return false
  return tableCells(trimmed) !== null
}

/** 2. 没写分隔行时自动补一行（表头取第一行），顺手合并被空行切开的表格行 */
function insertMissingSeparatorRows(lines: string[]): string[] {
  const result: string[] = []
  let index = 0
  while (index < lines.length) {
    const first = lines[index] ?? ''
    if (!isWrappedPipeRow(first)) {
      result.push(first)
      index += 1
      continue
    }

    const rows: string[] = [first]
    let hasSeparator = isSeparatorRow(first)
    let cursor = index + 1
    while (cursor < lines.length) {
      const line = lines[cursor] ?? ''
      if (isWrappedPipeRow(line)) {
        rows.push(line)
        hasSeparator = hasSeparator || isSeparatorRow(line)
        cursor += 1
        continue
      }
      // 还没出现分隔行时，空行也算表格内部（作者常拿空行当表头分隔）
      if (!hasSeparator && line.trim() === '' && isWrappedPipeRow(lines[cursor + 1] ?? '')) {
        cursor += 1
        continue
      }
      break
    }

    if (!hasSeparator && rows.length >= 2) {
      const header = rows[0] ?? ''
      const columns = Math.max(tableCells(header)?.length ?? 2, 2)
      // 补的分隔行跟表头保持同样缩进，方便后面 dedentTables 把整块缩进一起去掉
      const headerIndent = /^[ \t]*/.exec(header)?.[0] ?? ''
      result.push(header)
      result.push(`${headerIndent}| ${new Array(columns).fill('---').join(' | ')} |`)
      result.push(...rows.slice(1))
    } else {
      result.push(...rows)
    }
    index = cursor
  }
  return result
}

/** 3. 整块缩进的表格去掉缩进，否则 4 空格 / Tab 会被 marked 当成代码块 */
function dedentTables(lines: string[]): string[] {
  const result: string[] = []
  let index = 0
  while (index < lines.length) {
    const line = lines[index] ?? ''
    const next = lines[index + 1] ?? ''
    const indent = /^[ \t]+/.exec(line)?.[0] ?? ''
    const dedentable =
      indent !== '' &&
      tableCells(line) !== null &&
      !isSeparatorRow(line) &&
      isSeparatorRow(next) &&
      next.startsWith(indent)

    if (!dedentable) {
      result.push(line)
      index += 1
      continue
    }

    result.push(line.slice(indent.length))
    result.push(next.slice(indent.length))
    let cursor = index + 2
    while (cursor < lines.length) {
      const row = lines[cursor] ?? ''
      if (!row.startsWith(indent) || tableCells(row) === null) break
      result.push(row.slice(indent.length))
      cursor += 1
    }
    index = cursor
  }

  return result
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
  // breaks: true —— 编辑器里敲一次回车，正文里就换一行（否则单个换行会被合并成空格，
  // 得空两行才能分段）。表格 / 代码块不受影响，只影响段落里的人为换行。
  const html = marked.parse(normalizeTables(source), {
    async: false,
    gfm: true,
    breaks: true
  }) as string
  // 上传的图片在库里存的是 /uploads/... 相对地址（等于接口路径去掉 /api 前缀），
  // 渲染时统一补上 /api：开发由 vite、生产由 nginx 去掉前缀转发，
  // 这样正文里手写的 ![](/uploads/article/x.png) 也能正常显示。
  return DOMPurify.sanitize(html).replace(UPLOAD_IMAGE_SRC, '$1/api/uploads/')
}
