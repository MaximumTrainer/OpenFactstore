import client from './client'
import type { AssertResult } from '../types'

export const assertCompliance = (sha256Digest: string, flowId: string) =>
  client.post<AssertResult>('/assert', { sha256Digest, flowId })

export const getChainOfCustody = (sha256: string) =>
  client.get(`/compliance/artifact/${sha256}`)
