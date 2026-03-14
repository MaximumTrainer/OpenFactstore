package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class BuilderType { GITHUB_ACTIONS, JENKINS, CIRCLE_CI, GENERIC }

enum class SlsaLevel { L0, L1, L2, L3 }

enum class ProvenanceStatus { NO_PROVENANCE, PROVENANCE_UNVERIFIED, PROVENANCE_VERIFIED }

@Entity
@Table(name = "build_provenances")
class BuildProvenance(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "artifact_id", nullable = false, unique = true)
    val artifactId: UUID,

    @Column(name = "builder_id", nullable = false)
    var builderId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "builder_type", nullable = false)
    var builderType: BuilderType,

    @Column(name = "build_config_uri")
    var buildConfigUri: String? = null,

    @Column(name = "source_repository_uri")
    var sourceRepositoryUri: String? = null,

    @Column(name = "source_commit_sha")
    var sourceCommitSha: String? = null,

    @Column(name = "build_started_on")
    var buildStartedOn: Instant? = null,

    @Column(name = "build_finished_on")
    var buildFinishedOn: Instant? = null,

    @Column(name = "provenance_signature", length = 2048)
    var provenanceSignature: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "slsa_level", nullable = false)
    var slsaLevel: SlsaLevel = SlsaLevel.L0,

    @Column(name = "recorded_at", nullable = false)
    val recordedAt: Instant = Instant.now()
)
