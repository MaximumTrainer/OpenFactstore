package com.factstore.adapter.inbound.web

import com.factstore.application.ledger.CheckpointView
import com.factstore.application.ledger.LedgerCheckpointService
import com.factstore.application.ledger.LedgerProof
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

/** Merkle checkpoints and inclusion proofs over the signed event log (#188). */
@RestController
@Tag(name = "Ledger proofs", description = "Tamper-evident proofs an auditor can verify without trusting Factstore")
class LedgerProofController(private val service: LedgerCheckpointService) {

    @GetMapping("/api/v1/queries/ledger/proof/{factId}")
    @PreAuthorize("@authz.has('trails:read')")
    @Operation(
        summary = "Inclusion proof for a fact",
        description = "The fact's record bytes and signature, its RFC 9162 inclusion path and the signed checkpoint " +
            "root. Verify it independently with tools/ledger-verifier. 404 with error proof_pending until a " +
            "checkpoint covers the fact."
    )
    fun proof(@PathVariable factId: UUID): ResponseEntity<LedgerProof> = ResponseEntity.ok(service.proof(factId))

    @GetMapping("/api/v1/queries/ledger/checkpoints")
    @PreAuthorize("@authz.authenticated()")
    @Operation(summary = "Signed ledger checkpoints, oldest first")
    fun checkpoints(): ResponseEntity<List<CheckpointView>> = ResponseEntity.ok(service.list())

    @PostMapping("/api/v1/admin/ledger/checkpoints")
    @PreAuthorize("@authz.has('admin')")
    @Operation(
        summary = "Checkpoint now",
        description = "Covers every signed record since the last checkpoint. 201 with the checkpoint, or 204 when " +
            "there is nothing new. Checkpoints are also made on a schedule."
    )
    fun checkpoint(): ResponseEntity<CheckpointView> =
        service.checkpoint()?.let { ResponseEntity.status(HttpStatus.CREATED).body(it) }
            ?: ResponseEntity.noContent().build()
}
