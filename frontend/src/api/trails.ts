import client from './client'
import { v2 } from './v2'
import type { CommandResult, Trail, TrailCascadeCounts, TrailCleanupRequest, TrailCleanupResult, TrailDeletion } from '../types'

export const getTrails = (flowId?: string, includeArchived = false) =>
  client.get<Trail[]>('/trails', {
    ...v2,
    params: { ...(flowId ? { flowId } : {}), includeArchived },
  })

export const getTrail = (id: string) => client.get<Trail>(`/trails/${id}`, v2)

export const createTrail = (data: Partial<Trail>) =>
  client.post<CommandResult>('/trails', data, v2).then((r) => getTrail(r.data.id))

/** Soft delete: the trail leaves the default listings, its evidence is retained. */
export const archiveTrail = (id: string) => client.post<Trail>(`/trails/${id}/archive`, {})

export const unarchiveTrail = (id: string) => client.post<Trail>(`/trails/${id}/unarchive`, {})

/** What deleting this trail would remove — shown before the user is asked to confirm. */
export const getTrailCascade = (id: string) => client.get<TrailCascadeCounts>(`/trails/${id}/cascade`)

/** Permanent. Cascades to the trail's evidence; the audit log and ledger are left intact. */
export const deleteTrail = (id: string) => client.delete<TrailDeletion>(`/trails/${id}`)

/** Bulk cleanup. Defaults to a dry run on the server, so always send `dryRun` explicitly. */
export const cleanupTrails = (request: TrailCleanupRequest) =>
  client.post<TrailCleanupResult>('/trails/cleanup', request)
