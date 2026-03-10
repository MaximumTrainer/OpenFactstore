package com.factstore.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "artifacts")
class Artifact(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "trail_id", nullable = false)
    var trailId: UUID,

    @Column(name = "image_name", nullable = false)
    var imageName: String,

    @Column(name = "image_tag", nullable = false)
    var imageTag: String,

    @Column(name = "sha256_digest", nullable = false)
    var sha256Digest: String,

    @Column
    var registry: String? = null,

    @Column(name = "reported_at", nullable = false)
    val reportedAt: Instant = Instant.now(),

    @Column(name = "reported_by", nullable = false)
    var reportedBy: String
)
