package com.factstore.adapter.inbound.web

import com.factstore.application.fact.FactIngestionResponse
import com.factstore.application.fact.FactIngestionService
import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Facts", description = "Fact ingestion with strict validation against the schema registry")
class FactIngestionController(private val service: FactIngestionService) {

    @PostMapping("/api/v1/commands/facts")
    @PreAuthorize("@authz.has('attestations:write')")
    @Operation(
        summary = "Ingest a fact",
        description = "202 when the fact conforms and is accepted. 400 with error invalid_envelope, unknown_schema " +
            "or schema_nonconformant and a list of violations when it does not; nothing is stored. 409 when the " +
            "fact_id was already ingested."
    )
    fun ingest(@RequestBody body: JsonNode): ResponseEntity<FactIngestionResponse> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(service.ingest(body))
}
