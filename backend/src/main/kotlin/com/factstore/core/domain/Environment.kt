package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class EnvironmentType { K8S, S3, LAMBDA, GENERIC }

enum class DriftPolicy { WARN, BLOCK, IGNORE }

@Entity
@Table(name = "environments")
class Environment(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(unique = true, nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: EnvironmentType,

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String = "",

    @Column(name = "org_slug", nullable = true, length = 255)
    var orgSlug: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Enumerated(EnumType.STRING)
    @Column(name = "drift_policy", nullable = false)
    var driftPolicy: DriftPolicy = DriftPolicy.WARN
)
