package com.factstore.adapter.inbound.web

import com.factstore.application.schema.FactSchemaResponse
import com.factstore.application.schema.SchemaRegistryService
import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * The schema registry API (#195), on the command/query split the compliance spec uses: writes
 * under `/api/v1/commands`, reads under `/api/v1/queries`.
 */
@RestController
@Tag(name = "Schema registry", description = "Versioned JSON Schemas that facts declare in governance.schema_ref")
class SchemaRegistryController(private val registry: SchemaRegistryService) {

    @PutMapping("/api/v1/commands/schemas/{name}/v{version}")
    @PreAuthorize("@authz.has('admin')")
    @Operation(
        summary = "Publish a version of a fact schema",
        description = "201 when published, 200 when the same content was already published, 409 when the version " +
            "exists with different content or breaks compatibility with the previous version, 400 for an " +
            "invalid JSON Schema or an out-of-order version."
    )
    fun publish(
        @PathVariable name: String,
        @PathVariable version: Int,
        @RequestBody schema: JsonNode
    ): ResponseEntity<FactSchemaResponse> {
        val result = registry.publish(name, version, schema)
        return ResponseEntity.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK).body(result.schema)
    }

    @GetMapping("/api/v1/queries/schemas/{name}/v{version}")
    @PreAuthorize("@authz.has('policies:read')")
    @Operation(summary = "Read one version of a fact schema")
    fun get(@PathVariable name: String, @PathVariable version: Int): ResponseEntity<FactSchemaResponse> =
        ResponseEntity.ok(registry.get(name, version))

    @GetMapping("/api/v1/queries/schemas/{name}")
    @PreAuthorize("@authz.has('policies:read')")
    @Operation(summary = "List the published versions of a fact schema")
    fun list(@PathVariable name: String): ResponseEntity<List<FactSchemaResponse>> =
        ResponseEntity.ok(registry.list(name))
}
