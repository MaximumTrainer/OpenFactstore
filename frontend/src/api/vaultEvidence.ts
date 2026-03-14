import client from './client'
import type {
  VaultEvidenceResponse,
  VaultEvidenceListResponse,
  VaultHealthResponse,
  StoreEvidenceRequest,
} from '../types'

export const storeEvidence = (
  entityType: string,
  entityId: string,
  request: StoreEvidenceRequest,
) => client.post<VaultEvidenceResponse>(`/evidence/${entityType}/${entityId}`, request)

export const retrieveEvidence = (
  entityType: string,
  entityId: string,
  evidenceType: string,
) =>
  client.get<VaultEvidenceResponse>(`/evidence/${entityType}/${entityId}`, {
    params: { evidenceType },
  })

export const listEvidence = (entityType: string, entityId: string) =>
  client.get<VaultEvidenceListResponse>(`/evidence/${entityType}/${entityId}/list`)

export const downloadEvidence = (
  entityType: string,
  entityId: string,
  evidenceType: string,
) =>
  client.get<Record<string, string>>(`/evidence/${entityType}/${entityId}/download`, {
    params: { evidenceType },
  })

export const deleteEvidence = (
  entityType: string,
  entityId: string,
  evidenceType: string,
) =>
  client.delete(`/evidence/${entityType}/${entityId}`, {
    params: { evidenceType },
  })

export const getVaultHealth = () => client.get<VaultHealthResponse>('/evidence/health')
