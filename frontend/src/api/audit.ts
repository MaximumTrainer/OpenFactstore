import client from './client'
import { v2 } from './v2'
import type { AuditEvent, AuditEventPage, AuditEventType } from '../types'

export const getAuditEvents = (params?: {
  eventType?: AuditEventType
  trailId?: string
  actor?: string
  from?: string
  to?: string
  page?: number
  size?: number
  sortDesc?: boolean
}) => client.get<AuditEventPage>('/audit', { params })

export const getAuditEvent = (id: string) => client.get<AuditEvent>(`/audit/${id}`)

export const getTrailAuditEvents = (trailId: string) =>
  client.get<AuditEvent[]>(`/trails/${trailId}/audit`, v2)
