package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "environment_baselines")
class EnvironmentBaseline(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "environment_id", nullable = false) val environmentId: UUID,
    @Column(name = "snapshot_id") var snapshotId: UUID? = null,
    @Column(name = "approved_by", nullable = false) val approvedBy: String,
    @Column(name = "approved_at", nullable = false) val approvedAt: Instant = Instant.now(),
    @Column(nullable = true, columnDefinition = "TEXT") var description: String? = null,
    @Column(name = "is_active", nullable = false) var isActive: Boolean = true
)
