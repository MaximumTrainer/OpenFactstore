package com.factstore.core.domain

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

    /**
     * Inline binary content. Null when the evidence is stored at an external location
     * referenced by [externalUrl].
     */
    @Column(name = "content", nullable = true, columnDefinition = "BLOB")
    val content: ByteArray? = null,

    /**
     * URL pointer to an externally hosted evidence file (e.g., S3 bucket, private server).
     * Allows security-conscious customers to keep heavy artefacts outside the fact-store
     * while still recording their metadata and hash here.
     * Exactly one of [content] and [externalUrl] must be non-null.
     */
    @Column(name = "external_url", nullable = true, columnDefinition = "TEXT")
    val externalUrl: String? = null
)
