package com.factstore.application

import com.factstore.core.domain.Notification
import com.factstore.core.domain.NotificationDelivery
import com.factstore.core.domain.NotificationSeverity
import com.factstore.core.port.inbound.INotificationService
import com.factstore.core.port.outbound.INotificationRepository
import com.factstore.dto.NotificationDeliveryResponse
import com.factstore.dto.NotificationEvent
import com.factstore.dto.NotificationResponse
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class NotificationService(
    private val notificationRepository: INotificationRepository,
    private val notificationDispatchService: NotificationDispatchService
) : INotificationService {

    private val log = LoggerFactory.getLogger(NotificationService::class.java)

    @Transactional(readOnly = true)
    override fun listNotifications(isRead: Boolean?, severity: NotificationSeverity?): List<NotificationResponse> =
        notificationRepository.findAll(isRead, severity).map { it.toResponse() }

    override fun markAsRead(id: UUID): NotificationResponse {
        val notification = notificationRepository.findById(id)
            ?: throw NotFoundException("Notification not found: $id")
        notification.isRead = true
        return notificationRepository.save(notification).toResponse()
    }

    override fun markAllAsRead() {
        notificationRepository.markAllAsRead()
        log.info("Marked all notifications as read")
    }

    @Transactional(readOnly = true)
    override fun countUnread(): Long = notificationRepository.countUnread()

    override fun publishEvent(event: NotificationEvent) {
        notificationDispatchService.dispatch(event)
    }
}

fun Notification.toResponse() = NotificationResponse(
    id = id,
    title = title,
    message = message,
    severity = severity,
    isRead = isRead,
    entityType = entityType,
    entityId = entityId,
    createdAt = createdAt
)

fun NotificationDelivery.toResponse() = NotificationDeliveryResponse(
    id = id,
    ruleId = ruleId,
    eventType = eventType,
    payload = payload,
    status = status,
    sentAt = sentAt,
    error = error,
    attemptCount = attemptCount
)
