package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class ApiKeyType { PERSONAL, SERVICE }

@Entity
@Table(name = "api_keys")
class ApiKey(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: ApiKeyType,

    @Column(nullable = false)
    var name: String,

    /**
     * Stores the first 12 characters of the generated key (including type prefix).
     * Used for efficient database lookup before BCrypt verification.
     * Example: "fsp_abcde12" (personal) or "fss_abcde12" (service)
     */
    @Column(name = "key_prefix", nullable = false, length = 16)
    val keyPrefix: String,

    /**
     * BCrypt hash of the full API key. Never store the plain-text key.
     */
    @Column(name = "hashed_key", nullable = false)
    var hashedKey: String,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null
)
