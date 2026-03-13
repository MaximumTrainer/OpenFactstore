package com.factstore.core.port.inbound

import com.factstore.core.domain.NotificationSeverity
import com.factstore.dto.NotificationEvent
import com.factstore.dto.NotificationResponse
import java.util.UUID

interface INotificationService {
    fun listNotifications(isRead: Boolean?, severity: NotificationSeverity?): List<NotificationResponse>
    fun markAsRead(id: UUID): NotificationResponse
    fun markAllAsRead()
    fun countUnread(): Long
    fun publishEvent(event: NotificationEvent)
}
