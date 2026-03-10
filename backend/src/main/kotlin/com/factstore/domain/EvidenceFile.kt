package com.factstore.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "evidence_files")
class EvidenceFile(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "attestation_id", nullable = false)
    val attestationId: UUID,

    @Column(name = "file_name", nullable = false)
    val fileName: String,

    @Column(name = "sha256_hash", nullable = false)
    val sha256Hash: String,

    @Column(name = "file_size_bytes", nullable = false)
    val fileSizeBytes: Long,

    @Column(name = "content_type", nullable = false)
    val contentType: String,

    @Column(name = "stored_at", nullable = false)
    val storedAt: Instant = Instant.now(),

    @Column(name = "content", nullable = false, columnDefinition = "BLOB")
    val content: ByteArray
)
