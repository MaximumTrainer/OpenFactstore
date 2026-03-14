package com.factstore.core.port.outbound

/**
 * Outbound port for a secure, tamper-proof evidence storage backend.
 *
 * Implementations may target HashiCorp Vault KV v2, AWS Secrets Manager, or
 * any other secrets engine that provides cryptographic storage and fine-grained
 * access control for compliance evidence artifacts.
 *
 * Path structure: `secret/evidence/{entityType}/{entityId}/{evidenceType}`
 */
interface ISecureEvidenceStore {

    /**
     * Stores evidence key-value data at the structured Vault path.
     *
     * @param entityType  Logical category of the entity (e.g. "software_release", "trail")
     * @param entityId    Identifier of the entity (e.g. a UUID or release tag)
     * @param evidenceType  Type of evidence being stored (e.g. "security_scan", "approval")
     * @param data        Key-value pairs representing the evidence payload
     * @return            Storage receipt containing the Vault path and version
     */
    fun storeEvidence(
        entityType: String,
        entityId: String,
        evidenceType: String,
        data: Map<String, String>
    ): VaultStorageReceipt

    /**
     * Retrieves evidence data stored at the given path. Returns null when no
     * evidence is found or when the secret has been soft-deleted/archived.
     *
     * Throws for operational failures (e.g., Vault unreachable, permission denied)
     * so that callers can distinguish "not found" from infrastructure errors.
     */
    fun retrieveEvidence(
        entityType: String,
        entityId: String,
        evidenceType: String
    ): Map<String, String>?

    /**
     * Returns version and timestamp metadata for the evidence secret without
     * fetching the payload. Returns null when the secret does not exist.
     *
     * Throws for operational failures.
     */
    fun getEvidenceMetadata(
        entityType: String,
        entityId: String,
        evidenceType: String
    ): EvidenceMetadata?

    /**
     * Lists all evidence types recorded for a given entity.
     */
    fun listEvidence(entityType: String, entityId: String): List<String>

    /**
     * Soft-deletes (archives) the latest version of the evidence secret.
     * The data is not permanently destroyed and can be undeleted via the
     * Vault UI/API if required.
     */
    fun deleteEvidence(entityType: String, entityId: String, evidenceType: String)

    /**
     * Returns true when the backing store is reachable and healthy.
     */
    fun isHealthy(): Boolean
}

/** Receipt returned after evidence is written to the secure store. */
data class VaultStorageReceipt(
    /** Full Vault mount path where the secret was written. */
    val path: String,
    /** KV v2 version number of the secret (0 for non-versioned stores). */
    val version: Int,
    /** When the secret was stored (epoch millis). */
    val storedAtEpochMs: Long = System.currentTimeMillis()
)

/** Metadata returned by the KV v2 metadata endpoint for a specific evidence secret. */
data class EvidenceMetadata(
    /** KV v2 current version number. */
    val version: Int,
    /** ISO-8601 timestamp string as returned by Vault (e.g. "2024-01-15T10:30:00.000000000Z"). */
    val createdTime: String?
)

