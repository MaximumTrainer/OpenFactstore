package com.factstore.core.port.inbound

import com.factstore.core.domain.EvidenceFile
import java.util.UUID

interface IEvidenceVaultService {
    fun store(attestationId: UUID, fileName: String, contentType: String, content: ByteArray): EvidenceFile
    /**
     * Records an evidence reference without uploading binary content.
     * The [externalUrl] points to the actual file (e.g., an S3 pre-signed URL or
     * a private server path). The [sha256Hash] is provided by the caller and stored
     * for later integrity auditing.
     */
    fun storeExternal(
        attestationId: UUID,
        fileName: String,
        contentType: String,
        externalUrl: String,
        sha256Hash: String,
        fileSizeBytes: Long
    ): EvidenceFile
    fun findByAttestationId(attestationId: UUID): List<EvidenceFile>
    fun verifyIntegrity(id: UUID): Boolean
    /** Returns the first evidence file matching the given SHA-256 hash, or null if not found. */
    fun findBySha256Hash(sha256Hash: String): EvidenceFile?
    /** Returns all evidence files attached to attestations that belong to the given trail. */
    fun findByTrailId(trailId: UUID): List<EvidenceFile>
}

