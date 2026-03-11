package com.factstore.core.port.inbound

import com.factstore.dto.WebhookResponse

interface IWebhookService {
    fun processWebhook(
        source: String,
        payload: String,
        signature: String?,
        deliveryId: String?
    ): WebhookResponse
}
