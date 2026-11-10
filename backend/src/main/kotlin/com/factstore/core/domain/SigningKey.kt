package com.factstore.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class SigningKeyStatus { ACTIVE, REVOKED }

/**
 * A CI runner's public signing key in the trusted registry (#187). Facts must be signed by an
 * ACTIVE key. Keys are never deleted, so the signature on any stored fact can still be checked.
 */
@Entity
@Table(name = "workload_signing_keys")
class SigningKey(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "key_id", nullable = false, unique = true, length = 128)
    val keyId: String,

    /** Ed25519 public key, X.509 SubjectPublicKeyInfo, base64. */
    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    val publicKey: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: SigningKeyStatus = SigningKeyStatus.ACTIVE,

    @Column(name = "created_by", nullable = false)
    val createdBy: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
)
