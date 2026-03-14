package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class ScmProvider { GITHUB, GITLAB, BITBUCKET, AZURE_DEVOPS }

@Entity
@Table(
    name = "scm_integrations",
    uniqueConstraints = [UniqueConstraint(columnNames = ["org_slug", "provider"])]
)
class ScmIntegration(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "org_slug", nullable = false)
    val orgSlug: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: ScmProvider,

    // NOTE: In production this should be encrypted via a KMS (e.g. AWS KMS, HashiCorp Vault).
    // Here we use Base64 encoding as a minimal at-rest encoding for this implementation.
    @Column(name = "token_encrypted", nullable = false, columnDefinition = "TEXT")
    var tokenEncrypted: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
