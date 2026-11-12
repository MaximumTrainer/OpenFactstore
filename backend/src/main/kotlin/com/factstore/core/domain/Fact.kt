package com.factstore.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Convert
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

    /** Who sent it, as verified at ingestion (#191). */
    @Convert(converter = FactIdentityConverter::class)
    @Column(name = "identity_json", nullable = false, columnDefinition = "TEXT")
    val identity: FactIdentity = FactIdentity.credential("unrecorded"),

    /** The registry key that signed it at the source (#187). Null only for facts from before #187. */
    @Column(name = "signature_key_id", length = 128)
    val signatureKeyId: String? = null,

    /** The source's Ed25519 signature over [signedBody], base64url. */
    @Column(name = "signature", columnDefinition = "TEXT")
    val signature: String? = null,

    /** The request body exactly as signed, so the signature can be checked again later. */
    @Column(name = "signed_body", columnDefinition = "TEXT")
    val signedBody: String? = null,

    /** SHA-256 of the signed request body, kept even when masking means the body itself is not (#198). */
    @Column(name = "signed_body_sha256", length = 64)
    val signedBodySha256: String? = null,

    /** The masking ruleset the payload was checked against (#198). */
    @Column(name = "masking_ruleset", length = 64)
    val maskingRuleset: String? = null,

    /** Paths Factstore masked on arrival, in redact mode (#198). Empty when nothing was masked. */
    @Convert(converter = StringListConverter::class)
    @Column(name = "masked_paths", columnDefinition = "TEXT")
    val maskedPaths: List<String> = emptyList(),

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
