package com.factstore.repository

import com.factstore.domain.EvidenceFile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EvidenceFileRepository : JpaRepository<EvidenceFile, UUID> {
    fun findByAttestationId(attestationId: UUID): List<EvidenceFile>
}
