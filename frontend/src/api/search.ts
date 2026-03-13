import client from './client'
import type { SearchResponse } from '../types'

export const search = (q: string, type?: string) =>
  client.get<SearchResponse>('/search', { params: { q, ...(type ? { type } : {}) } })
