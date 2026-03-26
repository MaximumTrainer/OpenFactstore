package com.factstore.adapter.inbound.web.query

import com.factstore.core.port.inbound.query.IAttestationQueryHandler
import com.factstore.dto.query.AttestationView
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v2/trails/{trailId}/attestations")
@Tag(name = "Attestations – Queries", description = "Attestation read operations (CQRS query path)")
class AttestationQueryController(private val queryHandler: IAttestationQueryHandler) {

    @GetMapping
    @Operation(summary = "List attestations for a trail")
    fun listAttestations(@PathVariable trailId: UUID): ResponseEntity<List<AttestationView>> =
        ResponseEntity.ok(queryHandler.listAttestations(trailId))
}
