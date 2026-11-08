package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IServiceAccountService
import com.factstore.dto.ApiKeyCreatedResponse
import com.factstore.dto.ApiKeyResponse
import com.factstore.dto.CreateServiceAccountRequest
import com.factstore.dto.ServiceAccountResponse
import com.factstore.dto.UpdateServiceAccountRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class CreateServiceAccountApiKeyRequest(
    val label: String,
    val ttlDays: Int? = null
)

@RestController
@RequestMapping("/api/v1/service-accounts")
@Tag(name = "Service Accounts", description = "Service account and associated API key management")
class ServiceAccountController(private val serviceAccountService: IServiceAccountService) {

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "Create a new service account")
    fun createServiceAccount(
        @RequestBody request: CreateServiceAccountRequest
    ): ResponseEntity<ServiceAccountResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(serviceAccountService.createServiceAccount(request))

    @PreAuthorize("@authz.authenticated()")
    @GetMapping
    @Operation(summary = "List all service accounts")
    fun listServiceAccounts(): ResponseEntity<List<ServiceAccountResponse>> =
        ResponseEntity.ok(serviceAccountService.listServiceAccounts())

    @PreAuthorize("@authz.authenticated()")
    @GetMapping("/{id}")
    @Operation(summary = "Get service account details")
    fun getServiceAccount(@PathVariable id: UUID): ResponseEntity<ServiceAccountResponse> =
        ResponseEntity.ok(serviceAccountService.getServiceAccount(id))

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "Update a service account")
    fun updateServiceAccount(
        @PathVariable id: UUID,
        @RequestBody request: UpdateServiceAccountRequest
    ): ResponseEntity<ServiceAccountResponse> =
        ResponseEntity.ok(serviceAccountService.updateServiceAccount(id, request))

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(
        summary = "Delete a service account",
        description = "Deletes the service account and all its API keys."
    )
    fun deleteServiceAccount(@PathVariable id: UUID): ResponseEntity<Void> {
        serviceAccountService.deleteServiceAccount(id)
        return ResponseEntity.noContent().build()
    }

    // --- API key sub-resource ---

    @PostMapping("/{id}/api-keys")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(
        summary = "Generate a new API key for a service account",
        description = "The plain-text key is returned **once** — store it securely."
    )
    fun createApiKey(
        @PathVariable id: UUID,
        @RequestBody request: CreateServiceAccountApiKeyRequest
    ): ResponseEntity<ApiKeyCreatedResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(serviceAccountService.createApiKey(id, request.label, request.ttlDays))

    @PreAuthorize("@authz.authenticated()")
    @GetMapping("/{id}/api-keys")
    @Operation(
        summary = "List API keys for a service account",
        description = "Returns key metadata only — the raw key is never retrievable after creation."
    )
    fun listApiKeys(@PathVariable id: UUID): ResponseEntity<List<ApiKeyResponse>> =
        ResponseEntity.ok(serviceAccountService.listApiKeys(id))

    @DeleteMapping("/{id}/api-keys/{keyId}")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "Revoke an API key for a service account")
    fun revokeApiKey(
        @PathVariable id: UUID,
        @PathVariable keyId: UUID
    ): ResponseEntity<Void> {
        serviceAccountService.revokeApiKey(id, keyId)
        return ResponseEntity.noContent().build()
    }
}
