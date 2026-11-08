package com.factstore.adapter.inbound.web

import org.springframework.security.access.prepost.PreAuthorize
import com.factstore.application.DryRunContext
import com.factstore.core.port.inbound.IAssertService
import com.factstore.dto.AssertRequest
import com.factstore.dto.AssertResponse
import com.factstore.dto.DryRunResponse
import com.factstore.dto.TrailAssertRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@Tag(name = "Assert", description = "Compliance assertion")
class AssertController(private val assertService: IAssertService) {

    @PreAuthorize("@authz.has('assert:execute')")
    @PostMapping("/api/v1/assert")
    @Operation(
        summary = "Assert compliance for an artifact against a flow",
        description = "Supply trailId to scope the verdict to one pipeline execution. " +
            "Without it, the most recent trail carrying the digest decides."
    )
    fun assertCompliance(
        @RequestBody request: AssertRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<*> {
        val response = assertService.assertCompliance(request)
        if (DryRunContext.isDryRun(httpRequest)) {
            return ResponseEntity.ok(DryRunResponse(wouldCreate = response))
        }
        return ResponseEntity.ok(response)
    }

    @PreAuthorize("@authz.has('assert:execute')")
    @PostMapping("/api/v1/trails/{trailId}/assert")
    @Operation(
        summary = "Assert compliance for a specific pipeline execution",
        description = "The natural call for a pipeline judging its own run. No digest is required: " +
            "attestations recorded before the image exists still count."
    )
    fun assertTrail(
        @PathVariable trailId: UUID,
        @RequestBody(required = false) request: TrailAssertRequest?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<*> {
        val response = assertService.assertTrail(
            trailId = trailId,
            flowId = request?.flowId,
            sha256Digest = request?.sha256Digest
        )
        if (DryRunContext.isDryRun(httpRequest)) {
            return ResponseEntity.ok(DryRunResponse(wouldCreate = response))
        }
        return ResponseEntity.ok(response)
    }
}
