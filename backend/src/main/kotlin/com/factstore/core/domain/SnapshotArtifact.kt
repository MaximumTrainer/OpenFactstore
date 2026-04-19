package com.factstore.core.domain

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "snapshot_artifacts")
class SnapshotArtifact(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "snapshot_id", nullable = false)
    val snapshotId: UUID,

    @Column(name = "artifact_sha256", nullable = false)
    val artifactSha256: String,

    @Column(name = "artifact_name", nullable = false)
    val artifactName: String,

    @Column(name = "artifact_tag", nullable = false)
    val artifactTag: String,

    @Column(name = "instance_count", nullable = false)
    val instanceCount: Int = 1,

    @Column(name = "compliance_state")
    var complianceState: String? = null
)
