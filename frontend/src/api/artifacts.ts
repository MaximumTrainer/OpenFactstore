import client from './client'
import type { Artifact, BuildProvenance, ProvenanceVerification } from '../types'

export const getArtifacts = (trailId: string) =>
  client.get<Artifact[]>(`/trails/${trailId}/artifacts`)
export const reportArtifact = (trailId: string, data: Partial<Artifact>) =>
  client.post<Artifact>(`/trails/${trailId}/artifacts`, data)

export const recordProvenance = (trailId: string, artifactId: string, data: object) =>
  client.post<BuildProvenance>(`/trails/${trailId}/artifacts/${artifactId}/provenance`, data)
export const getProvenance = (trailId: string, artifactId: string) =>
  client.get<BuildProvenance>(`/trails/${trailId}/artifacts/${artifactId}/provenance`)
export const getProvenanceBySha256 = (sha256: string) =>
  client.get<BuildProvenance>(`/artifacts/${sha256}/provenance`)
export const verifyProvenance = (trailId: string, artifactId: string) =>
  client.post<ProvenanceVerification>(`/trails/${trailId}/artifacts/${artifactId}/provenance/verify`)
