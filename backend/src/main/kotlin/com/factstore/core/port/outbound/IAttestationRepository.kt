package com.factstore.core.port.outbound

import com.factstore.core.domain.Attestation
import java.util.UUID

interface IAttestationRepository {
    fun save(attestation: Attestation): Attestation
    fun findById(id: UUID): Attestation?
    fun findByTrailId(trailId: UUID): List<Attestation>
    fun findByTrailIdIn(trailIds: Collection<UUID>): List<Attestation>
}
