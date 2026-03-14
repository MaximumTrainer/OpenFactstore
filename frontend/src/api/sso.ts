import client from './client'
import type {
  CreateSsoConfigRequest,
  SsoConfig,
  SsoLoginUrlResponse,
  SsoTestConnectionResponse,
  UpdateSsoConfigRequest,
} from '../types'

export const getSsoConfig = (orgSlug: string) =>
  client.get<SsoConfig>(`/organisations/${orgSlug}/sso`)

export const createSsoConfig = (orgSlug: string, data: CreateSsoConfigRequest) =>
  client.post<SsoConfig>(`/organisations/${orgSlug}/sso`, data)

export const updateSsoConfig = (orgSlug: string, data: UpdateSsoConfigRequest) =>
  client.put<SsoConfig>(`/organisations/${orgSlug}/sso`, data)

export const deleteSsoConfig = (orgSlug: string) =>
  client.delete(`/organisations/${orgSlug}/sso`)

export const testSsoConnection = (orgSlug: string) =>
  client.post<SsoTestConnectionResponse>(`/organisations/${orgSlug}/sso/test`)

export const initiateSsoLogin = (orgSlug: string, redirectUri: string) =>
  client.get<SsoLoginUrlResponse>(`/organisations/${orgSlug}/sso/login`, {
    params: { redirectUri },
  })
