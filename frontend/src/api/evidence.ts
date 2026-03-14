import client from './client'
import type {
  BulkEvidenceRequest,
  BulkEvidenceResponse,
  CoverageReport,
  EvidenceGapsResponse,
  EvidenceSummary,
  ReportCoverageRequest,
} from '../types'

export const reportCoverage = (trailId: string, data: ReportCoverageRequest) =>
  client.post<CoverageReport>(`/trails/${trailId}/coverage`, data)

export const getCoverageReports = (trailId: string) =>
  client.get<CoverageReport[]>(`/trails/${trailId}/coverage`)

export const collectBulkEvidence = (data: BulkEvidenceRequest) =>
  client.post<BulkEvidenceResponse>('/evidence/collect', data)

export const getEvidenceSummary = (trailId: string) =>
  client.get<EvidenceSummary>(`/trails/${trailId}/evidence-summary`)

export const getEvidenceGaps = () =>
  client.get<EvidenceGapsResponse>('/evidence/gaps')
