package com.factstore.adapter.inbound.web.query

import org.springframework.security.access.prepost.PreAuthorize
import com.factstore.core.port.inbound.query.ITrailQueryHandler
import com.factstore.core.port.inbound.IAuditService
import com.factstore.dto.AuditEventResponse
import com.factstore.dto.query.TrailView
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/api/v2/trails")
    @Operation(summary = "List trails, optionally filter by flowId; archived trails are hidden by default")
    fun listTrails(
        @RequestParam(required = false) flowId: UUID?,
        @Parameter(description = "Include archived records (default false)") @RequestParam(defaultValue = "false") includeArchived: Boolean,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?
    ): ResponseEntity<List<TrailView>> = Paging.page(queryHandler.listTrails(flowId, includeArchived), page, size)

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/api/v2/trails/{id}")
    @Operation(summary = "Get trail by ID")
    fun getTrail(@PathVariable id: UUID): ResponseEntity<TrailView> =
        ResponseEntity.ok(queryHandler.getTrail(id))

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/api/v2/flows/{flowId}/trails")
    @Operation(summary = "List trails for a flow; archived trails are hidden by default")
    fun listTrailsForFlow(
        @PathVariable flowId: UUID,
        @Parameter(description = "Include archived records (default false)") @RequestParam(defaultValue = "false") includeArchived: Boolean,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?
    ): ResponseEntity<List<TrailView>> = Paging.page(queryHandler.listTrailsForFlow(flowId, includeArchived), page, size)

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/api/v2/trails/{id}/audit")
    @Operation(summary = "Get audit events for a specific trail")
    fun getTrailAuditEvents(@PathVariable id: UUID): ResponseEntity<List<AuditEventResponse>> =
        ResponseEntity.ok(auditService.getEventsForTrail(id))
}
