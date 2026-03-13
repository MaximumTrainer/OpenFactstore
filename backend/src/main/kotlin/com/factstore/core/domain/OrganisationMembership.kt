package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class MemberRole { ADMIN, MEMBER, VIEWER, SERVICE_ACCOUNT }

@Entity
@Table(
    name = "organisation_memberships",
    uniqueConstraints = [UniqueConstraint(name = "uq_org_memberships_org_user", columnNames = ["org_slug", "user_id"])]
)
class OrganisationMembership(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "org_slug", nullable = false, length = 255)
    val orgSlug: String,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: MemberRole,

    @Column(name = "joined_at", nullable = false)
    val joinedAt: Instant = Instant.now()
)
