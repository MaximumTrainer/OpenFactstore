import client from './client'

export interface HubTemplate {
  id: string
  name: string
  description: string
  framework: string
  version: string
}

export const listTemplates = () => client.get<HubTemplate[]>('/hub/templates')
export const importTemplate = (id: string) => client.post<void>(`/hub/templates/${id}/import`)
