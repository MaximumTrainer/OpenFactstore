package com.factstore.adapter.inbound.web

import com.factstore.application.signing.EventChainVerifier
import com.factstore.application.signing.VerificationReport
import com.factstore.core.port.outbound.EventSigningPublicKey
import com.factstore.core.port.outbound.IEventRecordSigner
import com.factstore.exception.BadRequestException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class VerifyEventsRequest(val from: Long, val to: Long)

/** Signed event records (#184): the published verification keys, and verification over a range. */
@RestController
@Tag(name = "Event integrity", description = "Signatures on stored event records")
class EventIntegrityController(
    private val verifier: EventChainVerifier,
    private val signer: IEventRecordSigner
) {

    @PostMapping("/api/v1/admin/events/verify")
    @PreAuthorize("@authz.has('admin')")
    @Operation(
        summary = "Verify stored event records over a sequence range",
        description = "Recomputes each signed record's canonical bytes, checks its hash, its link to the record " +
            "before it and its signature, and reports the first failing sequence number and how many were checked."
    )
    fun verify(@RequestBody request: VerifyEventsRequest): ResponseEntity<VerificationReport> {
        if (request.from < 1 || request.to < request.from) throw BadRequestException("Need 1 <= from <= to")
        return ResponseEntity.ok(verifier.verify(request.from, request.to))
    }

    @GetMapping("/api/v1/queries/event-signing-keys")
    @PreAuthorize("@authz.authenticated()")
    @Operation(
        summary = "Published event-signing keys",
        description = "Every key version that has signed records, oldest first, with its validity window."
    )
    fun keys(): ResponseEntity<List<EventSigningPublicKey>> = ResponseEntity.ok(signer.publicKeys())
}
