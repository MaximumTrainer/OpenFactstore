// #250: the endpoint inventory (docs/api-inventory.md) is generated from docs/openapi.json and
// tools/endpoint-inventory/decisions.json. These tests keep the three in step: the committed
// inventory is current, every duplicate has a recorded decision, and what is deprecated in the
// spec is exactly what the decisions say. Run: node --test tools/docs-drift/*.test.mjs
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { REPO_ROOT, read, docFiles, curlCommands, endpointMentions, findOperation } from './lib.mjs'
import { loadInputs, operations, versionTwins, renderInventory, deprecatedByDecision } from '../endpoint-inventory/generate.mjs'

const { openapi, decisions } = loadInputs(REPO_ROOT)
const key = (o) => `${o.method} ${o.path}`

test('the committed endpoint inventory is what the generator produces', () => {
  assert.equal(read('docs/api-inventory.md'), renderInventory(openapi, decisions),
    'docs/api-inventory.md is stale; run: node tools/endpoint-inventory/generate.mjs')
})

test('every operation a decision names exists in the OpenAPI document', () => {
  const known = new Set(operations(openapi).map(key))
  const missing = decisions.groups.flatMap((g) =>
    [...g.operations, ...(g.deprecate ?? [])].filter((op) => !known.has(op)).map((op) => `${g.id}: ${op}`))
  assert.deepEqual(missing, [])
})

test('every v1/v2 twin is covered by a decision', () => {
  const decided = new Set(decisions.groups.flatMap((g) => g.operations))
  const undecided = versionTwins(openapi).flat().filter((op) => !decided.has(op))
  assert.deepEqual(undecided, [], 'add these to tools/endpoint-inventory/decisions.json')
})

test('every decision is keep, deprecate or deprecate-after-parity, with a reason', () => {
  const bad = decisions.groups.filter((g) =>
    !['keep', 'deprecate', 'deprecate-after-parity'].includes(g.decision) || !g.reason?.trim() ||
    (g.decision === 'deprecate') !== Boolean(g.deprecate?.length) || g.operations.length < 2)
  assert.deepEqual(bad.map((g) => g.id), [])
})

test('the operations deprecated in the spec are exactly those the decisions deprecate', () => {
  const inSpec = operations(openapi).filter((o) => o.deprecated).map(key).sort()
  assert.deepEqual(inSpec, [...deprecatedByDecision(decisions)].sort())
})

test('the docs do not tell readers to call a deprecated endpoint', () => {
  const deprecated = new Set(deprecatedByDecision(decisions))
  // The inventory, the deprecation notice and the generated matrix name them on purpose; anywhere
  // else a deprecated endpoint may only be named on a line that says it is deprecated.
  const allowed = new Set(['docs/api-inventory.md', 'docs/upgrading/api-deprecations.md', 'docs/api-authorisation-matrix.md'])
  const uses = docFiles().filter((f) => !allowed.has(f)).flatMap((file) => {
    const text = read(file)
    const lines = text.split('\n')
    const flagged = (c) => /deprecated/i.test(lines[c.line - 1] ?? '')
    return [...curlCommands(text, file), ...endpointMentions(text, file)]
      .filter((c) => c.path && !flagged(c))
      .map((c) => ({ ...c, found: findOperation(openapi, c.method ?? 'GET', c.path) }))
      .filter((c) => c.found && deprecated.has(`${(c.method ?? 'GET').toUpperCase()} ${c.found.template}`))
      .map((c) => `${c.file}:${c.line} ${c.method} ${c.path}`)
  })
  assert.deepEqual(uses, [], 'point these at the successor endpoint')
})
