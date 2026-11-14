#!/usr/bin/env node
// Runs the tutorials' shell steps against a running stack (#249).
// Usage, from a checkout whose stack is up (scripts/bootstrap-env.sh, then docker compose up):
//   node tools/docs-drift/run-tutorials.mjs [--print]
// The steps run in the repository root, where they read the bootstrap key from .env.
import { spawnSync } from 'node:child_process'
import { REPO_ROOT } from './lib.mjs'
import { tutorialScript } from './tutorials.mjs'

const script = tutorialScript()
if (process.argv.includes('--print')) {
  process.stdout.write(script)
  process.exit(0)
}
const result = spawnSync('bash', ['-c', script], { stdio: 'inherit', env: process.env, cwd: REPO_ROOT })
process.exit(result.status ?? 1)
