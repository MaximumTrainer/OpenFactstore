package com.factstore.adapter.inbound.web

import com.factstore.application.fact.FactIngestionResponse
import com.factstore.application.fact.FactIngestionService
import com.factstore.application.fact.FactView
import com.factstore.exception.ForbiddenException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Tag(name = "Facts", description = "Fact ingestion with strict validation against the schema registry")
class FactIngestionController(
    private val service: FactIngestionService,
    /**
     * #189: retire static keys for ingest. When on, only a verified workload identity (an OIDC
     * token or SPIFFE JWT-SVID, see `WorkloadIdentityAuthFilter`) may ingest; an API key or a
     * signed-in person is refused even with the scope.
     */
    @Value("\${factstore.ingest.require-workload-identity:false}") private val requireWorkloadIdentity: Boolean
) {

    @PostMapping("/api/v1/commands/facts")
    @PreAuthorize("@authz.has('attestations:write')")
    @Operation(
        summary = "Ingest a fact",
        description = "202 when the fact conforms and is accepted. 400 with error invalid_envelope, unknown_schema " +
            "or schema_nonconformant and a list of violations when it does not; nothing is stored. 409 when the " +
            "fact_id was already ingested. 401 when X-Signature is missing or does not verify against the trusted " +
            "signing-key registry (#187)."
    )
    fun ingest(
        // The raw text, not a parsed tree: the source signature is over these exact bytes (#187).
        @RequestBody body: String,
        @RequestHeader("X-Signature", required = false) signature: String?,
        @RequestHeader("X-Signature-Key-Id", required = false) signatureKeyId: String?
    ): ResponseEntity<FactIngestionResponse> {
        if (requireWorkloadIdentity && !callerIsWorkload()) {
            throw ForbiddenException(
                "Fact ingestion requires a workload identity (an OIDC token or SPIFFE JWT-SVID); static credentials are not accepted"
            )
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.ingest(body, signature, signatureKeyId))
    }

    @GetMapping("/api/v1/queries/facts/{factId}")
    @PreAuthorize("@authz.has('trails:read')")
    @Operation(
        summary = "Read a fact",
        description = "The fact as accepted, with the identity verified when it arrived (#191): for a CI workload, " +
            "the issuer, subject, repository, run id and attempt, actor, workflow, ref and commit."
    )
    fun get(@PathVariable factId: UUID): ResponseEntity<FactView> = ResponseEntity.ok(service.get(factId))

    private fun callerIsWorkload(): Boolean =
        SecurityContextHolder.getContext().authentication?.authorities?.any { it.authority == "ROLE_WORKLOAD" } == true
}
