package com.factstore.core.port.outbound

import com.factstore.core.domain.ChannelType
import com.factstore.core.domain.NotificationRule
import com.factstore.dto.NotificationEvent

interface INotificationChannel {
    val channelType: ChannelType
    fun send(rule: NotificationRule, event: NotificationEvent)
}
