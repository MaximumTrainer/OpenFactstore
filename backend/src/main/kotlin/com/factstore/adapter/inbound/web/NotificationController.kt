package com.factstore.adapter.inbound.web

import com.factstore.core.domain.NotificationSeverity
import com.factstore.core.port.inbound.INotificationService
import com.factstore.dto.NotificationResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "In-app notification centre")
class NotificationController(private val notificationService: INotificationService) {

    @GetMapping
    @Operation(summary = "List in-app notifications")
    fun listNotifications(
        @RequestParam(required = false) isRead: Boolean?,
        @RequestParam(required = false) severity: NotificationSeverity?
    ): ResponseEntity<List<NotificationResponse>> =
        ResponseEntity.ok(notificationService.listNotifications(isRead, severity))

    @GetMapping("/unread-count")
    @Operation(summary = "Get count of unread notifications")
    fun countUnread(): ResponseEntity<Map<String, Long>> =
        ResponseEntity.ok(mapOf("count" to notificationService.countUnread()))

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    fun markAsRead(@PathVariable id: UUID): ResponseEntity<NotificationResponse> =
        ResponseEntity.ok(notificationService.markAsRead(id))

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    fun markAllAsRead(): ResponseEntity<Void> {
        notificationService.markAllAsRead()
        return ResponseEntity.noContent().build()
    }
}
