import client from './client'
import type { FlowComplianceReport, AuditTrailExport } from '../types'

export const getComplianceReport = (flowId?: string, from?: string, to?: string) =>
  client.get<FlowComplianceReport>('/reports/compliance', {
    params: {
      ...(flowId ? { flowId } : {}),
      ...(from ? { from } : {}),
      ...(to ? { to } : {})
    }
  })

export const getAuditTrailExport = (trailId: string) =>
  client.get<AuditTrailExport>(`/reports/audit-trail/${trailId}`)
