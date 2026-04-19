package com.factstore.adapter.mock

import com.factstore.core.domain.Attestation
import com.factstore.core.port.outbound.IAttestationRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
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

    override fun findByTrailId(trailId: UUID, pageable: Pageable): Page<Attestation> {
        val all = store.values.filter { it.trailId == trailId }
        val start = (pageable.pageNumber * pageable.pageSize).coerceAtMost(all.size)
        val end = (start + pageable.pageSize).coerceAtMost(all.size)
        return PageImpl(all.subList(start, end), pageable, all.size.toLong())
    }

    override fun findByTrailIdIn(trailIds: Collection<UUID>): List<Attestation> =
        store.values.filter { it.trailId in trailIds }

    override fun findAll(): List<Attestation> = store.values.toList()
    override fun findByArtifactFingerprint(fingerprint: String): List<Attestation> =
        store.values.filter { it.artifactFingerprint == fingerprint }
}
