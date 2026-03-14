package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "regulatory_frameworks")
class RegulatoryFramework(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false) val name: String,
    @Column(nullable = false) val version: String,
    @Column(columnDefinition = "TEXT") val description: String? = null,
    @Column(name = "is_active") var isActive: Boolean = true,
    @Column(name = "org_slug") val orgSlug: String? = null,
    @Column(name = "created_at") val createdAt: Instant = Instant.now()
)
