import { describe, it, expect, vi, beforeEach } from 'vitest'

// #250: the /api/v1 twins of these calls are deprecated; the web app uses /api/v2. A v2 write
// answers with { id, status }, so the record is read back and callers still get the record.
const calls: { method: string; url: string; baseURL?: string; data?: unknown }[] = []
vi.mock('./client', () => {
  const respond = (method: string) => (url: string, a?: unknown, b?: unknown) => {
    const config = (method === 'get' || method === 'delete' ? a : b) as { baseURL?: string } | undefined
    const data = method === 'get' || method === 'delete' ? undefined : a
    calls.push({ method, url, baseURL: config?.baseURL, data })
    const list = /\/(artifacts|attestations)$/.test(url)
    const body = method !== 'get' ? { id: 'new-id', status: 'created' }
      : list ? [{ id: 'other' }, { id: 'new-id', read: true }] : { id: url.split('/').pop(), read: true }
    return Promise.resolve({ data: body })
  }
  return { default: { get: respond('get'), post: respond('post'), put: respond('put'), delete: respond('delete') } }
})

import * as flows from './flows'
import * as trails from './trails'
import * as artifacts from './artifacts'
import * as attestations from './attestations'
import * as audit from './audit'

beforeEach(() => { calls.length = 0 })

const V2 = '/api/v2'

describe('the web app calls /api/v2 where /api/v1 is deprecated', () => {
  it('reads flows, trails, artifacts, attestations and trail audit from v2', async () => {
    await flows.getFlows()
    await flows.getFlow('f1')
    await trails.getTrails('f1')
    await trails.getTrail('t1')
    await artifacts.getArtifacts('t1')
    await artifacts.getProvenance('t1', 'a1')
    await artifacts.getProvenanceBySha256('sha256:x')
    await attestations.getAttestations('t1')
    await audit.getTrailAuditEvents('t1')
    expect(calls.map((c) => c.baseURL)).toEqual(calls.map(() => V2))
  })

  it('creates on v2 and returns the record read back from v2', async () => {
    const created = await flows.createFlow({ name: 'f', description: '', requiredAttestationTypes: [] })
    expect(calls[0]).toMatchObject({ method: 'post', url: '/flows', baseURL: V2 })
    expect(calls[1]).toMatchObject({ method: 'get', url: '/flows/new-id', baseURL: V2 })
    expect(created.data).toMatchObject({ id: 'new-id', read: true })
  })

  it('updates a flow on v2 and returns it read back', async () => {
    const updated = await flows.updateFlow('f1', { description: 'd' })
    expect(calls.map((c) => [c.method, c.url, c.baseURL])).toEqual([['put', '/flows/f1', V2], ['get', '/flows/f1', V2]])
    expect(updated.data).toMatchObject({ id: 'f1', read: true })
  })

  it('creates trails, artifacts and attestations on v2', async () => {
    await trails.createTrail({ flowId: 'f1' })
    await artifacts.reportArtifact('t1', { imageName: 'app' })
    await artifacts.recordProvenance('t1', 'a1', {})
    await artifacts.verifyProvenance('t1', 'a1')
    const attestation = await attestations.createAttestation('t1', { type: 'junit' })
    expect(attestation.data).toMatchObject({ id: 'new-id', read: true })
    expect(calls.filter((c) => c.method !== 'get').map((c) => c.baseURL)).toEqual([V2, V2, V2, V2, V2])
  })
})
