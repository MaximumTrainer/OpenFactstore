// The tutorials CI executes against a real stack (#249). Their shell blocks run in order in one
// bash session, so a step that drifts from the API fails the build.
//
// A whole file runs, unless it marks regions with <!-- docs-e2e:begin --> ... <!-- docs-e2e:end -->,
// in which case only those run. A block directly after <!-- docs-e2e:skip --> never runs: use it
// for setup the CI job has already done (cloning, starting the stack) and for placeholders.
import { codeBlocks, read } from './lib.mjs'

export const TUTORIALS = [
  'docs/user-guide.md',
  'docs/getting-started/02-setup.md',
  'docs/getting-started/03-authentication.md',
  'docs/getting-started/04-flows.md',
  'docs/getting-started/05-trails.md',
  'docs/getting-started/06-artifacts.md',
  'docs/getting-started/07-attestations.md'
]

const SHELL = new Set(['bash', 'sh', 'shell', 'console'])

/** The runnable shell blocks of a tutorial, as { file, line, body }. */
export function tutorialBlocks(file, text = read(file)) {
  const lines = text.split('\n')
  const regions = []
  let start = null
  lines.forEach((l, i) => {
    if (l.includes('<!-- docs-e2e:begin -->')) start = i + 1
    if (l.includes('<!-- docs-e2e:end -->') && start !== null) { regions.push([start, i + 1]); start = null }
  })
  return codeBlocks(text)
    .filter((b) => SHELL.has(b.lang))
    .filter((b) => !regions.length || regions.some(([s, e]) => b.line > s && b.line < e))
    .filter((b) => {
      let k = b.line - 2
      while (k >= 0 && !lines[k].trim()) k--
      return !(k >= 0 && lines[k].includes('<!-- docs-e2e:skip -->'))
    })
    .map((b) => ({ file, line: b.line, body: b.body }))
}

/** One bash script that runs the tutorials' steps in order and stops at the first failure. */
export function tutorialScript(files = TUTORIALS) {
  const parts = [
    'set -euo pipefail',
    // A step whose request fails must fail the run, as a reader would see it fail.
    'curl() { command curl --fail-with-body --show-error "$@"; }'
    // No credentials are passed in: the tutorials' own setup steps read them from .env, so CI
    // also proves those instructions work.
  ]
  for (const file of files) {
    for (const b of tutorialBlocks(file)) {
      parts.push(`printf '\\n\\n### %s:%s\\n' ${JSON.stringify(b.file)} ${b.line}`, b.body)
    }
  }
  parts.push("printf '\\n\\nAll tutorial steps passed.\\n'")
  return parts.join('\n') + '\n'
}
