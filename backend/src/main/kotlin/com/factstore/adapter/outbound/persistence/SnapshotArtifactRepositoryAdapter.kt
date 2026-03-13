package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.SnapshotArtifact
import com.factstore.core.port.outbound.ISnapshotArtifactRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SnapshotArtifactRepositoryJpa : JpaRepository<SnapshotArtifact, UUID> {
    fun findAllBySnapshotId(snapshotId: UUID): List<SnapshotArtifact>
    fun findAllBySnapshotIdIn(snapshotIds: List<UUID>): List<SnapshotArtifact>
    fun findAllByArtifactSha256(artifactSha256: String): List<SnapshotArtifact>
}

@Component
class SnapshotArtifactRepositoryAdapter(private val jpa: SnapshotArtifactRepositoryJpa) : ISnapshotArtifactRepository {
    override fun saveAll(artifacts: List<SnapshotArtifact>): List<SnapshotArtifact> = jpa.saveAll(artifacts)
    override fun findAllBySnapshotId(snapshotId: UUID): List<SnapshotArtifact> =
        jpa.findAllBySnapshotId(snapshotId)
    override fun findAllBySnapshotIdIn(snapshotIds: List<UUID>): List<SnapshotArtifact> =
        jpa.findAllBySnapshotIdIn(snapshotIds)
    override fun findAllByArtifactSha256(artifactSha256: String): List<SnapshotArtifact> =
        jpa.findAllByArtifactSha256(artifactSha256)
}
