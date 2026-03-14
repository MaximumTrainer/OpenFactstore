package com.factstore.core.port.outbound

import com.factstore.core.domain.EvidenceFile
import java.util.UUID

interface IEvidenceFileRepository {
    fun save(evidenceFile: EvidenceFile): EvidenceFile
    fun findById(id: UUID): EvidenceFile?
    fun findByAttestationId(attestationId: UUID): List<EvidenceFile>
    /**
     * Returns the earliest-stored evidence file with the given SHA-256 hash (ordered by
     * stored_at ascending) so the result is deterministic when multiple records share the
     * same hash.
     */
    fun findTopBySha256HashOrderedByStoredAt(sha256Hash: String): EvidenceFile?
    fun findByTrailId(trailId: UUID): List<EvidenceFile>
}
