package com.factstore.core.port.outbound

import com.factstore.core.domain.Attestation
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

interface IAttestationRepository {
    fun save(attestation: Attestation): Attestation
    fun findById(id: UUID): Attestation?
    fun findByTrailId(trailId: UUID): List<Attestation>
    fun findByTrailId(trailId: UUID, pageable: Pageable): Page<Attestation>
    fun findByTrailIdIn(trailIds: Collection<UUID>): List<Attestation>
    fun findAll(): List<Attestation>
    fun findByArtifactFingerprint(fingerprint: String): List<Attestation>
}
