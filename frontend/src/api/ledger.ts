import client from './client'
import type { PagedLedgerEntries, LedgerEntry, LedgerVerification, ChainVerification, LedgerStatus } from '../types'

export const getLedgerEntries = (page = 0, size = 20) =>
  client.get<PagedLedgerEntries>('/ledger/entries', { params: { page, size } })

export const getLedgerEntry = (factId: string) =>
  client.get<LedgerEntry>(`/ledger/entries/${factId}`)

export const verifyFact = (factId: string) =>
  client.post<LedgerVerification>(`/ledger/verify/${factId}`)

export const verifyChain = (from: string, to: string) =>
  client.post<ChainVerification>('/ledger/verify-chain', { from, to })

export const getLedgerStatus = () =>
  client.get<LedgerStatus>('/ledger/status')
