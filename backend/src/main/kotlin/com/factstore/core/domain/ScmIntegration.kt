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

    @Column(name = "token_encrypted", nullable = false, columnDefinition = "TEXT")
    var encryptedToken: String,

    @Column(name = "is_token_encrypted", nullable = false)
    var isTokenEncrypted: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
