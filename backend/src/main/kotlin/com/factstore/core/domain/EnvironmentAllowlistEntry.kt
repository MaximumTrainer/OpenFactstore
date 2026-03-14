package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class AllowlistEntryStatus { ACTIVE, REMOVED }

@Entity
@Table(name = "environment_allowlist_entries")
class EnvironmentAllowlistEntry(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "environment_id", nullable = false) val environmentId: UUID,
    @Column(name = "sha256") val sha256: String? = null,
    @Column(name = "name_pattern") val namePattern: String? = null,
    @Column(nullable = false, columnDefinition = "TEXT") val reason: String,
    @Column(name = "approved_by", nullable = false) val approvedBy: String,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now(),
    @Column(name = "expires_at") val expiresAt: Instant? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: AllowlistEntryStatus = AllowlistEntryStatus.ACTIVE
) {
    fun isEffective(now: Instant = Instant.now()): Boolean =
        status == AllowlistEntryStatus.ACTIVE && (expiresAt == null || expiresAt.isAfter(now))

    fun matches(sha256Digest: String? = null, artifactName: String? = null): Boolean {
        if (!isEffective()) return false
        if (sha256 != null && sha256Digest != null && sha256 == sha256Digest) return true
        if (namePattern != null && artifactName != null && Regex(namePattern).containsMatchIn(artifactName)) return true
        return false
    }
}
