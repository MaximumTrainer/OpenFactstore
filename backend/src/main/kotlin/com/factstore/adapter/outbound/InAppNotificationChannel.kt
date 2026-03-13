package com.factstore.adapter.outbound

import com.factstore.core.domain.ChannelType
import com.factstore.core.domain.Notification
import com.factstore.core.domain.NotificationRule
import com.factstore.core.port.outbound.INotificationChannel
import com.factstore.core.port.outbound.INotificationRepository
import com.factstore.dto.NotificationEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class InAppNotificationChannel(
    private val notificationRepository: INotificationRepository
) : INotificationChannel {

    override val channelType = ChannelType.IN_APP

    private val log = LoggerFactory.getLogger(InAppNotificationChannel::class.java)

    override fun send(rule: NotificationRule, event: NotificationEvent) {
        val notification = Notification(
            title = event.title,
            message = event.message,
            severity = event.severity,
            entityType = event.entityType,
            entityId = event.entityId
        )
        notificationRepository.save(notification)
        log.info("Persisted in-app notification for rule=${rule.id} event=${event.triggerEvent}")
    }
}
