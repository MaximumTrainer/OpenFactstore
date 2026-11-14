// Unit tests for the extraction the drift tests rely on (#249).
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { curlCommands, endpointMentions, tableEndpoints, httpBlockRequests, pathMatches, parseJsonBody, codeBlocks } from './lib.mjs'

const md = (s) => s.replace(/^\n/, '')

test('curl: method, path, headers and body across continuation lines', () => {
  const [c] = curlCommands(md(`
\`\`\`bash
curl -s -X PUT "$BASE_URL/api/v1/flows/$FLOW_ID?x=1" \\
  -H "Authorization: Bearer $FACTSTORE_TOKEN" \\
  -H 'Content-Type: application/json' \\
  -d '{"description": "d"}' | jq .
\`\`\`
`), 'f.md')
  assert.equal(c.method, 'PUT')
  assert.equal(c.path, '/api/v1/flows/$FLOW_ID')
  assert.deepEqual(c.headers, ['Authorization: Bearer $FACTSTORE_TOKEN', 'Content-Type: application/json'])
  assert.equal(c.body, '{"description": "d"}')
  assert.equal(c.line, 2)
})

test('curl: data implies POST; command substitution and several calls per block', () => {
  const calls = curlCommands(md(`
\`\`\`
FLOW_ID=$(curl -s "$BASE_URL/api/v1/flows" -d '{}' | jq -r .id)
curl "$BASE_URL/api/v1/flows/$FLOW_ID"
\`\`\`
`))
  assert.deepEqual(calls.map((c) => [c.method, c.path, c.line]), [['POST', '/api/v1/flows', 2], ['GET', '/api/v1/flows/$FLOW_ID', 3]])
})

test('curl in an HTML page', () => {
  const [c] = curlCommands('<!DOCTYPE html>\n<pre><code>curl -H "Authorization: Bearer x" http://h/api/v1/trails/&lt;id&gt;</code></pre>')
  assert.equal(c.path, '/api/v1/trails/<id>')
  assert.equal(c.headers.length, 1)
})

test('endpoints in prose, tables and http blocks', () => {
  assert.deepEqual(endpointMentions('call `POST /api/v1/assert` then').map((m) => m.path), ['/api/v1/assert'])
  assert.deepEqual(tableEndpoints('| `GET` | `/api/v1/flows/{id}` | Get |').map((m) => [m.method, m.path]), [['GET', '/api/v1/flows/{id}']])
  const [r] = httpBlockRequests(md(`
\`\`\`http
POST /api/v1/trails
Content-Type: application/json

{ "flowId": "x" }
\`\`\`
`))
  assert.equal(r.method, 'POST')
  assert.equal(r.body, '{ "flowId": "x" }')
})

test('path matching: placeholders only match parameters, literals match literals or parameters', () => {
  assert.ok(pathMatches('/api/v1/flows/$FLOW_ID', '/api/v1/flows/{id}'))
  assert.ok(pathMatches('/api/v1/flows/<flowId>', '/api/v1/flows/{id}'))
  assert.ok(pathMatches('/api/v1/flows/abc', '/api/v1/flows/{id}'))
  assert.ok(!pathMatches('/api/v1/flows/$FLOW_ID', '/api/v1/flows/search'))
  assert.ok(!pathMatches('/api/v1/flows', '/api/v1/flows/{id}'))
})

test('JSON bodies with shell variables parse; non-JSON does not', () => {
  assert.deepEqual(parseJsonBody('{"flowId": "$FLOW_ID", "n": $N}'), { flowId: 'x', n: 0 })
  assert.equal(parseJsonBody('name=x'), undefined)
})

test('code blocks keep their language and opening line', () => {
  assert.deepEqual(codeBlocks('a\n```bash\nx\n```\n').map((b) => [b.lang, b.line, b.body]), [['bash', 2, 'x']])
})
