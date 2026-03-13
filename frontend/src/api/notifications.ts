import client from './client'
import type { Notification, NotificationSeverity } from '../types'

export const notificationsApi = {
  list(params?: { isRead?: boolean; severity?: NotificationSeverity }) {
    return client.get<Notification[]>('/notifications', { params })
  },

  countUnread() {
    return client.get<{ count: number }>('/notifications/unread-count')
  },

  markAsRead(id: string) {
    return client.post<Notification>(`/notifications/${id}/read`)
  },

  markAllAsRead() {
    return client.post('/notifications/read-all')
  }
}
