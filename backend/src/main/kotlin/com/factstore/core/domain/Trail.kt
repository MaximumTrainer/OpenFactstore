package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class TrailStatus { PENDING, COMPLIANT, NON_COMPLIANT }

@Entity
@Table(name = "trails")
class Trail(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "flow_id", nullable = false)
    var flowId: UUID,

    @Column(name = "git_commit_sha", nullable = false)
    var gitCommitSha: String,

    @Column(name = "git_branch", nullable = false)
    var gitBranch: String,

    @Column(name = "git_author", nullable = false)
    var gitAuthor: String,

    @Column(name = "git_author_email", nullable = false)
    var gitAuthorEmail: String,

    @Column(name = "pull_request_id")
    var pullRequestId: String? = null,

    @Column(name = "pull_request_reviewer")
    var pullRequestReviewer: String? = null,

    @Column(name = "deployment_actor")
    var deploymentActor: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TrailStatus = TrailStatus.PENDING,

    @Column(name = "org_slug", nullable = true, length = 255)
    var orgSlug: String? = null,

    @Column(name = "template_yaml", columnDefinition = "TEXT")
    var templateYaml: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
