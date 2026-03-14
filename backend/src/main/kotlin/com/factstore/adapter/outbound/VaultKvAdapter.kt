package com.factstore.adapter.outbound

import com.factstore.config.VaultProperties
import com.factstore.core.port.outbound.EvidenceMetadata
import com.factstore.core.port.outbound.ISecureEvidenceStore
import com.factstore.core.port.outbound.VaultStorageReceipt
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.vault.core.VaultTemplate
import org.springframework.vault.support.VaultResponse

/**
 * HashiCorp Vault KV v2 adapter for secure evidence storage.
 *
 * Stores compliance evidence at structured paths within the Vault KV backend:
 *   `{kv.backend}/data/evidence/{entityType}/{entityId}/{evidenceType}`
 *
 * This adapter is activated only when `vault.enabled=true` in application configuration.
 * For local development, start the Vault dev server via Docker Compose.
 *
 * Authentication methods supported: TOKEN, APPROLE.
 */
@Component
@ConditionalOnProperty(name = ["vault.enabled"], havingValue = "true")
class VaultKvAdapter(
    private val vaultTemplate: VaultTemplate,
    private val vaultProperties: VaultProperties
) : ISecureEvidenceStore {

    private val log = LoggerFactory.getLogger(VaultKvAdapter::class.java)

    private val kvBackend: String get() = vaultProperties.kv.backend

    override fun storeEvidence(
        entityType: String,
        entityId: String,
        evidenceType: String,
        data: Map<String, String>
    ): VaultStorageReceipt {
        val path = buildPath(entityType, entityId, evidenceType)
        log.info("Storing evidence at Vault path: {}", path)
        val kvDataPath = kvDataPath(entityType, entityId, evidenceType)
        val response: VaultResponse? = vaultTemplate.write(kvDataPath, mapOf("data" to data))
        val version = extractVersion(response)
        return VaultStorageReceipt(path = path, version = version)
    }

    override fun retrieveEvidence(
        entityType: String,
        entityId: String,
        evidenceType: String
    ): Map<String, String>? {
        val path = kvDataPath(entityType, entityId, evidenceType)
        log.debug("Retrieving evidence from Vault path: {}", path)
        // vaultTemplate.read() returns null when the secret does not exist.
        // Operational failures (Vault down, permission denied) throw — we let those propagate
        // so callers can distinguish "not found" (null → 404) from infrastructure errors (5xx).
        val response = vaultTemplate.read(path) ?: return null
        @Suppress("UNCHECKED_CAST")
        return response.data?.get("data") as? Map<String, String>
    }

    override fun getEvidenceMetadata(
        entityType: String,
        entityId: String,
        evidenceType: String
    ): EvidenceMetadata? {
        val metaPath = kvMetadataPath(entityType, entityId, evidenceType)
        log.debug("Reading evidence metadata from Vault path: {}", metaPath)
        val response = vaultTemplate.read(metaPath) ?: return null
        val currentVersion = (response.data?.get("current_version") as? Number)?.toInt() ?: run {
            log.warn("Could not determine current_version from Vault metadata at path: {}", metaPath)
            0
        }
        @Suppress("UNCHECKED_CAST")
        val versions = response.data?.get("versions") as? Map<String, Map<String, Any?>>
        val createdTime = versions?.get(currentVersion.toString())?.get("created_time") as? String
        return EvidenceMetadata(version = currentVersion, createdTime = createdTime)
    }

    override fun listEvidence(entityType: String, entityId: String): List<String> {
        val listPath = "${kvBackend}/metadata/evidence/$entityType/$entityId"
        log.debug("Listing evidence keys at Vault path: {}", listPath)
        return try {
            vaultTemplate.list(listPath) ?: emptyList()
        } catch (e: Exception) {
            log.warn("Failed to list evidence at Vault path {}: {}", listPath, e.message)
            emptyList()
        }
    }

    override fun deleteEvidence(entityType: String, entityId: String, evidenceType: String) {
        val path = kvDataPath(entityType, entityId, evidenceType)
        log.info("Soft-deleting evidence at Vault path: {}", path)
        vaultTemplate.delete(path)
    }

    override fun isHealthy(): Boolean {
        return try {
            vaultTemplate.opsForSys().health().isInitialized
        } catch (e: Exception) {
            log.warn("Vault health check failed: {}", e.message)
            false
        }
    }

    /**
     * Logical KV v2 data path for reading/writing:
     *   `{backend}/data/evidence/{entityType}/{entityId}/{evidenceType}`
     */
    private fun kvDataPath(entityType: String, entityId: String, evidenceType: String): String =
        "$kvBackend/data/evidence/$entityType/$entityId/$evidenceType"

    /**
     * KV v2 metadata path for reading version/timestamp information:
     *   `{backend}/metadata/evidence/{entityType}/{entityId}/{evidenceType}`
     */
    private fun kvMetadataPath(entityType: String, entityId: String, evidenceType: String): String =
        "$kvBackend/metadata/evidence/$entityType/$entityId/$evidenceType"

    /**
     * Human-readable canonical path (without the internal `/data/` prefix).
     */
    private fun buildPath(entityType: String, entityId: String, evidenceType: String): String =
        "$kvBackend/evidence/$entityType/$entityId/$evidenceType"

    /**
     * Extracts the KV v2 version from the write response.
     * For Vault KV v2, the response data contains the version directly:
     *   `{ "version": 1, "created_time": "...", ... }`.
     */
    private fun extractVersion(response: VaultResponse?): Int =
        (response?.data?.get("version") as? Number)?.toInt() ?: 0
}

