import client from './client'
import { v2 } from './v2'
import type { CommandResult, Flow, FlowImpact, TemplateDrift } from '../types'

export const getFlows = (includeArchived = false) =>
  client.get<Flow[]>(`/flows?includeArchived=${includeArchived}`, v2)

export const getFlow = (id: string) => client.get<Flow>(`/flows/${id}`, v2)

export const createFlow = (data: {
  name: string
  description: string
  requiredAttestationTypes: string[]
  tags?: Record<string, string>
  templateYaml?: string
  requiresApproval?: boolean
  requiredApproverRoles?: string[]
  /** Apply hub templates at creation; their YAML is copied onto the flow. */
  templateIds?: string[]
}) => client.post<CommandResult>('/flows', data, v2).then((r) => getFlow(r.data.id))

/**
 * Updates a flow definition. Every field is optional and only what is sent is changed.
 *
 * The fields here mirror the server's `UpdateFlowRequest` exactly. An earlier version omitted
 * `templateYaml`, `requiresApproval` and `requiredApproverRoles` — so they could not be edited at
 * all — and sent a `visibility` field the backend has never had, which was silently discarded.
 */
export interface UpdateFlowPayload {
  name?: string
  description?: string
  requiredAttestationTypes?: string[]
  tags?: Record<string, string>
  templateYaml?: string
  requiresApproval?: boolean
  requiredApproverRoles?: string[]
}

/** v2 answers a write with `{ id, status }`, so the updated flow is read back. */
export const updateFlow = (id: string, data: UpdateFlowPayload) =>
  client.put<CommandResult>(`/flows/${id}`, data, v2).then(() => getFlow(id))

/** How much existing evidence a change to this flow would affect. */
export const getFlowImpact = (id: string) => client.get<FlowImpact>(`/flows/${id}/impact`)

/** Whether the flow still matches the template it was created from. */
export const getFlowTemplateDrift = (id: string) =>
  client.get<TemplateDrift>(`/flows/${id}/template-drift`)

export const renameFlow = (id: string, newName: string) =>
  client.post<Flow>(`/flows/${id}/rename`, { newName })

export const archiveFlow = (id: string) => client.post<Flow>(`/flows/${id}/archive`, {})

export const unarchiveFlow = (id: string) => client.post<Flow>(`/flows/${id}/unarchive`, {})
