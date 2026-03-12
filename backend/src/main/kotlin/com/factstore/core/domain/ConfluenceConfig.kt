package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "confluence_configs")
class ConfluenceConfig(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "confluence_base_url", nullable = false)
    var confluenceBaseUrl: String,

    @Column(name = "confluence_username", nullable = false)
    var confluenceUsername: String,

    @Column(name = "confluence_api_token", nullable = false)
    // Note: token is stored only in-memory (H2). For production use, integrate with a secrets
    // manager such as HashiCorp Vault and replace this column with a Vault secret reference.
    var confluenceApiToken: String,

    @Column(name = "default_space_key", nullable = false)
    var defaultSpaceKey: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
