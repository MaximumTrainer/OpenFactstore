import { describe, test, expect } from 'vitest'
import { PactV3, MatchersV3, SpecificationVersion } from '@pact-foundation/pact'
import axios from 'axios'
import { fileURLToPath } from 'url'
import { dirname, resolve } from 'path'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

const { like, eachLike } = MatchersV3

const PACT_DIR = resolve(__dirname, '../../../pacts')

const provider = new PactV3({
  consumer: 'factstore-frontend',
  provider: 'factstore-backend',
  dir: PACT_DIR,
  spec: SpecificationVersion.SPECIFICATION_VERSION_V3,
})

describe('Factstore API Contract – Flows', () => {
  test('GET /api/v1/flows returns a list of flows', async () => {
    await provider
      .given('flows exist')
      .uponReceiving('a GET request for all flows')
      .withRequest({ method: 'GET', path: '/api/v1/flows' })
      .willRespondWith({
        status: 200,
        body: eachLike({
          id: like('7f3f2b99-0000-0000-0000-000000000001'),
          name: like('test-flow'),
          description: like('A test flow'),
          requiredAttestationTypes: like(['junit']),
          createdAt: like('2024-01-01T00:00:00Z'),
          updatedAt: like('2024-01-01T00:00:00Z'),
        }),
      })
      .executeTest(async (mockServer) => {
        const response = await axios.get(`${mockServer.url}/api/v1/flows`)
        expect(response.status).toBe(200)
        expect(Array.isArray(response.data)).toBe(true)
        expect(response.data[0]).toHaveProperty('id')
        expect(response.data[0]).toHaveProperty('name')
      })
  })

  test('POST /api/v1/flows creates a flow', async () => {
    const requestBody = {
      name: 'new-flow',
      description: 'A new flow',
      requiredAttestationTypes: ['junit', 'snyk'],
    }

    await provider
      .given('no state needed')
      .uponReceiving('a POST request to create a flow')
      .withRequest({
        method: 'POST',
        path: '/api/v1/flows',
        body: like(requestBody),
      })
      .willRespondWith({
        status: 201,
        body: like({
          id: like('7f3f2b99-0000-0000-0000-000000000002'),
          name: like('new-flow'),
          description: like('A new flow'),
          requiredAttestationTypes: like(['junit', 'snyk']),
          createdAt: like('2024-01-01T00:00:00Z'),
          updatedAt: like('2024-01-01T00:00:00Z'),
        }),
      })
      .executeTest(async (mockServer) => {
        const response = await axios.post(
          `${mockServer.url}/api/v1/flows`,
          requestBody,
          { headers: { 'Content-Type': 'application/json' } }
        )
        expect(response.status).toBe(201)
        expect(response.data).toHaveProperty('id')
        expect(response.data.name).toBe('new-flow')
      })
  })
})

describe('Factstore API Contract – Assert', () => {
  test('POST /api/v1/assert returns compliance result', async () => {
    const requestBody = {
      sha256Digest: 'sha256:abc123',
      flowId: '7f3f2b99-0000-0000-0000-000000000001',
    }

    await provider
      .given('a flow with id 7f3f2b99-0000-0000-0000-000000000001 exists')
      .uponReceiving('a POST request to assert compliance')
      .withRequest({
        method: 'POST',
        path: '/api/v1/assert',
        body: like(requestBody),
      })
      .willRespondWith({
        status: 200,
        body: like({
          sha256Digest: like('sha256:abc123'),
          flowId: like('7f3f2b99-0000-0000-0000-000000000001'),
          status: like('NON_COMPLIANT'),
          missingAttestationTypes: like([]),
          failedAttestationTypes: like([]),
          details: like('No artifacts found with digest sha256:abc123'),
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
      })
  })
})
