#!/usr/bin/env node
// Runs the tutorials' shell steps against a running stack (#249).
// Usage: BASE_URL=http://localhost:8080 FACTSTORE_TOKEN=<admin key> node tools/docs-drift/run-tutorials.mjs [--print]
import { spawnSync } from 'node:child_process'
import { tutorialScript } from './tutorials.mjs'

const script = tutorialScript()
if (process.argv.includes('--print')) {
  process.stdout.write(script)
  process.exit(0)
}
const result = spawnSync('bash', ['-c', script], { stdio: 'inherit', env: process.env })
process.exit(result.status ?? 1)
