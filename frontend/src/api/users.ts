import client from './client'
import type { ApiKey, ApiKeyCreated, CreateApiKeyRequest, User, CreateUserRequest, UpdateUserRequest } from '../types'

// User endpoints
export const getUsers = () =>
  client.get<User[]>('/users')

export const getUser = (id: string) =>
  client.get<User>(`/users/${id}`)

export const createUser = (data: CreateUserRequest) =>
  client.post<User>('/users', data)

export const updateUser = (id: string, data: UpdateUserRequest) =>
  client.put<User>(`/users/${id}`, data)

export const deleteUser = (id: string) =>
  client.delete(`/users/${id}`)

// API Key endpoints
export const getApiKeysForUser = (userId: string) =>
  client.get<ApiKey[]>(`/api-keys/users/${userId}`)

/**
 * Creates a new API key and returns it with the plain-text key included.
 * The plain-text key is shown **exactly once** — store it securely.
 */
export const createApiKey = (data: CreateApiKeyRequest) =>
  client.post<ApiKeyCreated>('/api-keys', data)

export const revokeApiKey = (id: string) =>
  client.delete(`/api-keys/${id}/revoke`)
