package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "drift_reports")
class DriftReport(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "environment_id", nullable = false) val environmentId: UUID,
    @Column(name = "baseline_id") val baselineId: UUID? = null,
    @Column(name = "snapshot_id", nullable = false) val snapshotId: UUID,
    @Column(name = "generated_at", nullable = false) val generatedAt: Instant = Instant.now(),
    @Column(name = "has_drift", nullable = false) val hasDrift: Boolean,
    @Column(name = "added_artifacts", columnDefinition = "TEXT") var addedArtifactsRaw: String = "",
    @Column(name = "removed_artifacts", columnDefinition = "TEXT") var removedArtifactsRaw: String = "",
    @Column(name = "updated_artifacts", columnDefinition = "TEXT") var updatedArtifactsRaw: String = ""
)
