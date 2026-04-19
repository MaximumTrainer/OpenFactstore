package com.factstore.adapter.inbound.web

import com.factstore.application.CiContextResolver
import com.factstore.application.DryRunContext
import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.inbound.ITrailService
import com.factstore.dto.AuditEventResponse
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.DryRunResponse
import com.factstore.dto.PageResponse
import com.factstore.dto.TrailResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@Tag(name = "Trails", description = "Trail management")
class TrailController(
    private val trailService: ITrailService,
    private val auditService: IAuditService
) {

    @PostMapping("/api/v1/trails")
    @Operation(summary = "Create/begin a trail")
    fun createTrail(
        @Valid @RequestBody request: CreateTrailRequest,
        @RequestHeader(value = "X-Factstore-CI-Context", required = false) ciContext: String?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<*> {
        val enrichedRequest = CiContextResolver.resolve(ciContext)
            ?.let { CiContextResolver.enrich(request, it) }
            ?: request
        if (DryRunContext.isDryRun(httpRequest)) {
            val now = Instant.now()
            val wouldBe = TrailResponse(
                id = UUID.randomUUID(),
                flowId = enrichedRequest.flowId,
                gitCommitSha = enrichedRequest.gitCommitSha ?: "",
                gitBranch = enrichedRequest.gitBranch ?: "",
                gitAuthor = enrichedRequest.gitAuthor,
                gitAuthorEmail = enrichedRequest.gitAuthorEmail,
                pullRequestId = enrichedRequest.pullRequestId,
                pullRequestReviewer = enrichedRequest.pullRequestReviewer,
                deploymentActor = enrichedRequest.deploymentActor,
                status = TrailStatus.PENDING,
                orgSlug = enrichedRequest.orgSlug,
                templateYaml = enrichedRequest.templateYaml,
                buildUrl = enrichedRequest.buildUrl,
                createdAt = now,
                updatedAt = now
            )
            return ResponseEntity.ok(DryRunResponse(wouldCreate = wouldBe))
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(trailService.createTrail(enrichedRequest))
    }

    @GetMapping("/api/v1/trails")
    @Operation(summary = "List trails, optionally filter by flowId")
    fun listTrails(@RequestParam(required = false) flowId: UUID?): ResponseEntity<List<TrailResponse>> =
        ResponseEntity.ok(trailService.listTrails(flowId))

    @GetMapping("/api/v1/trails/{id}")
    @Operation(summary = "Get trail by ID")
    fun getTrail(@PathVariable id: UUID): ResponseEntity<TrailResponse> =
        ResponseEntity.ok(trailService.getTrail(id))

    @GetMapping("/api/v1/flows/{flowId}/trails")
    @Operation(summary = "List trails for a flow")
    fun listTrailsForFlow(
        @PathVariable flowId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PageResponse<TrailResponse>> =
        ResponseEntity.ok(trailService.listTrailsForFlow(flowId, page, size))

    @GetMapping("/api/v1/trails/{id}/audit")
    @Operation(summary = "Get audit events for a specific trail")
    fun getTrailAuditEvents(@PathVariable id: UUID): ResponseEntity<List<AuditEventResponse>> =
        ResponseEntity.ok(auditService.getEventsForTrail(id))
}
