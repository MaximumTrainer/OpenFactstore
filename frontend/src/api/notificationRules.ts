import client from './client'
import type {
  NotificationRule,
  CreateNotificationRuleRequest,
  UpdateNotificationRuleRequest,
  NotificationDelivery
} from '../types'

export const notificationRulesApi = {
  list() {
    return client.get<NotificationRule[]>('/notification-rules')
  },

  get(id: string) {
    return client.get<NotificationRule>(`/notification-rules/${id}`)
  },

  create(request: CreateNotificationRuleRequest) {
    return client.post<NotificationRule>('/notification-rules', request)
  },

  update(id: string, request: UpdateNotificationRuleRequest) {
    return client.put<NotificationRule>(`/notification-rules/${id}`, request)
  },

  delete(id: string) {
    return client.delete(`/notification-rules/${id}`)
  },

  test(id: string) {
    return client.post(`/notification-rules/${id}/test`)
  },

  getDeliveries(id: string) {
    return client.get<NotificationDelivery[]>(`/notification-rules/${id}/deliveries`)
  }
}
