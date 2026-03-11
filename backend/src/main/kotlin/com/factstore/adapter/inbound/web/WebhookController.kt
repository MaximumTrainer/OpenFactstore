package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IWebhookService
import com.factstore.dto.WebhookResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "Inbound webhook endpoints for CI/CD pipeline events")
class WebhookController(private val webhookService: IWebhookService) {

    @PostMapping("/{source}")
    @Operation(summary = "Receive incoming webhook from CI/CD system")
    fun receiveWebhook(
        @PathVariable source: String,
        @RequestBody payload: String,
        @RequestHeader("X-Hub-Signature-256", required = false) githubSignature: String?,
        @RequestHeader("X-Webhook-Signature", required = false) genericSignature: String?,
        @RequestHeader("X-Delivery-Id", required = false) deliveryId: String?,
        @RequestHeader("X-GitHub-Delivery", required = false) githubDeliveryId: String?
    ): ResponseEntity<WebhookResponse> {
        val signature = githubSignature ?: genericSignature
        val resolvedDeliveryId = deliveryId ?: githubDeliveryId
        val response = webhookService.processWebhook(source, payload, signature, resolvedDeliveryId)
        return ResponseEntity.ok(response)
    }
}
