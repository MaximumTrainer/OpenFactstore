package com.factstore.adapter.mock

import com.factstore.core.domain.SnapshotArtifact
import com.factstore.core.port.outbound.ISnapshotArtifactRepository
import java.util.UUID

class InMemorySnapshotArtifactRepository : ISnapshotArtifactRepository {
    private val store = mutableMapOf<UUID, SnapshotArtifact>()

    override fun saveAll(artifacts: List<SnapshotArtifact>): List<SnapshotArtifact> {
        artifacts.forEach { store[it.id] = it }
        return artifacts
    }

    override fun findAllBySnapshotId(snapshotId: UUID): List<SnapshotArtifact> =
        store.values.filter { it.snapshotId == snapshotId }

    override fun findAllByArtifactSha256(artifactSha256: String): List<SnapshotArtifact> =
        store.values.filter { it.artifactSha256 == artifactSha256 }
}
