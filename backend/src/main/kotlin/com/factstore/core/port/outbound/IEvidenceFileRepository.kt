package com.factstore.core.port.outbound

import com.factstore.core.domain.EvidenceFile
import java.util.UUID

interface IEvidenceFileRepository {
    fun save(evidenceFile: EvidenceFile): EvidenceFile
    fun findById(id: UUID): EvidenceFile?
    fun findByAttestationId(attestationId: UUID): List<EvidenceFile>
    fun findFirstBySha256Hash(sha256Hash: String): EvidenceFile?
    fun findByTrailId(trailId: UUID): List<EvidenceFile>
}
