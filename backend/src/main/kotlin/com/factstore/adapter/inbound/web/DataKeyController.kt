package com.factstore.adapter.inbound.web

import com.factstore.application.shredding.DataKeyView
import com.factstore.application.shredding.ShredCertificate
import com.factstore.application.shredding.ShredRequestView
import com.factstore.application.shredding.ShreddingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Cryptographic shredding of tenant-month data keys (#200). Administrators only, two of them. */
@RestController
@Tag(name = "Data keys", description = "Per-tenant, per-month encryption keys and their shredding")
class DataKeyController(private val service: ShreddingService) {

    @PostMapping("/api/v1/commands/data-keys/{tenant}/{period}/shred-requests")
    @PreAuthorize("@authz.has('admin')")
    @Operation(
        summary = "Request that a data key be shredded",
        description = "period is YYYY-MM. 202 with a request that a different administrator must approve; 404 when " +
            "no key exists; 409 when it is already shredded."
    )
    fun request(@PathVariable tenant: String, @PathVariable period: String): ResponseEntity<ShredRequestView> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(service.request(tenant, period))

    @PostMapping("/api/v1/commands/data-keys/shred-requests/{requestId}/approve")
    @PreAuthorize("@authz.has('admin')")
    @Operation(
        summary = "Approve a shred request and destroy the key",
        description = "Must be a different administrator from the requester (403 otherwise). Irreversible: every fact " +
            "encrypted under the key becomes unreadable (410). Returns the destruction certificate, a ledger record."
    )
    fun approve(@PathVariable requestId: UUID): ResponseEntity<ShredCertificate> = ResponseEntity.ok(service.approve(requestId))

    @GetMapping("/api/v1/queries/data-keys")
    @PreAuthorize("@authz.has('admin')")
    @Operation(summary = "List data keys and whether they are shredded")
    fun list(): ResponseEntity<List<DataKeyView>> = ResponseEntity.ok(service.list())
}
