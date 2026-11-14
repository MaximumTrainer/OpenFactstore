import { describe, test, expect } from 'vitest'
import { PactV3, MatchersV3, SpecificationVersion } from '@pact-foundation/pact'
import axios from 'axios'
import { fileURLToPath } from 'url'
import { dirname, resolve } from 'path'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

const { like, eachLike } = MatchersV3

const PACT_DIR = resolve(__dirname, '../../../pacts')

/**
 * A **fresh** provider per test.
 *
 * One shared `PactV3` was reused across every test in this file, and interactions accumulate
 * on the instance: if two tests overlap at all, one test's mock server ends up expecting the
 * other's request and fails with "the following request was expected but not received" --
 * naming an endpoint that test never calls.
 *
 * It usually passed, because the tests usually ran strictly one after another. That made it an
 * intermittent contract failure on CI, which is the worst kind: the signal a contract test
 * exists to give is only worth anything if a red run means the contract actually broke.
 * Running this file with `--sequence.concurrent` reproduces the CI failure exactly.
 */
const newProvider = () =>
  new PactV3({
    consumer: 'factstore-cli',
    provider: 'factstore-backend',
    dir: PACT_DIR,
    spec: SpecificationVersion.SPECIFICATION_VERSION_V3,
  })

describe('Factstore CLI API Contract – Flows', () => {
  test('GET /api/v2/flows returns available flows for selection', async () => {
    await newProvider()
      .given('flows exist')
      .uponReceiving('a CLI GET request for all flows')
      .withRequest({ method: 'GET', path: '/api/v2/flows' })
      .willRespondWith({
        status: 200,
        body: eachLike({
          id: like('7f3f2b99-0000-0000-0000-000000000001'),
          name: like('test-flow'),
          description: like('A test flow'),
          requiredAttestationTypes: like(['junit']),
          tags: like({}),
          createdAt: like('2024-01-01T00:00:00Z'),
          updatedAt: like('2024-01-01T00:00:00Z'),
        }),
      })
      .executeTest(async (mockServer) => {
        const response = await axios.get(`${mockServer.url}/api/v2/flows`)
        expect(response.status).toBe(200)
        expect(Array.isArray(response.data)).toBe(true)
        expect(response.data[0]).toHaveProperty('id')
        expect(response.data[0]).toHaveProperty('name')
        expect(response.data[0]).toHaveProperty('requiredAttestationTypes')
        expect(response.data[0]).toHaveProperty('tags')
      })
  })
})

describe('Factstore CLI API Contract – Assert', () => {
  test('POST /api/v1/assert reports compliance status', async () => {
    const requestBody = {
      sha256Digest: 'sha256:deadbeef',
      flowId: '7f3f2b99-0000-0000-0000-000000000001',
    }

    await newProvider()
      .given('a flow with id 7f3f2b99-0000-0000-0000-000000000001 exists')
      .uponReceiving('a CLI POST request to assert compliance')
      .withRequest({
        method: 'POST',
        path: '/api/v1/assert',
        body: like(requestBody),
      })
      .willRespondWith({
        status: 200,
        body: like({
          sha256Digest: like('sha256:deadbeef'),
          flowId: like('7f3f2b99-0000-0000-0000-000000000001'),
          status: like('NON_COMPLIANT'),
          missingAttestationTypes: like([]),
          failedAttestationTypes: like([]),
          details: like('No artifacts found with digest sha256:deadbeef'),
        }),
      })
      .executeTest(async (mockServer) => {
        const response = await axios.post(
          `${mockServer.url}/api/v1/assert`,
          requestBody,
          { headers: { 'Content-Type': 'application/json' } }
        )
        expect(response.status).toBe(200)
        expect(response.data).toHaveProperty('status')
        expect(['COMPLIANT', 'NON_COMPLIANT']).toContain(response.data.status)
        expect(response.data).toHaveProperty('missingAttestationTypes')
      })
  })

  test('POST /api/v2/trails creates a new trail', async () => {
    const requestBody = {
      flowId: '7f3f2b99-0000-0000-0000-000000000001',
      gitCommitSha: 'abc123def456',
      gitBranch: 'main',
      gitAuthor: 'developer',
      gitAuthorEmail: 'dev@example.com',
    }

    await newProvider()
      .given('a flow with id 7f3f2b99-0000-0000-0000-000000000001 exists')
      .uponReceiving('a CLI POST request to create a trail')
      .withRequest({
        method: 'POST',
        path: '/api/v2/trails',
        body: like(requestBody),
      })
      .willRespondWith({
        status: 201,
        // A v2 write answers with the id and what happened (#250: the v1 twin is deprecated).
        body: like({
          id: like('8a4e3c00-0000-0000-0000-000000000001'),
          status: like('created'),
        }),
      })
      .executeTest(async (mockServer) => {
        const response = await axios.post(
          `${mockServer.url}/api/v2/trails`,
          requestBody,
          { headers: { 'Content-Type': 'application/json' } }
        )
        expect(response.status).toBe(201)
        expect(response.data).toHaveProperty('id')
        expect(response.data).toHaveProperty('status')
      })
  })
})
