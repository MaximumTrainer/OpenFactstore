import client from './client'
import type { Trail } from '../types'

export const getTrails = (flowId?: string) =>
  client.get<Trail[]>('/trails', { params: flowId ? { flowId } : {} })
export const getTrail = (id: string) => client.get<Trail>(`/trails/${id}`)
export const createTrail = (data: Partial<Trail>) => client.post<Trail>('/trails', data)
