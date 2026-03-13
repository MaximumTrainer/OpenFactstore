package com.factstore.adapter.inbound.web

import com.factstore.core.domain.AuditEventType
import com.factstore.core.port.inbound.IAuditService
import com.factstore.dto.AuditEventPage
import com.factstore.dto.AuditEventResponse
import com.factstore.exception.BadRequestException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit Log", description = "Immutable audit event log")
class AuditController(private val auditService: IAuditService) {

    @GetMapping
    @Operation(summary = "Query audit events with optional filters and pagination")
    fun queryEvents(
        @RequestParam(required = false) eventType: AuditEventType?,
        @RequestParam(required = false) trailId: UUID?,
        @RequestParam(required = false) actor: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "true") sortDesc: Boolean
    ): ResponseEntity<AuditEventPage> {
        if (page < 0) throw BadRequestException("page must be >= 0")
        if (size < 1 || size > 100) throw BadRequestException("size must be between 1 and 100")
        return ResponseEntity.ok(auditService.queryEvents(eventType, trailId, actor, from, to, page, size, sortDesc))
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific audit event by ID")
    fun getEvent(@PathVariable id: UUID): ResponseEntity<AuditEventResponse> =
        ResponseEntity.ok(auditService.getEvent(id))
}
