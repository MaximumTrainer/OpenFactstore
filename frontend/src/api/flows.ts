import client from './client'
import type { Flow } from '../types'

export const getFlows = () => client.get<Flow[]>('/flows')
export const getFlow = (id: string) => client.get<Flow>(`/flows/${id}`)
export const createFlow = (data: {
  name: string
  description: string
  requiredAttestationTypes: string[]
  tags?: Record<string, string>
  visibility?: 'PUBLIC' | 'PRIVATE'
}) =>
  client.post<Flow>('/flows', data)
export const updateFlow = (id: string, data: {
  name?: string
  description?: string
  requiredAttestationTypes?: string[]
  tags?: Record<string, string>
  visibility?: 'PUBLIC' | 'PRIVATE'
}) =>
  client.put<Flow>(`/flows/${id}`, data)
