package com.factstore.adapter.inbound.web.query

import com.factstore.core.port.inbound.query.ITrailQueryHandler
import com.factstore.core.port.inbound.IAuditService
import com.factstore.dto.AuditEventResponse
import com.factstore.dto.query.TrailView
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@Tag(name = "Trails – Queries", description = "Trail read operations (CQRS query path)")
class TrailQueryController(
    private val queryHandler: ITrailQueryHandler,
    private val auditService: IAuditService
) {

    @GetMapping("/api/v2/trails")
    @Operation(summary = "List trails, optionally filter by flowId")
    fun listTrails(@RequestParam(required = false) flowId: UUID?): ResponseEntity<List<TrailView>> =
        ResponseEntity.ok(queryHandler.listTrails(flowId))

    @GetMapping("/api/v2/trails/{id}")
    @Operation(summary = "Get trail by ID")
    fun getTrail(@PathVariable id: UUID): ResponseEntity<TrailView> =
        ResponseEntity.ok(queryHandler.getTrail(id))

    @GetMapping("/api/v2/flows/{flowId}/trails")
    @Operation(summary = "List trails for a flow")
    fun listTrailsForFlow(@PathVariable flowId: UUID): ResponseEntity<List<TrailView>> =
        ResponseEntity.ok(queryHandler.listTrailsForFlow(flowId))

    @GetMapping("/api/v2/trails/{id}/audit")
    @Operation(summary = "Get audit events for a specific trail")
    fun getTrailAuditEvents(@PathVariable id: UUID): ResponseEntity<List<AuditEventResponse>> =
        ResponseEntity.ok(auditService.getEventsForTrail(id))
}
