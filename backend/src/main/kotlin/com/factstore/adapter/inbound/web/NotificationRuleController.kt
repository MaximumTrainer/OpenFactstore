package com.factstore.adapter.inbound.web

import org.springframework.security.access.prepost.PreAuthorize
import com.factstore.core.port.inbound.INotificationRuleService
import com.factstore.dto.CreateNotificationRuleRequest
import com.factstore.dto.NotificationDeliveryResponse
import com.factstore.dto.NotificationRuleResponse
import com.factstore.dto.UpdateNotificationRuleRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notification-rules")
@Tag(name = "Notification Rules", description = "Notification rule management")
class NotificationRuleController(private val notificationRuleService: INotificationRuleService) {

    @PreAuthorize("@authz.has('admin')")
    @PostMapping
    @Operation(summary = "Create a notification rule")
    fun createRule(@RequestBody request: CreateNotificationRuleRequest): ResponseEntity<NotificationRuleResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(notificationRuleService.createRule(request))

    @PreAuthorize("@authz.has('admin')")
    @GetMapping
    @Operation(summary = "List all notification rules")
    fun listRules(): ResponseEntity<List<NotificationRuleResponse>> =
        ResponseEntity.ok(notificationRuleService.listRules())

    @PreAuthorize("@authz.has('admin')")
    @GetMapping("/{id}")
    @Operation(summary = "Get a notification rule by ID")
    fun getRule(@PathVariable id: UUID): ResponseEntity<NotificationRuleResponse> =
        ResponseEntity.ok(notificationRuleService.getRule(id))

    @PreAuthorize("@authz.has('admin')")
    @PutMapping("/{id}")
    @Operation(summary = "Update a notification rule")
    fun updateRule(
        @PathVariable id: UUID,
        @RequestBody request: UpdateNotificationRuleRequest
    ): ResponseEntity<NotificationRuleResponse> =
        ResponseEntity.ok(notificationRuleService.updateRule(id, request))

    @PreAuthorize("@authz.has('admin')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a notification rule")
    fun deleteRule(@PathVariable id: UUID): ResponseEntity<Void> {
        notificationRuleService.deleteRule(id)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("@authz.has('admin')")
    @PostMapping("/{id}/test")
    @Operation(summary = "Send a test notification for a rule")
    fun testRule(@PathVariable id: UUID): ResponseEntity<Void> {
        notificationRuleService.testRule(id)
        return ResponseEntity.accepted().build()
    }

    @PreAuthorize("@authz.has('admin')")
    @GetMapping("/{id}/deliveries")
    @Operation(summary = "Get delivery history for a notification rule")
    fun getRuleDeliveries(@PathVariable id: UUID): ResponseEntity<List<NotificationDeliveryResponse>> =
        ResponseEntity.ok(notificationRuleService.getRuleDeliveries(id))
}
