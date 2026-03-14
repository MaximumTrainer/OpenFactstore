package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class BundleStatus { INACTIVE, ACTIVE }

@Entity
@Table(name = "opa_bundles")
class OpaBundle(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val version: String,

    @Column(columnDefinition = "TEXT", nullable = false)
    val regoContent: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BundleStatus = BundleStatus.INACTIVE,

    @Column(name = "org_slug")
    val orgSlug: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
