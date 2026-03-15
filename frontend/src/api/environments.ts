import client from './client'
import type { Environment, EnvironmentSnapshot, CreateEnvironmentRequest, UpdateEnvironmentRequest, RecordSnapshotRequest } from '../types'

export interface AllowedArtifact {
  sha256: string
  name?: string
}

export const getEnvironments = () => client.get<Environment[]>('/environments')
export const getEnvironment = (id: string) => client.get<Environment>(`/environments/${id}`)
export const createEnvironment = (data: CreateEnvironmentRequest) =>
  client.post<Environment>('/environments', data)
export const updateEnvironment = (id: string, data: UpdateEnvironmentRequest) =>
  client.put<Environment>(`/environments/${id}`, data)
export const deleteEnvironment = (id: string) => client.delete(`/environments/${id}`)

export const listSnapshots = (environmentId: string) =>
  client.get<EnvironmentSnapshot[]>(`/environments/${environmentId}/snapshots`)
export const getLatestSnapshot = (environmentId: string) =>
  client.get<EnvironmentSnapshot>(`/environments/${environmentId}/snapshots/latest`)
export const getSnapshot = (environmentId: string, snapshotIndex: number) =>
  client.get<EnvironmentSnapshot>(`/environments/${environmentId}/snapshots/${snapshotIndex}`)
export const recordSnapshot = (environmentId: string, data: RecordSnapshotRequest) =>
  client.post<EnvironmentSnapshot>(`/environments/${environmentId}/snapshots`, data)

export const getEnvironmentAllowlist = (id: string) =>
  client.get<AllowedArtifact[]>(`/environments/${id}/allowlist`)
