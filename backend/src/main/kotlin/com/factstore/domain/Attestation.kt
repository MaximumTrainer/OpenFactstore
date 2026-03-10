package com.factstore.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class AttestationStatus { PASSED, FAILED, PENDING }

@Entity
@Table(name = "attestations")
class Attestation(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "trail_id", nullable = false)
    var trailId: UUID,

    @Column(nullable = false)
    var type: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AttestationStatus = AttestationStatus.PENDING,

    @Column(name = "evidence_file_hash")
    var evidenceFileHash: String? = null,

    @Column(name = "evidence_file_name")
    var evidenceFileName: String? = null,

    @Column(name = "evidence_file_size_bytes")
    var evidenceFileSizeBytes: Long? = null,

    @Column(columnDefinition = "TEXT")
    var details: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
