package com.factstore.core.port.inbound

import com.factstore.dto.StoreEvidenceRequest
import com.factstore.dto.VaultEvidenceResponse
import com.factstore.dto.VaultEvidenceListResponse
import com.factstore.dto.VaultHealthResponse

/**
 * Inbound service port for the HashiCorp Vault-backed secure evidence store.
 *
 * Evidence is stored at structured Vault KV v2 paths:
 *   `secret/evidence/{entityType}/{entityId}/{evidenceType}`
 */
interface IVaultEvidenceService {

    /** Store (or overwrite) evidence for a given entity in Vault. */
    fun storeEvidence(
        entityType: String,
        entityId: String,
        request: StoreEvidenceRequest
    ): VaultEvidenceResponse

    /** Retrieve evidence metadata for a given entity. */
    fun retrieveEvidence(
        entityType: String,
        entityId: String,
        evidenceType: String
    ): VaultEvidenceResponse

    /** List all evidence types available for a given entity. */
    fun listEvidence(entityType: String, entityId: String): VaultEvidenceListResponse

    /**
     * Download the raw evidence payload for a given entity.
     *
     * Returns a map of key-value pairs as stored in Vault.
     */
    fun downloadEvidence(
        entityType: String,
        entityId: String,
        evidenceType: String
    ): Map<String, String>

    /**
     * Soft-delete (archive) evidence for a given entity.
     * The secret is marked as deleted in Vault but not permanently destroyed.
     */
    fun deleteEvidence(entityType: String, entityId: String, evidenceType: String)

    /** Check Vault connectivity and return a health status response. */
    fun getHealth(): VaultHealthResponse
}
