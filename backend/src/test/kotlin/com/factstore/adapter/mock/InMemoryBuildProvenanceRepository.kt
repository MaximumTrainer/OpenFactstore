package com.factstore.adapter.mock

import com.factstore.core.domain.BuildProvenance
import com.factstore.core.port.outbound.IBuildProvenanceRepository
import java.util.UUID

/**
 * In-memory implementation of IBuildProvenanceRepository for use in unit tests.
 */
class InMemoryBuildProvenanceRepository : IBuildProvenanceRepository {
    private val store = mutableMapOf<UUID, BuildProvenance>()

    override fun save(provenance: BuildProvenance): BuildProvenance {
        store[provenance.id] = provenance
        return provenance
    }

    override fun findByArtifactId(artifactId: UUID): BuildProvenance? =
        store.values.firstOrNull { it.artifactId == artifactId }

    override fun findByArtifactIdIn(artifactIds: List<UUID>): List<BuildProvenance> =
        store.values.filter { it.artifactId in artifactIds }

    override fun existsByArtifactId(artifactId: UUID): Boolean =
        store.values.any { it.artifactId == artifactId }
}
