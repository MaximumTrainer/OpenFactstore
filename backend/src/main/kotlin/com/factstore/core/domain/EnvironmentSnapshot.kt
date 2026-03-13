package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "environment_snapshots")
class EnvironmentSnapshot(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "environment_id", nullable = false)
    val environmentId: UUID,

    @Column(name = "snapshot_index", nullable = false)
    val snapshotIndex: Long,

    @Column(name = "recorded_at", nullable = false)
    val recordedAt: Instant = Instant.now(),

    @Column(name = "recorded_by", nullable = false)
    val recordedBy: String
)
