package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IApiKeyService
import com.factstore.dto.ApiKeyCreatedResponse
import com.factstore.dto.ApiKeyResponse
import com.factstore.dto.CreateApiKeyRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/api-keys")
@Tag(name = "API Keys", description = "API key management (Personal and Service Account keys)")
class ApiKeyController(private val apiKeyService: IApiKeyService) {

    @PostMapping
    @Operation(
        summary = "Create a new API key",
        description = "Generates a new API key for a user. The plain-text key is returned **once** — store it securely."
    )
    fun createApiKey(@RequestBody request: CreateApiKeyRequest): ResponseEntity<ApiKeyCreatedResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(apiKeyService.createApiKey(request))

    @GetMapping("/users/{userId}")
    @Operation(summary = "List API keys for a user")
    fun listApiKeysForUser(@PathVariable userId: UUID): ResponseEntity<List<ApiKeyResponse>> =
        ResponseEntity.ok(apiKeyService.listApiKeysForUser(userId))

    @DeleteMapping("/{id}/revoke")
    @Operation(summary = "Revoke an API key")
    fun revokeApiKey(@PathVariable id: UUID): ResponseEntity<Void> {
        apiKeyService.revokeApiKey(id)
        return ResponseEntity.noContent().build()
    }
}
