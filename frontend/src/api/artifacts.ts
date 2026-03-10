import client from './client'
import type { Artifact } from '../types'

export const getArtifacts = (trailId: string) =>
  client.get<Artifact[]>(`/trails/${trailId}/artifacts`)
export const reportArtifact = (trailId: string, data: Partial<Artifact>) =>
  client.post<Artifact>(`/trails/${trailId}/artifacts`, data)
