package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.EvidenceFile
import com.factstore.core.port.outbound.IEvidenceFileRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EvidenceFileRepositoryJpa : JpaRepository<EvidenceFile, UUID> {
    fun findByAttestationId(attestationId: UUID): List<EvidenceFile>
    fun findFirstBySha256Hash(sha256Hash: String): EvidenceFile?

    @Query("""
        SELECT ef FROM EvidenceFile ef
        WHERE ef.attestationId IN (
            SELECT a.id FROM Attestation a WHERE a.trailId = :trailId
        )
    """)
    fun findByTrailId(@Param("trailId") trailId: UUID): List<EvidenceFile>
}

@Component
class EvidenceFileRepositoryAdapter(private val jpa: EvidenceFileRepositoryJpa) : IEvidenceFileRepository {
    override fun save(evidenceFile: EvidenceFile): EvidenceFile = jpa.save(evidenceFile)
    override fun findById(id: UUID): EvidenceFile? = jpa.findById(id).orElse(null)
    override fun findByAttestationId(attestationId: UUID): List<EvidenceFile> = jpa.findByAttestationId(attestationId)
    override fun findFirstBySha256Hash(sha256Hash: String): EvidenceFile? = jpa.findFirstBySha256Hash(sha256Hash)
    override fun findByTrailId(trailId: UUID): List<EvidenceFile> = jpa.findByTrailId(trailId)
}
