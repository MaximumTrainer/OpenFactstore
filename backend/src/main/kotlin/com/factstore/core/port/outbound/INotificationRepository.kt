package com.factstore.core.port.outbound

import com.factstore.core.domain.Notification
import com.factstore.core.domain.NotificationSeverity
import java.util.UUID

interface INotificationRepository {
    fun save(notification: Notification): Notification
    fun saveAll(notifications: List<Notification>): List<Notification>
    fun findById(id: UUID): Notification?
    fun findAll(isRead: Boolean?, severity: NotificationSeverity?): List<Notification>
    fun countUnread(): Long
    fun markAllAsRead()
}
