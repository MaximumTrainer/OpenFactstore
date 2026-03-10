import client from './client'
import type { Flow } from '../types'

export const getFlows = () => client.get<Flow[]>('/flows')
export const getFlow = (id: string) => client.get<Flow>(`/flows/${id}`)
export const createFlow = (data: { name: string; description: string; requiredAttestationTypes: string[] }) =>
  client.post<Flow>('/flows', data)
