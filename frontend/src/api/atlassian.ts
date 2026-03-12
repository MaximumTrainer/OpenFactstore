import client from './client'
import type {
  JiraConfig,
  ConfluenceConfig,
  JiraTicket,
  ConnectionTestResponse,
  JiraSyncResponse
} from '../types'

// Jira config
export const getJiraConfig = () => client.get<JiraConfig>('/integrations/jira/config')
export const saveJiraConfig = (data: {
  jiraBaseUrl: string
  jiraUsername: string
  jiraApiToken: string
  defaultProjectKey: string
}) => client.post<JiraConfig>('/integrations/jira/config', data)

// Jira connectivity
export const testJiraConnectivity = () =>
  client.post<ConnectionTestResponse>('/integrations/jira/test')

// Jira sync
export const syncToJira = () =>
  client.post<JiraSyncResponse>('/integrations/jira/sync')

// Jira tickets
export const listJiraTickets = () => client.get<JiraTicket[]>('/integrations/jira/tickets')
export const createJiraTicket = (data: { trailId: string; summary: string; issueType: string }) =>
  client.post<JiraTicket>('/integrations/jira/tickets', data)

// Confluence config
export const getConfluenceConfig = () => client.get<ConfluenceConfig>('/integrations/confluence/config')
export const saveConfluenceConfig = (data: {
  confluenceBaseUrl: string
  confluenceUsername: string
  confluenceApiToken: string
  defaultSpaceKey: string
}) => client.post<ConfluenceConfig>('/integrations/confluence/config', data)

// Confluence connectivity
export const testConfluenceConnectivity = () =>
  client.post<ConnectionTestResponse>('/integrations/confluence/test')
