package com.factstore.adapter.inbound.web

import org.springframework.security.access.prepost.PreAuthorize
import com.factstore.core.port.inbound.IWebhookConfigService
import com.factstore.dto.CreateWebhookConfigRequest
import com.factstore.dto.WebhookConfigResponse
import com.factstore.dto.WebhookDeliveryResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/webhook-configs")
@Tag(name = "Webhook Configs", description = "Webhook configuration management")
class WebhookConfigController(private val webhookConfigService: IWebhookConfigService) {

    @PreAuthorize("@authz.has('admin')")
    @PostMapping
    @Operation(summary = "Register a new webhook configuration")
    fun createConfig(@RequestBody request: CreateWebhookConfigRequest): ResponseEntity<WebhookConfigResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(webhookConfigService.createConfig(request))

    @PreAuthorize("@authz.has('admin')")
    @GetMapping
    @Operation(summary = "List webhook configurations")
    fun listConfigs(): ResponseEntity<List<WebhookConfigResponse>> =
        ResponseEntity.ok(webhookConfigService.listConfigs())

    @PreAuthorize("@authz.has('admin')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a webhook configuration")
    fun deleteConfig(@PathVariable id: UUID): ResponseEntity<Void> {
        webhookConfigService.deleteConfig(id)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("@authz.has('admin')")
    @GetMapping("/{id}/deliveries")
    @Operation(summary = "List recent webhook deliveries")
    fun listDeliveries(@PathVariable id: UUID): ResponseEntity<List<WebhookDeliveryResponse>> =
        ResponseEntity.ok(webhookConfigService.listDeliveries(id))
}
