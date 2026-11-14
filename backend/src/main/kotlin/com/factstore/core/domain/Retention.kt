package com.factstore.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** What retention does to data past its period (#251). Nothing ever deletes a ledger record. */
enum class RetentionAction {
    /** Destroy the tenant-month data key, so the content is unreadable; the ledger still verifies. */
    SHRED,
    /** Write the record to the retention archive, then remove it from the database. */
    ARCHIVE,
    /** Remove the record. Only for operational data that is not evidence. */
    PRUNE
}

/**
 * The kinds of data a retention policy can govern, each with a fixed action and a minimum period,
 * so a mistyped policy cannot throw away a day's evidence.
 */
enum class RetentionDataClass(val action: RetentionAction, val minimumDays: Int) {
    /** Fact payloads and signed bodies, encrypted under tenant-month data keys (#200). */
    FACT_CONTENT(RetentionAction.SHRED, 365),
    /** Environment snapshots. An environment's latest snapshot is never touched. */
    ENVIRONMENT_SNAPSHOTS(RetentionAction.ARCHIVE, 90),
    /** In-app notifications and notification delivery records. Instance-wide: governed by the `default` tenant. */
    NOTIFICATIONS(RetentionAction.PRUNE, 30)
}

@Entity
@Table(name = "retention_policies")
class RetentionPolicy(
    @Id
    val id: UUID = UUID.randomUUID(),

    /** An organisation slug, or `default` for data that belongs to no organisation. */
    @Column(nullable = false)
    val tenant: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "data_class", nullable = false, length = 64)
    val dataClass: RetentionDataClass,

    @Column(name = "retain_days", nullable = false)
    var retainDays: Int,

    @Column(name = "updated_by", nullable = false)
    var updatedBy: String,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant
)

enum class LegalHoldScope { TRAIL, FLOW }

/**
 * Suspends retention for everything recorded against a trail, or every trail of a flow (#251).
 * A hold is released, never deleted, so the record that it existed stays.
 */
@Entity
@Table(name = "legal_holds")
class LegalHold(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 16)
    val scopeType: LegalHoldScope,

    @Column(name = "scope_id", nullable = false)
    val scopeId: UUID,

    @Column(nullable = false, columnDefinition = "TEXT")
    val reason: String,

    @Column(name = "placed_by", nullable = false)
    val placedBy: String,

    @Column(name = "placed_at", nullable = false)
    val placedAt: Instant,

    @Column(name = "released_by")
    var releasedBy: String? = null,

    @Column(name = "released_at")
    var releasedAt: Instant? = null
) {
    val active: Boolean get() = releasedAt == null
}
