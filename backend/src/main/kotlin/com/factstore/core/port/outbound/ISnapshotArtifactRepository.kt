package com.factstore.core.port.outbound

import com.factstore.core.domain.SnapshotArtifact
import java.util.UUID

interface ISnapshotArtifactRepository {
    fun saveAll(artifacts: List<SnapshotArtifact>): List<SnapshotArtifact>
    fun findAllBySnapshotId(snapshotId: UUID): List<SnapshotArtifact>
    fun findAllBySnapshotIdIn(snapshotIds: List<UUID>): List<SnapshotArtifact>
    fun findAllByArtifactSha256(artifactSha256: String): List<SnapshotArtifact>
}
