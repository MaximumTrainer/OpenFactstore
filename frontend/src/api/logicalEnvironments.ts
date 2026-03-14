import client from './client'
import type {
  LogicalEnvironment,
  CreateLogicalEnvironmentRequest,
  UpdateLogicalEnvironmentRequest,
  MergedSnapshotResponse
} from '../types'

export const logicalEnvironmentsApi = {
  list() {
    return client.get<LogicalEnvironment[]>('/logical-environments')
  },

  get(id: string) {
    return client.get<LogicalEnvironment>(`/logical-environments/${id}`)
  },

  create(request: CreateLogicalEnvironmentRequest) {
    return client.post<LogicalEnvironment>('/logical-environments', request)
  },

  update(id: string, request: UpdateLogicalEnvironmentRequest) {
    return client.put<LogicalEnvironment>(`/logical-environments/${id}`, request)
  },

  delete(id: string) {
    return client.delete(`/logical-environments/${id}`)
  },

  addMember(id: string, physicalEnvId: string) {
    return client.post<LogicalEnvironment>(`/logical-environments/${id}/members/${physicalEnvId}`)
  },

  removeMember(id: string, physicalEnvId: string) {
    return client.delete(`/logical-environments/${id}/members/${physicalEnvId}`)
  },

  getMergedSnapshot(id: string) {
    return client.get<MergedSnapshotResponse>(`/logical-environments/${id}/snapshots/latest`)
  }
}
