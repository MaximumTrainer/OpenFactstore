package com.factstore.core.domain

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

    @Column(nullable = true)
    var name: String? = null,

    @Column(name = "evidence_url", nullable = true)
    var evidenceUrl: String? = null,

    @Column(name = "org_slug", nullable = true, length = 255)
    var orgSlug: String? = null,

    @Column(name = "artifact_fingerprint", nullable = true, length = 255)
    var artifactFingerprint: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    // Issue #126: structured JSON payload
    @Column(name = "attestation_data", columnDefinition = "TEXT")
    var attestationData: String? = null,

    // Issue #127: external URLs (pipe-separated)
    @Column(name = "external_urls", columnDefinition = "TEXT")
    var externalUrlsRaw: String? = null,

    // Issue #129: git commit info
    @Column(name = "git_commit_sha")
    var gitCommitSha: String? = null,

    @Column(name = "git_branch")
    var gitBranch: String? = null,

    @Column(name = "git_repo_url", columnDefinition = "TEXT")
    var gitRepoUrl: String? = null,

    // Issue #124: artifact-level attestation
    @Column(name = "artifact_id", nullable = true)
    var artifactId: UUID? = null,

    // Issue #125: attestation override with justification
    @Column(name = "overrides_attestation_id", nullable = true)
    var overridesAttestationId: UUID? = null,

    @Column(name = "justification", columnDefinition = "TEXT")
    var justification: String? = null,

    /**
     * #240: the status is the caller's claim, which the server had no evaluator to check.
     * Shown in assert and gate output; a flow can refuse to count it.
     */
    @Column(name = "self_asserted", nullable = false)
    var selfAsserted: Boolean = false
) {
    // Issue #127: computed property for external URLs list
    var externalUrls: List<String>
        get() = externalUrlsRaw?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
        set(value) { externalUrlsRaw = value.joinToString("|") }

    // Issue #128: key-value annotations
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "attestation_annotations", joinColumns = [JoinColumn(name = "attestation_id")])
    @MapKeyColumn(name = "annotation_key")
    @Column(name = "annotation_value")
    var annotations: MutableMap<String, String> = mutableMapOf()
}
