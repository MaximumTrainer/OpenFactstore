package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.Attestation
import com.factstore.core.port.outbound.IAttestationRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AttestationRepositoryJpa : JpaRepository<Attestation, UUID> {
    fun findByTrailId(trailId: UUID): List<Attestation>
    fun findByTrailIdIn(trailIds: Collection<UUID>): List<Attestation>
}

@Component
class AttestationRepositoryAdapter(private val jpa: AttestationRepositoryJpa) : IAttestationRepository {
    override fun save(attestation: Attestation): Attestation = jpa.save(attestation)
    override fun findById(id: UUID): Attestation? = jpa.findById(id).orElse(null)
    override fun findByTrailId(trailId: UUID): List<Attestation> = jpa.findByTrailId(trailId)
    override fun findByTrailIdIn(trailIds: Collection<UUID>): List<Attestation> = jpa.findByTrailIdIn(trailIds)
    override fun findAll(): List<Attestation> = jpa.findAll()
}
