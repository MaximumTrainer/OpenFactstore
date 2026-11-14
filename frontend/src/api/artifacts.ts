import client from './client'
import { v2 } from './v2'
import type { Artifact, BuildProvenance, CommandResult, ProvenanceVerification } from '../types'

export const getArtifacts = (trailId: string) =>
  client.get<Artifact[]>(`/trails/${trailId}/artifacts`, v2)
export const reportArtifact = (trailId: string, data: Partial<Artifact>) =>
  client
    .post<CommandResult>(`/trails/${trailId}/artifacts`, data, v2)
    .then((r) => getArtifacts(trailId).then((list) => ({ ...list, data: list.data.find((a) => a.id === r.data.id) as Artifact })))

export const recordProvenance = (trailId: string, artifactId: string, data: object) =>
  client.post<BuildProvenance>(`/trails/${trailId}/artifacts/${artifactId}/provenance`, data, v2)
export const getProvenance = (trailId: string, artifactId: string) =>
  client.get<BuildProvenance>(`/trails/${trailId}/artifacts/${artifactId}/provenance`, v2)
export const getProvenanceBySha256 = (sha256: string) =>
  client.get<BuildProvenance>(`/artifacts/${sha256}/provenance`, v2)
export const verifyProvenance = (trailId: string, artifactId: string) =>
  client.post<ProvenanceVerification>(`/trails/${trailId}/artifacts/${artifactId}/provenance/verify`, undefined, v2)
