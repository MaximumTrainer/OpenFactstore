package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "deployments")
data class Deployment(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false) val artifactSha256: String,
    @Column(nullable = false) val environmentId: UUID,
    @Column(nullable = false) val snapshotIndex: Long,
    @Column(nullable = false) val deployedAt: Instant = Instant.now()
)
