import client from './client'

export interface DeploymentPolicy {
  id: string
  name: string
  policyType: string
  threshold?: number
  requireSignature: boolean
  policyEvaluator?: string
  wasmModuleContent?: string
}

export interface CreatePolicyRequest {
  name: string
  policyType: string
  threshold?: number
  requireSignature?: boolean
}

export const listPolicies = () => client.get<DeploymentPolicy[]>('/policies')
export const getPolicy = (id: string) => client.get<DeploymentPolicy>(`/policies/${id}`)
export const createPolicy = (data: CreatePolicyRequest) => client.post<DeploymentPolicy>('/policies', data)
export const deletePolicy = (id: string) => client.delete(`/policies/${id}`)
