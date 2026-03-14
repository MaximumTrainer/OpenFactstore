package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IVaultEvidenceService
import com.factstore.dto.StoreEvidenceRequest
import com.factstore.dto.VaultEvidenceListResponse
import com.factstore.dto.VaultEvidenceResponse
import com.factstore.dto.VaultHealthResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for HashiCorp Vault-backed secure evidence storage.
 *
 * All endpoints follow the path pattern:
 *   `/api/v1/evidence/{entityType}/{entityId}`
 *
 * Evidence is stored in the configured Vault KV v2 backend at:
 *   `{vault.kv.backend}/evidence/{entityType}/{entityId}/{evidenceType}`
 *
 * This controller is only active when `vault.enabled=true`.
 */
@RestController
@RequestMapping("/api/v1/evidence")
@Tag(name = "Vault Evidence", description = "HashiCorp Vault-backed secure evidence storage")
@ConditionalOnProperty(name = ["vault.enabled"], havingValue = "true")
class VaultEvidenceController(
    private val vaultEvidenceService: IVaultEvidenceService
) {

    @PostMapping("/{entityType}/{entityId}")
    @Operation(summary = "Store evidence in Vault", description = "Stores evidence key-value data in Vault KV v2 at the configured KV backend under evidence/{entityType}/{entityId}/{evidenceType}")
    fun storeEvidence(
        @PathVariable entityType: String,
        @PathVariable entityId: String,
        @RequestBody request: StoreEvidenceRequest
    ): ResponseEntity<VaultEvidenceResponse> {
        val response = vaultEvidenceService.storeEvidence(entityType, entityId, request)
        return ResponseEntity.status(201).body(response)
    }

    @GetMapping("/{entityType}/{entityId}")
    @Operation(summary = "Retrieve evidence metadata", description = "Returns evidence metadata stored in Vault for the given entity")
    fun retrieveEvidence(
        @PathVariable entityType: String,
        @PathVariable entityId: String,
        @org.springframework.web.bind.annotation.RequestParam evidenceType: String
    ): ResponseEntity<VaultEvidenceResponse> =
        ResponseEntity.ok(vaultEvidenceService.retrieveEvidence(entityType, entityId, evidenceType))

    @GetMapping("/{entityType}/{entityId}/list")
    @Operation(summary = "List evidence types for an entity", description = "Returns all evidence types stored in Vault for the given entity")
    fun listEvidence(
        @PathVariable entityType: String,
        @PathVariable entityId: String
    ): ResponseEntity<VaultEvidenceListResponse> =
        ResponseEntity.ok(vaultEvidenceService.listEvidence(entityType, entityId))

    @GetMapping("/{entityType}/{entityId}/download")
    @Operation(summary = "Download evidence artifact", description = "Returns the raw evidence key-value payload from Vault")
    fun downloadEvidence(
        @PathVariable entityType: String,
        @PathVariable entityId: String,
        @org.springframework.web.bind.annotation.RequestParam evidenceType: String
    ): ResponseEntity<Map<String, String>> {
        val data = vaultEvidenceService.downloadEvidence(entityType, entityId, evidenceType)
        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"evidence-$entityType-$entityId-$evidenceType.json\""
            )
            .contentType(MediaType.APPLICATION_JSON)
            .body(data)
    }

    @DeleteMapping("/{entityType}/{entityId}")
    @Operation(summary = "Soft-delete evidence", description = "Marks evidence as archived in Vault (soft-delete — data is not permanently destroyed)")
    fun deleteEvidence(
        @PathVariable entityType: String,
        @PathVariable entityId: String,
        @org.springframework.web.bind.annotation.RequestParam evidenceType: String
    ): ResponseEntity<Void> {
        vaultEvidenceService.deleteEvidence(entityType, entityId, evidenceType)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/health")
    @Operation(summary = "Vault connectivity health check", description = "Checks whether the Vault server is reachable and initialised")
    fun health(): ResponseEntity<VaultHealthResponse> =
        ResponseEntity.ok(vaultEvidenceService.getHealth())
}
