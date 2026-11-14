// Extracts what the documentation claims about the HTTP API, so tests can hold it against the
// implementation (#249). No dependencies: runs with `node --test tools/docs-drift/*.test.mjs`.
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

export const REPO_ROOT = join(fileURLToPath(new URL('.', import.meta.url)), '..', '..')

// Directories that hold no user documentation: dependencies, build output, vendored skills.
const SKIP_DIRS = new Set(['.git', 'node_modules', 'build', 'dist', '.gradle', '.claude', 'test-results', 'playwright-report'])

/** Every Markdown file in the repository, as repo-relative POSIX paths. */
export function markdownFiles(root = REPO_ROOT) {
  return walkFiles(root, (name) => name.toLowerCase().endsWith('.md'))
}

/** The pages of the static website (website/*.html). */
export function websitePages(root = REPO_ROOT) {
  return walkFiles(join(root, 'website'), (name) => name.endsWith('.html')).map((f) => `website/${f}`)
}

/** All user documentation: Markdown anywhere plus the website pages. */
export function docFiles(root = REPO_ROOT) {
  return [...markdownFiles(root), ...websitePages(root)]
}

function walkFiles(root, accept) {
  const out = []
  const walk = (dir) => {
    for (const name of readdirSync(dir)) {
      if (SKIP_DIRS.has(name)) continue
      const full = join(dir, name)
      const st = statSync(full)
      if (st.isDirectory()) walk(full)
      else if (accept(name)) out.push(relative(root, full).split(sep).join('/'))
    }
  }
  walk(root)
  return out.sort()
}

export function read(file, root = REPO_ROOT) {
  return readFileSync(join(root, file), 'utf8').replace(/\r\n/g, '\n')
}

const ENTITIES = { '&lt;': '<', '&gt;': '>', '&amp;': '&', '&quot;': '"', '&#39;': "'", '&apos;': "'" }

/** <pre> blocks of an HTML page, with tags stripped and entities decoded. */
function htmlCodeBlocks(html) {
  const blocks = []
  for (const m of html.matchAll(/<pre[^>]*>([\s\S]*?)<\/pre>/g)) {
    const body = m[1].replace(/<[^>]+>/g, '').replace(/&(lt|gt|amp|quot|#39|apos);/g, (e) => ENTITIES[e])
    blocks.push({ lang: '', line: html.slice(0, m.index).split('\n').length - 1, body })
  }
  return blocks
}

/**
 * Code blocks of a document: fenced blocks in Markdown, <pre> blocks in HTML.
 * Each is { lang, body, line } where line is the 1-based line of the opening fence.
 */
export function codeBlocks(markdown) {
  if (/^\s*<(!doctype|html)/i.test(markdown)) return htmlCodeBlocks(markdown)
  const blocks = []
  const lines = markdown.split('\n')
  let open = null
  lines.forEach((text, i) => {
    const fence = text.match(/^\s*(`{3,}|~{3,})\s*([\w+-]*)/)
    if (!open && fence) {
      open = { marker: fence[1], lang: fence[2].toLowerCase(), line: i + 1, body: [] }
    } else if (open && text.trim().startsWith(open.marker[0].repeat(open.marker.length)) && text.trim().replace(/[`~]/g, '') === '') {
      blocks.push({ lang: open.lang, line: open.line, body: open.body.join('\n') })
      open = null
    } else if (open) {
      open.body.push(text)
    }
  })
  return blocks
}

/**
 * Splits shell text into words, honouring quotes and backslash-newline continuations. Unquoted
 * `|`, `;`, `&&`, `||`, `)` and newlines end a command and come back as `{ op }` tokens.
 */
export function shellTokens(text) {
  const src = text.replace(/\\\n/g, ' ')
  const tokens = []
  let word = null
  const push = () => { if (word !== null) tokens.push(word); word = null }
  for (let i = 0; i < src.length; i++) {
    const c = src[i]
    if (c === "'") {
      const end = src.indexOf("'", i + 1)
      const stop = end === -1 ? src.length : end
      word = (word ?? '') + src.slice(i + 1, stop)
      i = stop
    } else if (c === '"') {
      let j = i + 1
      let acc = ''
      while (j < src.length && src[j] !== '"') {
        if (src[j] === '\\' && j + 1 < src.length) { acc += src[j + 1]; j += 2 } else { acc += src[j]; j++ }
      }
      word = (word ?? '') + acc
      i = j
    } else if (c === '\\' && i + 1 < src.length) {
      word = (word ?? '') + src[i + 1]
      i++
    } else if (c === '#' && word === null && (i === 0 || /\s/.test(src[i - 1]))) {
      const nl = src.indexOf('\n', i)
      i = (nl === -1 ? src.length : nl) - 1
    } else if (c === '\n' || c === ';' || c === '|' || c === ')' || c === '(' || (c === '&' && src[i + 1] === '&')) {
      push()
      tokens.push({ op: c })
      if (c === '&' || (c === '|' && src[i + 1] === '|')) i++
    } else if (/\s/.test(c)) {
      push()
    } else {
      word = (word ?? '') + c
    }
  }
  push()
  return tokens
}

const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']

/** Parses the words of one curl invocation (after the `curl` word). */
export function parseCurl(words) {
  const headers = []
  let method = null
  let url = null
  let body = null
  let hasData = false
  for (let i = 0; i < words.length; i++) {
    const w = words[i]
    const next = () => words[++i]
    if (w === '-X' || w === '--request') method = (next() ?? '').toUpperCase()
    else if (/^-X[A-Z]+$/.test(w)) method = w.slice(2)
    else if (w === '-H' || w === '--header') headers.push(next() ?? '')
    else if (['-d', '--data', '--data-raw', '--data-binary', '--json'].includes(w)) {
      hasData = true
      body = next() ?? ''
      if (w === '--json') headers.push('Content-Type: application/json')
    } else if (w === '-F' || w === '--form' || w === '--data-urlencode') { hasData = true; next() }
    else if (w === '--upload-file' || w === '-T') { hasData = true; method = method ?? 'PUT'; next() }
    else if (['-o', '--output', '-u', '--user', '-w', '--write-out', '--cacert', '--cert', '--key', '-b', '--cookie', '-c', '--cookie-jar', '-A', '--user-agent', '--max-time', '-m', '--connect-timeout', '--retry', '-e', '--referer'].includes(w)) next()
    else if (w === '--url') url = next()
    else if (!w.startsWith('-') && url === null) url = w
  }
  if (!method) method = hasData ? 'POST' : 'GET'
  return { method, url, headers, body }
}

/** Every curl command in a Markdown document: { file, line, method, url, path, headers, body }. */
export function curlCommands(markdown, file = '') {
  const out = []
  for (const block of codeBlocks(markdown)) {
    const tokens = shellTokens(block.body)
    let nth = 0
    for (let i = 0; i < tokens.length; i++) {
      if (tokens[i] !== 'curl') continue
      const words = []
      let j = i + 1
      while (j < tokens.length && typeof tokens[j] === 'string') words.push(tokens[j++])
      const parsed = parseCurl(words)
      const line = block.line + 1 + countCurlLine(block.body, nth++)
      out.push({ file, line, ...parsed, path: apiPath(parsed.url) })
    }
  }
  return out
}

function countCurlLine(body, nth) {
  const lines = body.split('\n')
  let seen = -1
  for (let k = 0; k < lines.length; k++) {
    const matches = lines[k].match(/(^|[\s($|`])curl(\s|$)/g)
    if (!matches) continue
    for (let m = 0; m < matches.length; m++) { seen++; if (seen === nth) return k }
  }
  return 0
}

/** `/api/...` path of a URL with the query string removed, or null when it is not an API URL. */
export function apiPath(url) {
  if (!url) return null
  const at = url.indexOf('/api/')
  if (at === -1) return null
  return url.slice(at).split(/[?#]/)[0].replace(/\/+$/, '') || null
}

/**
 * Endpoints written as `METHOD /api/...`: in http/text code blocks, and inline in prose or tables
 * (`POST /api/v1/assert`). Returns { file, line, method, path }.
 */
export function endpointMentions(markdown, file = '') {
  const out = []
  const re = new RegExp(`\\b(${METHODS.join('|')})\\s+(/api/[^\\s\`'"|)<>]*(?:<[^>\\s]+>[^\\s\`'"|)<>]*)*)`, 'g')
  markdown.split('\n').forEach((text, i) => {
    for (const m of text.matchAll(re)) out.push({ file, line: i + 1, method: m[1], path: apiPath(m[2]) })
  })
  return out
}

const PLACEHOLDER = /^(\$\{?[A-Za-z_][A-Za-z0-9_]*\}?|<[^>]+>|\{[^}]+\}|:[A-Za-z_]+|\.\.\.|…)$/

/** Does a documented path match a path template from the OpenAPI document? */
export function pathMatches(docPath, template) {
  const d = docPath.split('/').filter(Boolean)
  const t = template.split('/').filter(Boolean)
  if (d.length !== t.length) return false
  return d.every((seg, k) => {
    const isParam = /^\{[^}]+\}$/.test(t[k])
    if (isParam) return true
    if (PLACEHOLDER.test(seg) || /\$\{?[A-Za-z_]/.test(seg)) return false
    return seg === t[k]
  })
}

export function loadOpenApi(root = REPO_ROOT) {
  return JSON.parse(readFileSync(join(root, 'docs', 'openapi.json'), 'utf8'))
}

/** The operation in the OpenAPI document that a documented call refers to, or null. */
export function findOperation(openapi, method, path) {
  // Literal segments beat parameters: /api/v1/flows/search before /api/v1/flows/{id}.
  const candidates = Object.keys(openapi.paths)
    .filter((t) => pathMatches(path, t) && openapi.paths[t][method.toLowerCase()])
    .sort((a, b) => (a.match(/\{/g) ?? []).length - (b.match(/\{/g) ?? []).length)
  if (!candidates.length) return null
  return { template: candidates[0], operation: openapi.paths[candidates[0]][method.toLowerCase()] }
}

/** Does the path exist at all, with any method? Used to say "wrong method" rather than "no such path". */
export function pathExists(openapi, path) {
  return Object.keys(openapi.paths).some((t) => pathMatches(path, t))
}

/** Resolves a $ref in the OpenAPI document. */
export function deref(openapi, schema) {
  let s = schema
  const seen = new Set()
  while (s && s.$ref && !seen.has(s.$ref)) {
    seen.add(s.$ref)
    s = s.$ref.replace(/^#\//, '').split('/').reduce((o, k) => o?.[k], openapi)
  }
  return s
}

/** Property names a JSON request body may carry, or null when the schema is open/unknown. */
export function requestProperties(openapi, operation) {
  const content = operation.requestBody?.content
  if (!content) return null
  const media = content['application/json'] ?? Object.values(content)[0]
  const schema = deref(openapi, media?.schema)
  if (!schema) return null
  const props = new Set()
  const collect = (s) => {
    s = deref(openapi, s)
    if (!s) return false
    if (s.type === 'object' && !s.properties && s.additionalProperties !== false) return false
    for (const k of Object.keys(s.properties ?? {})) props.add(k)
    for (const sub of [...(s.allOf ?? []), ...(s.oneOf ?? []), ...(s.anyOf ?? [])]) if (collect(sub) === false) return false
    return true
  }
  if (collect(schema) === false || props.size === 0) return null
  return props
}

/**
 * Parses a documented JSON body. Shell variables inside it ("$FLOW_ID", ${X}) become strings so
 * the shape can be checked; returns undefined when it is not JSON (form data, placeholders).
 */
export function parseJsonBody(body) {
  if (!body || !/^\s*[[{]/.test(body)) return undefined
  const cleaned = body
    .replace(/"\$(?:\{\w+\}|\w+)"/g, '"x"')
    .replace(/\$(?:\{\w+\}|\w+)/g, '0')
    .replace(/\/\/[^\n"]*$/gm, '')
  try { return JSON.parse(cleaned) } catch { return undefined }
}

export { METHODS }

/** Required property names of a JSON request body, or an empty set. */
export function requiredProperties(openapi, operation) {
  const content = operation.requestBody?.content
  if (!content) return new Set()
  const media = content['application/json'] ?? Object.values(content)[0]
  const schema = deref(openapi, media?.schema)
  const req = new Set(schema?.required ?? [])
  for (const sub of schema?.allOf ?? []) for (const k of deref(openapi, sub)?.required ?? []) req.add(k)
  return req
}

/** Rows of Markdown tables that name an endpoint: | `POST` | `/api/v1/flows` | ... */
export function tableEndpoints(markdown, file = '') {
  const out = []
  markdown.split('\n').forEach((text, i) => {
    if (!text.trim().startsWith('|')) return
    const cells = text.split('|').map((c) => c.trim().replace(/^`|`$/g, ''))
    const method = cells.find((c) => METHODS.includes(c))
    const path = cells.find((c) => c.startsWith('/api/'))
    if (method && path) out.push({ file, line: i + 1, method, path: apiPath(path.split(/\s/)[0]) })
  })
  return out
}

/** Requests in ```http blocks: { file, line, method, path, body }. */
export function httpBlockRequests(markdown, file = '') {
  const out = []
  for (const block of codeBlocks(markdown)) {
    if (block.lang !== 'http') continue
    const lines = block.body.split('\n')
    lines.forEach((text, k) => {
      const m = text.match(new RegExp(`^\\s*(${METHODS.join('|')})\\s+(\\S+)`))
      if (!m) return
      // The body is the JSON that follows, up to the next request line.
      let end = lines.findIndex((l, n) => n > k && new RegExp(`^\\s*(${METHODS.join('|')})\\s+/`).test(l))
      if (end === -1) end = lines.length
      const rest = lines.slice(k + 1, end).filter((l) => !/^[A-Za-z-]+:\s/.test(l) && !/^\s*#/.test(l)).join('\n').trim()
      out.push({ file, line: block.line + 1 + k, method: m[1], path: apiPath(m[2]), body: rest.startsWith('{') ? rest : null })
    })
  }
  return out
}
