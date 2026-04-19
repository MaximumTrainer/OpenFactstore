package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.Attestation
import com.factstore.core.port.outbound.IAttestationRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AttestationRepositoryJpa : JpaRepository<Attestation, UUID> {
    fun findByTrailId(trailId: UUID): List<Attestation>
    fun findByTrailId(trailId: UUID, pageable: Pageable): Page<Attestation>
    fun findByTrailIdIn(trailIds: Collection<UUID>): List<Attestation>
    fun findByArtifactFingerprint(fingerprint: String): List<Attestation>
}

@Component
class AttestationRepositoryAdapter(private val jpa: AttestationRepositoryJpa) : IAttestationRepository {
    override fun save(attestation: Attestation): Attestation = jpa.save(attestation)
    override fun findById(id: UUID): Attestation? = jpa.findById(id).orElse(null)
    override fun findByTrailId(trailId: UUID): List<Attestation> = jpa.findByTrailId(trailId)
    override fun findByTrailId(trailId: UUID, pageable: Pageable): Page<Attestation> = jpa.findByTrailId(trailId, pageable)
    override fun findByTrailIdIn(trailIds: Collection<UUID>): List<Attestation> = jpa.findByTrailIdIn(trailIds)
    override fun findAll(): List<Attestation> = jpa.findAll()
    override fun findByArtifactFingerprint(fingerprint: String): List<Attestation> = jpa.findByArtifactFingerprint(fingerprint)
}
