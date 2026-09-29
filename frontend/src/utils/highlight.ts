import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import cpp from 'highlight.js/lib/languages/cpp'
import csharp from 'highlight.js/lib/languages/csharp'
import css from 'highlight.js/lib/languages/css'
import diff from 'highlight.js/lib/languages/diff'
import dockerfile from 'highlight.js/lib/languages/dockerfile'
import go from 'highlight.js/lib/languages/go'
import groovy from 'highlight.js/lib/languages/groovy'
import http from 'highlight.js/lib/languages/http'
import ini from 'highlight.js/lib/languages/ini'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import kotlin from 'highlight.js/lib/languages/kotlin'
import markdown from 'highlight.js/lib/languages/markdown'
import nginx from 'highlight.js/lib/languages/nginx'
import plaintext from 'highlight.js/lib/languages/plaintext'
import properties from 'highlight.js/lib/languages/properties'
import python from 'highlight.js/lib/languages/python'
import scss from 'highlight.js/lib/languages/scss'
import sql from 'highlight.js/lib/languages/sql'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'

/**
 * 代码块语法高亮。
 *
 * 只按需注册正文里真正会写的语言，不走 highlight.js 的全量包（`import hljs from 'highlight.js'`
 * 会把 190+ 种语言一起打进产物）。要支持新语言就在这里补一行 import + 注册。
 */
const LANGUAGES = {
  bash,
  cpp,
  csharp,
  css,
  diff,
  dockerfile,
  go,
  groovy,
  http,
  ini,
  java,
  javascript,
  json,
  kotlin,
  markdown,
  nginx,
  plaintext,
  properties,
  python,
  scss,
  sql,
  typescript,
  xml,
  yaml
}

for (const [name, language] of Object.entries(LANGUAGES)) {
  hljs.registerLanguage(name, language)
}

// highlight.js 没有 Vue 语言定义，单文件组件按 HTML/XML 高亮
hljs.registerAliases(['vue'], { languageName: 'xml' })

/** markdown 围栏里常见、但 highlight.js 本身不认识的写法，映射到已注册的语言。 */
const FENCE_ALIASES: Record<string, string> = {
  vue: 'xml',
  htm: 'xml',
  'c++': 'cpp',
  'c#': 'csharp',
  cs: 'csharp',
  conf: 'nginx',
  props: 'properties'
}

/** 语言标签上显示的正式写法，没收录的就原样显示围栏里写的名字。 */
const LANGUAGE_LABELS: Record<string, string> = {
  bash: 'Bash',
  c: 'C',
  conf: 'Nginx',
  cpp: 'C++',
  cs: 'C#',
  csharp: 'C#',
  css: 'CSS',
  diff: 'Diff',
  docker: 'Dockerfile',
  dockerfile: 'Dockerfile',
  go: 'Go',
  groovy: 'Groovy',
  htm: 'HTML',
  html: 'HTML',
  http: 'HTTP',
  ini: 'INI',
  java: 'Java',
  javascript: 'JavaScript',
  js: 'JavaScript',
  json: 'JSON',
  jsx: 'JSX',
  kotlin: 'Kotlin',
  kt: 'Kotlin',
  markdown: 'Markdown',
  md: 'Markdown',
  nginx: 'Nginx',
  plaintext: '纯文本',
  properties: 'Properties',
  props: 'Properties',
  py: 'Python',
  python: 'Python',
  scss: 'SCSS',
  sh: 'Shell',
  sql: 'SQL',
  text: '纯文本',
  ts: 'TypeScript',
  tsx: 'TSX',
  txt: '纯文本',
  typescript: 'TypeScript',
  vue: 'Vue',
  xml: 'XML',
  yaml: 'YAML',
  yml: 'YAML',
  zsh: 'Shell'
}

/** 围栏语言 → highlight.js 认识的语言名；识别不了返回 null（按纯文本渲染）。 */
export function resolveLanguage(raw: string): string | null {
  const name = raw.trim().toLowerCase()
  if (!name) return null
  if (hljs.getLanguage(name)) return name
  const mapped = FENCE_ALIASES[name]
  if (mapped) return mapped
  return null
}

/** 语言 token → 给人看的标签名（java → Java，vim → vim）。 */
export function languageLabel(raw: string): string {
  const name = raw.trim().toLowerCase()
  if (!name) return ''
  return LANGUAGE_LABELS[name] ?? raw.trim()
}

/**
 * 代码 → 带 hljs token 的 HTML。
 * 已经在 highlight.js 内部做过转义，调用方不要再转义一次；
 * 认不出的语言退化成纯文本（同样转义），保证不会漏出原始 HTML。
 */
export function highlightCode(code: string, language: string | null): string {
  const resolved = language ? resolveLanguage(language) : null
  if (!resolved) return escapeHtml(code)
  try {
    return hljs.highlight(code, { language: resolved, ignoreIllegals: true }).value
  } catch {
    return escapeHtml(code)
  }
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}
