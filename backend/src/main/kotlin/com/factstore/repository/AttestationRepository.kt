package com.factstore.repository

import com.factstore.domain.Attestation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AttestationRepository : JpaRepository<Attestation, UUID> {
    fun findByTrailId(trailId: UUID): List<Attestation>
}
