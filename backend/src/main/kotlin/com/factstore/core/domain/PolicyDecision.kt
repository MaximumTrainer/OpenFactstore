package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "policy_decisions")
class PolicyDecision(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "bundle_id")
    val bundleId: UUID? = null,

    @Column(name = "input_json", columnDefinition = "TEXT", nullable = false)
    val inputJson: String,

    @Column(name = "result_allow", nullable = false)
    val resultAllow: Boolean,

    @Column(name = "deny_reasons", columnDefinition = "TEXT")
    val denyReasons: String? = null,

    @Column(name = "org_slug")
    val orgSlug: String? = null,

    @Column(name = "evaluated_at", nullable = false)
    val evaluatedAt: Instant = Instant.now()
)
