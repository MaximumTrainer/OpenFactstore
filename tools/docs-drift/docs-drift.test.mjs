// #249: the documentation must describe the system that is actually built. Each test here holds
// one kind of claim the docs make against the implementation, so drift fails CI instead of
// reaching readers. Run: node --test tools/docs-drift/*.test.mjs
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, normalize } from 'node:path'
import {
  REPO_ROOT, read, docFiles, markdownFiles, curlCommands, endpointMentions, tableEndpoints,
  httpBlockRequests, findOperation, pathExists, loadOpenApi, parseJsonBody, requestProperties, unknownBodyFields,
  codeBlocks, kotlinRequiredFields, requestSchemaName, headingAnchors, links, requestMediaTypes, requestEnums,
  cliVocabulary, cliInvocations
} from './lib.mjs'
import { TUTORIALS, tutorialScript } from './tutorials.mjs'

const CANONICAL_GUIDE = 'docs/user-guide.md'
const openapi = loadOpenApi()
const docs = docFiles().map((file) => ({ file, text: read(file) }))
const where = (x) => `${x.file}:${x.line}`
const report = (problems) => `\n  ${problems.join('\n  ')}\n`

test('there is one user guide; USER_GUIDE.md only points to it', () => {
  const pointer = read('USER_GUIDE.md')
  assert.match(pointer, /\]\(\.\/docs\/user-guide\.md\)/, 'USER_GUIDE.md must link to docs/user-guide.md')
  assert.ok(pointer.split('\n').filter((l) => l.trim()).length <= 8, 'USER_GUIDE.md must be a short pointer, not a second guide')
  assert.doesNotMatch(pointer, /^##\s/m, 'USER_GUIDE.md must not have sections of its own')

  const others = markdownFiles().filter((f) => /user[-_ ]?guide/i.test(f) && f !== CANONICAL_GUIDE && f !== 'USER_GUIDE.md')
  assert.deepEqual(others, [], 'another user guide exists')

  // Nothing links readers to the pointer when it could link to the guide itself.
  const linking = docs
    .filter(({ file }) => file !== 'USER_GUIDE.md')
    .flatMap(({ file, text }) => text.split('\n').map((l, i) => ({ file, line: i + 1, l })))
    .filter(({ l }) => /USER_GUIDE\.md/.test(l))
    .map(where)
  assert.deepEqual(linking, [], 'links still point at USER_GUIDE.md')
})

test('the website points at the user guide instead of keeping a copy', () => {
  const page = read('website/user-guide.html')
  assert.match(page, /blob\/main\/docs\/user-guide\.md/, 'website/user-guide.html must link to docs/user-guide.md')
  assert.ok((page.match(/<section/g) ?? []).length <= 2, 'website/user-guide.html must not carry the guide itself')
})

test('every relative link and anchor in the docs resolves', () => {
  const broken = []
  const anchorsOf = (f) => headingAnchors(read(f))
  for (const { file, text } of docs) {
    for (const { line, target } of links(text)) {
      let path
      let anchor
      if (target.includes('/OpenFactstore/blob/main/')) [path, anchor] = target.split('/blob/main/')[1].split('#')
      else if (/^(https?:|mailto:|\{|\$)/.test(target)) continue
      else if (target.startsWith('#')) { if (!file.endsWith('.md')) continue; path = file; anchor = target.slice(1) }
      else { const [p, a] = target.split('#'); path = normalize(join(dirname(file), p)).split('\\').join('/'); anchor = a }
      if (!existsSync(join(REPO_ROOT, path))) broken.push(`${file}:${line} ${target} (no such file)`)
      else if (anchor && path.endsWith('.md') && !anchorsOf(path).has(anchor)) broken.push(`${file}:${line} ${target} (no such heading)`)
    }
  }
  assert.deepEqual(broken, [], `broken links${report(broken)}`)
})

test('no document tells readers to send X-Api-Key', () => {
  const hits = docs.flatMap(({ file, text }) =>
    text.split('\n').flatMap((l, i) => (l.includes('X-Api-Key') ? [`${file}:${i + 1}`] : [])))
  assert.deepEqual(hits, [], `X-Api-Key in the docs; the API takes Authorization: Bearer${report(hits)}`)
})

test('no document says the API can be called without credentials', () => {
  const stale = /does not require authentication|currently has no authentication|accepts all requests without|not yet enforced|publicly accessible|Authentication[^\n]{0,40}Not yet implemented/i
  const hits = docs.flatMap(({ file, text }) =>
    text.split('\n').flatMap((l, i) => (stale.test(l) ? [`${file}:${i + 1} ${l.trim().slice(0, 100)}`] : [])))
  assert.deepEqual(hits, [], `authentication is enforced by default since #209${report(hits)}`)
})

test('docs give the OpenAPI and Swagger UI paths the backend serves', () => {
  const yml = read('backend/src/main/resources/application.yml')
  const apiDocs = yml.match(/api-docs:\s*\n\s*path:\s*(\S+)/)[1]
  const swagger = yml.match(/swagger-ui:\s*\n\s*path:\s*(\S+)/)[1]
  const wrong = []
  for (const { file, text } of docs) {
    text.split('\n').forEach((l, i) => {
      for (const [, p] of l.matchAll(/localhost:\d+(\/[\w/.-]*api-docs)\b/g)) if (p !== apiDocs) wrong.push(`${file}:${i + 1} ${p} (served at ${apiDocs})`)
      for (const [, p] of l.matchAll(/localhost:\d+(\/swagger-ui[\w/.-]*)/g)) if (p !== swagger) wrong.push(`${file}:${i + 1} ${p} (served at ${swagger})`)
    })
  }
  assert.deepEqual(wrong, [], `paths that 404${report(wrong)}`)
})

test('every curl call to /api/ in the docs sends Authorization: Bearer', () => {
  const missing = docs
    .flatMap(({ file, text }) => curlCommands(text, file))
    .filter((c) => c.path && !c.headers.some((h) => /^authorization:\s*bearer\s+\S/i.test(h)))
    .map((c) => `${where(c)} ${c.method} ${c.path}`)
  assert.deepEqual(missing, [], `curl examples without credentials get 401${report(missing)}`)
})

function documentedCalls() {
  return docs.flatMap(({ file, text }) => [
    ...curlCommands(text, file).map((c) => ({ ...c, kind: 'curl' })),
    ...httpBlockRequests(text, file).map((c) => ({ ...c, kind: 'http' })),
    ...endpointMentions(text, file).map((c) => ({ ...c, kind: 'mention' })),
    ...tableEndpoints(text, file).map((c) => ({ ...c, kind: 'table' }))
  ]).filter((c) => c.path)
}

test('every documented endpoint exists in the API (docs/openapi.json)', () => {
  const wrong = documentedCalls()
    .filter((c) => !findOperation(openapi, c.method, c.path))
    .map((c) => `${where(c)} ${c.method} ${c.path}${pathExists(openapi, c.path) ? ' (path exists, not with this method)' : ''}`)
  assert.deepEqual([...new Set(wrong)], [], `endpoints that the API does not serve${report([...new Set(wrong)])}`)
})

test('documented JSON request bodies only use fields the API accepts', () => {
  const wrong = []
  for (const c of documentedCalls().filter((c) => c.kind === 'curl' || c.kind === 'http')) {
    const body = parseJsonBody(c.body)
    if (!body) continue
    const found = findOperation(openapi, c.method, c.path)
    if (!found) continue
    const unknown = unknownBodyFields(openapi, found.operation, body)
    const accepted = requestProperties(openapi, found.operation)
    if (unknown.length) wrong.push(`${where(c)} ${c.method} ${c.path}: ${unknown.join(', ')} (accepts ${[...(accepted ?? [])].join(', ')})`)
  }
  assert.deepEqual(wrong, [], `fields the API ignores or rejects${report(wrong)}`)
})

test('documented JSON request bodies carry every field the API requires', () => {
  const wrong = []
  for (const c of documentedCalls().filter((c) => c.kind === 'curl' || c.kind === 'http')) {
    const body = parseJsonBody(c.body)
    if (!body || Array.isArray(body)) continue
    const found = findOperation(openapi, c.method, c.path)
    const dto = found && requestSchemaName(found.operation)
    const required = dto && kotlinRequiredFields(dto)
    if (!required) continue
    const missing = [...required].filter((k) => !(k in body))
    if (missing.length) wrong.push(`${where(c)} ${c.method} ${c.path}: missing ${missing.join(', ')} (${dto})`)
  }
  assert.deepEqual(wrong, [], `requests the API refuses with 400${report(wrong)}`)
})

test('documented requests send the content type the API accepts', () => {
  const wrong = []
  for (const c of documentedCalls().filter((c) => c.kind === 'curl')) {
    const found = findOperation(openapi, c.method, c.path)
    if (!found) continue
    const accepts = requestMediaTypes(found.operation)
    if (c.form && !accepts.some((m) => m.startsWith('multipart/'))) wrong.push(`${where(c)} ${c.method} ${c.path}: sends a form, accepts ${accepts.join(', ') || 'no body'}`)
    if (parseJsonBody(c.body) !== undefined && accepts.length && !accepts.some((m) => m.includes('json'))) wrong.push(`${where(c)} ${c.method} ${c.path}: sends JSON, accepts ${accepts.join(', ')}`)
  }
  assert.deepEqual(wrong, [], `requests the API refuses with 415${report(wrong)}`)
})

test('documented enum values in request bodies are ones the API accepts', () => {
  const wrong = []
  for (const c of documentedCalls().filter((c) => c.kind === 'curl' || c.kind === 'http')) {
    const body = parseJsonBody(c.body)
    if (!body || Array.isArray(body)) continue
    const found = findOperation(openapi, c.method, c.path)
    if (!found) continue
    for (const [k, allowed] of requestEnums(openapi, found.operation)) {
      if (typeof body[k] === 'string' && body[k] !== 'x' && !allowed.includes(body[k])) wrong.push(`${where(c)} ${c.method} ${c.path}: ${k}=${body[k]} (one of ${allowed.join(', ')})`)
    }
  }
  assert.deepEqual(wrong, [], `values the API rejects${report(wrong)}`)
})

function backendSource() {
  const out = []
  const walk = (dir) => {
    for (const name of readdirSync(dir)) {
      const full = join(dir, name)
      if (statSync(full).isDirectory()) walk(full)
      else if (/\.(kt|java|ya?ml|properties)$/.test(name)) out.push(readFileSync(full, 'utf8'))
    }
  }
  walk(join(REPO_ROOT, 'backend', 'src', 'main'))
  return out.join('\n').toLowerCase()
}

test('every X- header in the docs is one the backend reads or sets', () => {
  const source = backendSource()
  const unknown = new Set()
  for (const { file, text } of docs) {
    text.split('\n').forEach((l, i) => {
      for (const [h] of l.matchAll(/\bX-[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9]/g)) {
        if (/^X-Forwarded-/i.test(h)) continue // read by the servlet container's forwarded-header support
        if (!source.includes(`"${h.toLowerCase()}"`)) unknown.add(`${file}:${i + 1} ${h}`)
      }
    })
  }
  assert.deepEqual([...unknown], [], `headers the backend does not know${report([...unknown])}`)
})

function composePorts() {
  // backend-command and backend-query publish "host:container" ports in docker-compose.yml.
  const yml = read('docker-compose.yml')
  const port = (service) => {
    const block = yml.split(/\n  (?=[a-z][\w-]*:\n)/).find((b) => b.startsWith(`${service}:`))
    assert.ok(block, `${service} is not in docker-compose.yml`)
    return block.match(/-\s*"(\d+):\d+"/)[1]
  }
  return { command: port('backend-command'), query: port('backend-query') }
}

test('the guide architecture shows the command and query services and their ports', () => {
  const { command, query } = composePorts()
  const guide = read(CANONICAL_GUIDE)
  const section = guide.split(/\n(?=##\s)/).find((s) => /^##\s.*Architecture/m.test(s))
  assert.ok(section, `${CANONICAL_GUIDE} has no Architecture section`)
  assert.match(section, new RegExp(`Command[^\\n]*:${command}\\b`, 'i'), `command service on :${command} missing`)
  assert.match(section, new RegExp(`Query[^\\n]*:${query}\\b`, 'i'), `query service on :${query} missing`)
  assert.match(section, /RabbitMQ/, 'the event bus between the services is missing')
  assert.doesNotMatch(section, /Backend \(Spring Boot :\d+\)/, 'still shows a single backend')
})

test('every port the docs give for the command and query APIs matches docker-compose.yml', () => {
  const { command, query } = composePorts()
  const wrong = []
  for (const { file, text } of docs) {
    text.split('\n').forEach((l, i) => {
      const m = l.match(/(Command|Query) (API|service)[^\n]*?localhost:(\d+)/i) ?? l.match(/(Command|Query)[^\n]*?:(\d{4})\b/)
      if (!m) return
      const kind = m[1].toLowerCase()
      const port = m[3] ?? m[2]
      if (port !== (kind === 'command' ? command : query) && ['8080', '8081'].includes(port)) wrong.push(`${file}:${i + 1} ${m[0]}`)
    })
  }
  assert.deepEqual(wrong, [], `ports that disagree with docker-compose.yml${report(wrong)}`)
})

test('every environment variable in the guide is one the stack reads', () => {
  const section = read(CANONICAL_GUIDE).split(/\n(?=###?\s)/).find((x) => /^###?\s.*Environment Variables/.test(x))
  assert.ok(section, `${CANONICAL_GUIDE} has no Environment Variables section`)
  const config = [
    ...readdirSync(join(REPO_ROOT, 'backend/src/main/resources')).filter((f) => /\.ya?ml$/.test(f)).map((f) => read(`backend/src/main/resources/${f}`)),
    read('docker-compose.yml')
  ].join('\n')
  const source = backendSource()
  const vars = [...section.matchAll(/^\|\s*`([A-Z][A-Z0-9_]+)`/gm)].map((m) => m[1])
  assert.ok(vars.length > 5, 'the Environment Variables table is empty')
  const unknown = vars.filter((v) =>
    !config.includes(`\${${v}`) && !new RegExp(`^\\s*${v}:`, 'm').test(config) &&
    !source.includes(v.toLowerCase().replace(/_/g, '.')))
  assert.deepEqual(unknown, [], `variables nothing reads${report(unknown)}`)
})

test('agent instructions require documentation to change with the code', () => {
  const files = ['CLAUDE.md', 'agents.md', 'AGENTS.md'].filter((f) => existsSync(join(REPO_ROOT, f)))
  assert.ok(files.length, 'no agent instructions file')
  for (const f of files) {
    const text = read(f)
    assert.match(text, /^##+\s.*Documentation/m, `${f} has no Documentation section`)
    assert.match(text, /every change[^\n]*(update|documentation)/i, `${f} does not say every change updates the docs`)
    assert.match(text, /node --test tools\/docs-drift\/\*\.test\.mjs/, `${f} does not name the drift tests`)
  }
})

test('documented factstore CLI commands and flags exist in the CLI source', () => {
  const { commands, flags } = cliVocabulary()
  const wrong = []
  for (const d of docs) {
    for (const c of cliInvocations(d.text)) {
      const problems = []
      // The command path is the leading words; the first unknown one in the first two
      // positions is a missing (sub)command, later ones are positional arguments.
      const path = c.words.slice(0, 2)
      for (const [i, w] of path.entries()) {
        if (w.startsWith('-') || commands.has(w)) { if (w.startsWith('-')) break; continue }
        if (/^[a-z][a-z-]*$/.test(w)) problems.push(`command ${c.words.slice(0, i + 1).join(' ')}`)
        break
      }
      for (const w of c.words) {
        if (!w.startsWith('--')) continue
        const name = w.slice(2).split('=')[0]
        if (name && !flags.has(name)) problems.push(`flag --${name}`)
      }
      if (problems.length) wrong.push(`${d.file}:${c.line} factstore ${c.words.slice(0, 3).join(' ')}: ${problems.join(', ')}`)
    }
  }
  assert.deepEqual(wrong, [], `the CLI rejects these${report(wrong)}`)
})

test('the tutorials CI runs are present and contain runnable steps', () => {
  for (const file of TUTORIALS) {
    assert.ok(existsSync(join(REPO_ROOT, file)), `${file} is missing`)
    const script = tutorialScript([file])
    assert.match(script, /\bcurl\b/, `${file} has no runnable curl steps`)
  }
})

test('tutorials capture ids from responses instead of hard-coding them', () => {
  const hardCoded = TUTORIALS.flatMap((file) =>
    codeBlocks(read(file)).flatMap((b) =>
      b.body.split('\n').filter((l) => /^\s*(export\s+)?[A-Z_]+_ID="?[0-9a-f]{8}-[0-9a-f]{4}-/.test(l)).map((l) => `${file}: ${l.trim()}`)))
  assert.deepEqual(hardCoded, [], `copy-pasting these ids fails on a real instance${report(hardCoded)}`)
})

test('CI runs the drift tests and the tutorials against docker compose', () => {
  const ci = read('.github/workflows/ci.yml')
  assert.match(ci, /node --test tools\/docs-drift\/\*\.test\.mjs/, 'ci.yml does not run the drift tests')
  assert.match(ci, /docker compose[^\n]*up/, 'ci.yml does not start the compose stack')
  assert.match(ci, /node tools\/docs-drift\/run-tutorials\.mjs/, 'ci.yml does not run the tutorials')
})
