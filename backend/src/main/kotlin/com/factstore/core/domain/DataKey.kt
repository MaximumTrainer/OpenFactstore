package com.factstore.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

enum class DataKeyStatus { ACTIVE, SHREDDED }

/**
 * The data-encryption key for one tenant's facts in one calendar month (#200).
 *
 * Stored only wrapped by a key-encryption key that lives in Vault Transit (or, for development,
 * in memory). Shredding deletes [wrappedKey]: every payload encrypted under it becomes
 * unreadable, while the ledger, which only ever held ciphertext, still verifies.
 */
@Entity
@Table(name = "data_keys", uniqueConstraints = [UniqueConstraint(columnNames = ["tenant", "period"])])
class DataKey(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant", nullable = false, length = 128)
    val tenant: String,

    /** `YYYY-MM`, UTC. */
    @Column(name = "period", nullable = false, length = 7)
    val period: String,

    @Column(name = "wrapped_key", columnDefinition = "TEXT")
    var wrappedKey: String?,

    @Column(name = "kek_id", nullable = false, length = 128)
    val kekId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: DataKeyStatus = DataKeyStatus.ACTIVE,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "shredded_at")
    var shreddedAt: Instant? = null,

    @Column(name = "shredded_by")
    var shreddedBy: String? = null
)

enum class ShredRequestStatus { PENDING_APPROVAL, APPROVED }

/** A request to shred a data key, which a second administrator must approve (#200). */
@Entity
@Table(name = "data_key_shred_requests")
class ShredRequest(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant", nullable = false, length = 128)
    val tenant: String,

    @Column(name = "period", nullable = false, length = 7)
    val period: String,

    @Column(name = "requested_by", nullable = false)
    val requestedBy: String,

    @Column(name = "requested_at", nullable = false)
    val requestedAt: Instant = Instant.now(),

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    var status: ShredRequestStatus = ShredRequestStatus.PENDING_APPROVAL,

    @Column(name = "approved_by")
    var approvedBy: String? = null,

    @Column(name = "approved_at")
    var approvedAt: Instant? = null
)
