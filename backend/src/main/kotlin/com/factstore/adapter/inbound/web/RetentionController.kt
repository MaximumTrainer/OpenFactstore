package com.factstore.adapter.inbound.web

import com.factstore.core.domain.RetentionDataClass
import com.factstore.core.port.inbound.IRetentionService
import com.factstore.dto.LegalHoldResponse
import com.factstore.dto.PlaceLegalHoldRequest
import com.factstore.dto.RetentionDataClassResponse
import com.factstore.dto.RetentionPolicyResponse
import com.factstore.dto.RetentionRunReport
import com.factstore.dto.SetRetentionPolicyRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Tag(name = "Retention", description = "Data retention policies, legal holds and the retention job (#251)")
class RetentionController(private val retention: IRetentionService) {

    @PreAuthorize("@authz.has('admin')")
    @GetMapping("/api/v1/admin/retention/data-classes")
    @Operation(summary = "The data classes a retention policy can govern, with their action and minimum period")
    fun dataClasses(): ResponseEntity<List<RetentionDataClassResponse>> = ResponseEntity.ok(retention.dataClasses())

    @PreAuthorize("@authz.has('admin')")
    @GetMapping("/api/v1/admin/retention/policies")
    @Operation(summary = "List retention policies")
    fun listPolicies(): ResponseEntity<List<RetentionPolicyResponse>> = ResponseEntity.ok(retention.listPolicies())

    @PreAuthorize("@authz.has('admin')")
    @PutMapping("/api/v1/admin/retention/policies/{tenant}/{dataClass}")
    @Operation(
        summary = "Set how long an organisation keeps a class of data",
        description = "tenant is an organisation slug, or 'default' for data that belongs to no organisation. " +
            "retainDays must be at least the data class's minimum; shorter is a 400."
    )
    fun setPolicy(
        @PathVariable tenant: String,
        @PathVariable dataClass: RetentionDataClass,
        @RequestBody request: SetRetentionPolicyRequest
    ): ResponseEntity<RetentionPolicyResponse> = ResponseEntity.ok(retention.setPolicy(tenant, dataClass, request.retainDays))

    @PreAuthorize("@authz.has('admin')")
    @DeleteMapping("/api/v1/admin/retention/policies/{tenant}/{dataClass}")
    @Operation(summary = "Remove a retention policy; the data class is then kept indefinitely")
    fun removePolicy(@PathVariable tenant: String, @PathVariable dataClass: RetentionDataClass): ResponseEntity<Void> {
        retention.removePolicy(tenant, dataClass)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("@authz.has('admin')")
    @PostMapping("/api/v1/admin/retention/runs")
    @Operation(
        summary = "Run the retention job now",
        description = "Defaults to a dry run, which reports what would be shredded, archived or pruned and changes nothing."
    )
    fun run(@RequestParam(defaultValue = "true") dryRun: Boolean): ResponseEntity<RetentionRunReport> =
        ResponseEntity.ok(retention.run(dryRun))

    @PreAuthorize("@authz.has('admin')")
    @GetMapping("/api/v1/legal-holds")
    @Operation(summary = "List legal holds; released holds only with includeReleased=true")
    fun listHolds(@RequestParam(defaultValue = "false") includeReleased: Boolean): ResponseEntity<List<LegalHoldResponse>> =
        ResponseEntity.ok(retention.listHolds(includeReleased))

    @PreAuthorize("@authz.has('admin')")
    @PostMapping("/api/v1/legal-holds")
    @Operation(summary = "Place a legal hold on a trail or flow; retention skips everything it covers")
    fun placeHold(@RequestBody request: PlaceLegalHoldRequest): ResponseEntity<LegalHoldResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(retention.placeHold(request))

    @PreAuthorize("@authz.has('admin')")
    @PostMapping("/api/v1/legal-holds/{id}/release")
    @Operation(summary = "Release a legal hold; the hold stays on record")
    fun releaseHold(@PathVariable id: UUID): ResponseEntity<LegalHoldResponse> = ResponseEntity.ok(retention.releaseHold(id))
}
