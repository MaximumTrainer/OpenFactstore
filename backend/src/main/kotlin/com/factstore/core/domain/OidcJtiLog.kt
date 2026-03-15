package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "oidc_jti_log")
class OidcJtiLog(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true) val jti: String,
    @Column(nullable = false) val issuer: String,
    @Column(name = "used_at", nullable = false) val usedAt: Instant = Instant.now()
)
