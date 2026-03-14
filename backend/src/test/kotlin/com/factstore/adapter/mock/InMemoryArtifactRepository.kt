package com.factstore.adapter.mock

import com.factstore.core.domain.Artifact
import com.factstore.core.port.outbound.IArtifactRepository
import java.util.UUID

/**
 * In-memory implementation of IArtifactRepository for use in unit tests.
 */
class InMemoryArtifactRepository : IArtifactRepository {
    private val store = mutableMapOf<UUID, Artifact>()

    override fun save(artifact: Artifact): Artifact {
        store[artifact.id] = artifact
        return artifact
    }

    override fun findById(id: UUID): Artifact? = store[id]

    override fun findByTrailId(trailId: UUID): List<Artifact> =
        store.values.filter { it.trailId == trailId }

    override fun findBySha256Digest(sha256Digest: String): List<Artifact> =
        store.values.filter { it.sha256Digest == sha256Digest }

    override fun findBySha256DigestStartingWith(prefix: String): List<Artifact> =
        store.values.filter { it.sha256Digest.startsWith(prefix) }

    override fun findAll(): List<Artifact> = store.values.toList()

    override fun searchByQuery(query: String): List<Artifact> =
        store.values.filter { a ->
            a.imageName.contains(query, ignoreCase = true) ||
                a.imageTag.contains(query, ignoreCase = true) ||
                a.sha256Digest.contains(query, ignoreCase = true) ||
                a.reportedBy.contains(query, ignoreCase = true)
        }
}
