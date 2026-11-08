package com.factstore.adapter.inbound.web

import org.springframework.security.access.prepost.PreAuthorize
import com.factstore.application.CiContextResolver
import com.factstore.application.DryRunContext
import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.inbound.ITrailCleanupService
import com.factstore.core.port.inbound.ITrailService
import com.factstore.dto.AuditEventResponse
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.DryRunResponse
import com.factstore.dto.PageResponse
import com.factstore.dto.TrailCascadeCounts
import com.factstore.dto.TrailCleanupRequest
import com.factstore.dto.TrailCleanupResponse
import com.factstore.dto.TrailDeletionResponse
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
    private val trailCleanupService: ITrailCleanupService,
    private val auditService: IAuditService
) {

    @PreAuthorize("@authz.has('trails:write')")
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
        // Get-or-create on (flowId, externalId): 201 for a new trail, 200 when an existing one
        // was reused, so a pipeline can tell whether it started the run or joined it (#164).
        val result = trailService.createOrGetTrail(enrichedRequest)
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(result.trail)
    }

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/api/v1/trails/lookup")
    @Operation(
        summary = "Resolve a trail without knowing its UUID",
        description = "Addresses a trail within a flow by externalId, name, or gitCommitSha " +
            "(most recent run for that commit). Lets a secondary pipeline attest against the " +
            "trail the primary pipeline created."
    )
    fun lookupTrail(
        @RequestParam flowId: UUID,
        @RequestParam(required = false) externalId: String?,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) gitCommitSha: String?
    ): ResponseEntity<TrailResponse> =
        ResponseEntity.ok(trailService.lookupTrail(flowId, externalId, name, gitCommitSha))

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/api/v1/trails")
    @Operation(summary = "List trails, optionally filter by flowId; archived trails are hidden by default")
    fun listTrails(
        @RequestParam(required = false) flowId: UUID?,
        @RequestParam(defaultValue = "false") includeArchived: Boolean
    ): ResponseEntity<List<TrailResponse>> =
        ResponseEntity.ok(trailService.listTrails(flowId, includeArchived))

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/api/v1/trails/{id}")
    @Operation(summary = "Get trail by ID")
    fun getTrail(@PathVariable id: UUID): ResponseEntity<TrailResponse> =
        ResponseEntity.ok(trailService.getTrail(id))

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/api/v1/flows/{flowId}/trails")
    @Operation(summary = "List trails for a flow; archived trails are hidden by default")
    fun listTrailsForFlow(
        @PathVariable flowId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "false") includeArchived: Boolean
    ): ResponseEntity<PageResponse<TrailResponse>> =
        ResponseEntity.ok(trailService.listTrailsForFlow(flowId, page, size, includeArchived))

    @PreAuthorize("@authz.has('trails:write')")
    @PostMapping("/api/v1/trails/{id}/archive")
    @Operation(
        summary = "Archive a trail (soft delete)",
        description = "Hides the trail from the default listings while retaining every piece of " +
            "evidence recorded against it. Reversible."
    )
    fun archiveTrail(@PathVariable id: UUID): ResponseEntity<TrailResponse> =
        ResponseEntity.ok(trailCleanupService.archiveTrail(id))

    @PreAuthorize("@authz.has('trails:write')")
    @PostMapping("/api/v1/trails/{id}/unarchive")
    @Operation(summary = "Unarchive a trail")
    fun unarchiveTrail(@PathVariable id: UUID): ResponseEntity<TrailResponse> =
        ResponseEntity.ok(trailCleanupService.unarchiveTrail(id))

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/api/v1/trails/{id}/cascade")
    @Operation(summary = "What deleting this trail would remove")
    fun getTrailCascade(@PathVariable id: UUID): ResponseEntity<TrailCascadeCounts> =
        ResponseEntity.ok(trailCleanupService.cascadeFor(id))

    @PreAuthorize("@authz.has('admin')")
    @DeleteMapping("/api/v1/trails/{id}")
    @Operation(
        summary = "Permanently delete a trail and the evidence it owns",
        description = "Cascades to attestations, artifacts, evidence files, approvals, coverage " +
            "reports, security scans, compliance assessments and Jira tickets. The audit log and " +
            "the append-only ledger are deliberately left intact. Prefer archiving."
    )
    fun deleteTrail(@PathVariable id: UUID): ResponseEntity<TrailDeletionResponse> =
        ResponseEntity.ok(trailCleanupService.deleteTrail(id))

    @PreAuthorize("@authz.has('admin')")
    @PostMapping("/api/v1/trails/cleanup")
    @Operation(
        summary = "Bulk cleanup of trails by flow, tag or age",
        description = "Defaults to a dry run and to ARCHIVE. At least one selector is required, " +
            "so a mistyped request cannot select every trail in the system."
    )
    fun cleanupTrails(@RequestBody request: TrailCleanupRequest): ResponseEntity<TrailCleanupResponse> =
        ResponseEntity.ok(trailCleanupService.cleanup(request))

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/api/v1/trails/{id}/audit")
    @Operation(summary = "Get audit events for a specific trail")
    fun getTrailAuditEvents(@PathVariable id: UUID): ResponseEntity<List<AuditEventResponse>> =
        ResponseEntity.ok(auditService.getEventsForTrail(id))
}
