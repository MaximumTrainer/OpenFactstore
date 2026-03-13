import client from './client'
import type { DashboardStats } from '../types'

export const getDashboardStats = () => client.get<DashboardStats>('/dashboard/stats')
