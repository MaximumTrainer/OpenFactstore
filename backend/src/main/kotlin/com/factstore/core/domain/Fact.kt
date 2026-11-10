package com.factstore.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A fact accepted at ingress (#196): its envelope was well formed and its `tool` payload
 * conformed strictly to the schema named in [schemaRef] when it arrived.
 */
@Entity
@Table(name = "facts")
class Fact(
    @Id
    @Column(name = "fact_id")
    val factId: UUID,

    @Column(name = "schema_ref", nullable = false, length = 160)
    val schemaRef: String,

    @Column(name = "subject_sha256", nullable = false, length = 64)
    val subjectSha256: String,

    @Column(name = "subject_repository")
    val subjectRepository: String? = null,

    /** The `tool` payload, exactly as validated. */
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    val payloadJson: String,

    @Column(name = "ingested_by", nullable = false)
    val ingestedBy: String,

    @Column(name = "ingested_at", nullable = false)
    val ingestedAt: Instant = Instant.now()
)

/** A subject digest: exactly 64 hexadecimal characters (#196). */
@JvmInline
value class Sha256Hash(val value: String) {
    init {
        require(PATTERN.matches(value)) { "must be 64 hexadecimal characters" }
    }

    companion object {
        private val PATTERN = Regex("^[a-fA-F0-9]{64}$")
    }
}

/** A SPIFFE ID naming a workload, `spiffe://<trust-domain>/<path>` (#189). */
@JvmInline
value class SpiffeId(val value: String) {
    init {
        require(value.startsWith(SCHEME) && value.length > SCHEME.length) { "must be a spiffe:// URI" }
    }

    companion object {
        const val SCHEME = "spiffe://"
    }
}
