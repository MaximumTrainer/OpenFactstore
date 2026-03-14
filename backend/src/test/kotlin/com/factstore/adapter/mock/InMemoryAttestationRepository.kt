package com.factstore.adapter.mock

import com.factstore.core.domain.Attestation
import com.factstore.core.port.outbound.IAttestationRepository
import java.util.UUID

class InMemoryAttestationRepository : IAttestationRepository {
    private val store = mutableMapOf<UUID, Attestation>()

    override fun save(attestation: Attestation): Attestation {
        store[attestation.id] = attestation
        return attestation
    }

    override fun findById(id: UUID): Attestation? = store[id]

    override fun findByTrailId(trailId: UUID): List<Attestation> =
        store.values.filter { it.trailId == trailId }
}
