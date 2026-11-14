import client from './client'
import { v2 } from './v2'
import type { CommandResult, Trail, TrailCascadeCounts, TrailCleanupRequest, TrailCleanupResult } from '../types'

export const getTrails = (flowId?: string, includeArchived = false) =>
  client.get<Trail[]>('/trails', {
    ...v2,
    params: { ...(flowId ? { flowId } : {}), includeArchived },
  })

export const getTrail = (id: string) => client.get<Trail>(`/trails/${id}`, v2)

export const createTrail = (data: Partial<Trail>) =>
  client.post<CommandResult>('/trails', data, v2).then((r) => getTrail(r.data.id))

/** Trails are archived, never deleted (#245): the trail leaves the default listings and becomes read-only; its evidence is kept. */
export const archiveTrail = (id: string) => client.post<Trail>(`/trails/${id}/archive`, {})

export const unarchiveTrail = (id: string) => client.post<Trail>(`/trails/${id}/unarchive`, {})

/** How much evidence the trail holds, all of which archiving keeps. */
export const getTrailCascade = (id: string) => client.get<TrailCascadeCounts>(`/trails/${id}/cascade`)

/** Bulk archive. Defaults to a dry run on the server, so always send `dryRun` explicitly. */
export const cleanupTrails = (request: TrailCleanupRequest) =>
  client.post<TrailCleanupResult>('/trails/cleanup', request)
