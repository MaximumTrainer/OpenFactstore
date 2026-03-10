import client from './client'
import type { Attestation } from '../types'

export const getAttestations = (trailId: string) =>
  client.get<Attestation[]>(`/trails/${trailId}/attestations`)
export const createAttestation = (trailId: string, data: Partial<Attestation>) =>
  client.post<Attestation>(`/trails/${trailId}/attestations`, data)
