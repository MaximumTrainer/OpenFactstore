// #242: the Compose stack ships no usable default credentials. RabbitMQ, Grafana and the SCM
// token key come from .env (written by scripts/bootstrap-env.sh) with no insecure fallback, so
// `docker compose config` fails when they are missing. Run: node --test tools/docs-drift/*.test.mjs
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { REPO_ROOT, read } from './lib.mjs'

const compose = read('docker-compose.yml')
const REQUIRED = [
  'RABBITMQ_DEFAULT_USER', 'RABBITMQ_DEFAULT_PASS',
  'GF_SECURITY_ADMIN_USER', 'GF_SECURITY_ADMIN_PASSWORD',
  'SCM_ENCRYPTION_KEY'
]

test('compose reads credentials from the environment with no fallback', () => {
  for (const name of REQUIRED) {
    assert.match(compose, new RegExp(`\\$\\{${name}:\\?[^}]+\\}`), `${name} must be \${${name}:?message}`)
    assert.doesNotMatch(compose, new RegExp(`\\$\\{${name}:-`), `${name} must not have a default`)
  }
  assert.doesNotMatch(compose, /:\s*guest\s*$/m, 'no guest/guest RabbitMQ credentials')
  assert.doesNotMatch(compose, /changeme/i, 'no changeme password')
})

test('bootstrap-env.sh generates every required credential', () => {
  const script = read('scripts/bootstrap-env.sh')
  for (const name of REQUIRED) assert.match(script, new RegExp(`^${name}=`, 'm'), `bootstrap-env.sh must write ${name}`)
})

const dockerCompose = spawnSync('docker', ['compose', 'version'], { encoding: 'utf8' })
const haveCompose = dockerCompose.status === 0

function composeConfig(values) {
  const dir = mkdtempSync(join(tmpdir(), 'compose-env-'))
  const envFile = join(dir, '.env')
  writeFileSync(envFile, Object.entries(values).map(([k, v]) => `${k}=${v}`).join('\n') + '\n')
  // A clean environment, so neither the caller's shell nor the repository's own .env leaks in.
  return spawnSync('docker', ['compose', '--env-file', envFile, 'config', '--quiet'], {
    cwd: REPO_ROOT, encoding: 'utf8', env: { PATH: process.env.PATH, HOME: process.env.HOME }
  })
}

const complete = Object.fromEntries(REQUIRED.map((n) => [n, n === 'SCM_ENCRYPTION_KEY' ? 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=' : 'x1']))

test('docker compose config fails without RabbitMQ or Grafana credentials', { skip: !haveCompose && 'docker compose not installed' }, () => {
  for (const missing of REQUIRED) {
    const values = { ...complete }
    delete values[missing]
    const result = composeConfig(values)
    assert.notEqual(result.status, 0, `compose config must fail without ${missing}`)
    assert.match(result.stderr, new RegExp(missing), `the error must name ${missing}`)
  }
  const ok = composeConfig(complete)
  assert.equal(ok.status, 0, ok.stderr)
})
