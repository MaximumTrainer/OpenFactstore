import client from './client'
import { v2 } from './v2'
import type { Attestation, CommandResult } from '../types'

export const getAttestations = (trailId: string) =>
  client.get<Attestation[]>(`/trails/${trailId}/attestations`, v2)

/** v2 answers with `{ id, status }`; the attestation is read back as the server judged it. */
export const createAttestation = (trailId: string, data: Partial<Attestation>) =>
  client
    .post<CommandResult>(`/trails/${trailId}/attestations`, data, v2)
    .then((r) => getAttestations(trailId).then((list) => ({ ...list, data: list.data.find((a) => a.id === r.data.id) as Attestation })))
