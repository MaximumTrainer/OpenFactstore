package com.factstore.core.port.inbound

import com.factstore.dto.ConfigureSlackRequest
import com.factstore.dto.SlackCommandResponse
import com.factstore.dto.SlackConfigResponse
import com.factstore.dto.SlackNotification

interface ISlackService {
    fun configureSlack(orgSlug: String, request: ConfigureSlackRequest): SlackConfigResponse
    fun removeSlack(orgSlug: String)
    fun getConfig(orgSlug: String): SlackConfigResponse
    fun verifySlackRequest(orgSlug: String, timestamp: String?, signature: String?, rawBody: String)
    fun handleSlashCommand(orgSlug: String, text: String, userId: String, userName: String): SlackCommandResponse
    fun handleInteractiveAction(orgSlug: String, payloadJson: String): SlackCommandResponse
    fun sendNotification(orgSlug: String, notification: SlackNotification): Boolean
}
