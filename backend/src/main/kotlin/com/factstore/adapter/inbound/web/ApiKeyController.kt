package com.factstore.adapter.inbound.web

import com.factstore.core.domain.security.Permission
import com.factstore.core.port.inbound.IApiKeyService
import com.factstore.dto.ApiKeyCreatedResponse
import com.factstore.dto.ApiKeyResponse
import com.factstore.dto.CreateApiKeyRequest
import com.factstore.dto.RotateApiKeyRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/api-keys")
@Tag(name = "API Keys", description = "API key management: scopes, rotation and revocation")
class ApiKeyController(private val apiKeyService: IApiKeyService) {

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(
        summary = "Create an API key",
        description = "The plain-text key is returned **once**. Scopes default to a read-only " +
            "set; pass `preset: CI_PIPELINE` for the common pipeline case. A caller cannot " +
            "grant scopes it does not itself hold, and the TTL is capped."
    )
    fun createApiKey(@RequestBody request: CreateApiKeyRequest): ResponseEntity<ApiKeyCreatedResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(apiKeyService.createApiKey(request))

    @PostMapping("/{id}/rotate")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(
        summary = "Rotate an API key",
        description = "Issues a replacement with the same scopes and returns its plain text " +
            "once. The previous key keeps working for the overlap window (default 24h), so a " +
            "pipeline can roll over without an outage."
    )
    fun rotateApiKey(
        @PathVariable id: UUID,
        @RequestBody(required = false) request: RotateApiKeyRequest?
    ): ResponseEntity<ApiKeyCreatedResponse> =
        ResponseEntity.ok(apiKeyService.rotateApiKey(id, request?.overlapHours))

    @GetMapping("/owners/{ownerId}")
    @PreAuthorize("hasAuthority('SCOPE_admin') or @userAccessPolicy.isSelf(#ownerId)")
    @Operation(
        summary = "List API keys for an owner",
        description = "Never returns key material. `expiringSoon` and `daysUntilExpiry` let a " +
            "client warn before a pipeline breaks."
    )
    fun listApiKeysForOwner(@PathVariable ownerId: UUID): ResponseEntity<List<ApiKeyResponse>> =
        ResponseEntity.ok(apiKeyService.listApiKeysForOwner(ownerId))

    @PreAuthorize("@authz.authenticated()")
    @GetMapping("/scopes")
    @Operation(
        summary = "The scope vocabulary",
        description = "The valid `resource:action` scopes and the presets, so a client does not " +
            "have to hard-code them."
    )
    fun listScopes(): ResponseEntity<Map<String, Any>> = ResponseEntity.ok(
        mapOf(
            "scopes" to Permission.entries.map { mapOf("scope" to it.scope, "name" to it.name) },
            "presets" to mapOf(
                "CI_PIPELINE" to Permission.CI_PIPELINE_PRESET.map { it.scope }.sorted(),
                "READ_ONLY" to Permission.DEFAULT_MINIMAL.map { it.scope }.sorted()
            )
        )
    )

    @DeleteMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(
        summary = "Revoke an API key",
        description = "Takes effect on the next request: the cached validation is dropped too."
    )
    fun revokeApiKey(@PathVariable id: UUID): ResponseEntity<Void> {
        apiKeyService.revokeApiKey(id)
        return ResponseEntity.noContent().build()
    }
}
