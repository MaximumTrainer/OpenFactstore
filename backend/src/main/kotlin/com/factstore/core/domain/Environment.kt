package com.factstore.core.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class EnvironmentType { K8S, ECS, LAMBDA, S3, DOCKER, GENERIC }

enum class DriftPolicy { WARN, BLOCK, IGNORE }

@Entity
@Table(name = "environments")
class Environment(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(unique = true, nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: EnvironmentType,

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String = "",

    @Column(name = "org_slug", nullable = true, length = 255)
    var orgSlug: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Enumerated(EnumType.STRING)
    @Column(name = "drift_policy", nullable = false)
    var driftPolicy: DriftPolicy = DriftPolicy.WARN,

    @Column(name = "scope_include_names", columnDefinition = "TEXT")
    var scopeIncludeNames: String? = null,

    @Column(name = "scope_include_patterns", columnDefinition = "TEXT")
    var scopeIncludePatterns: String? = null,

    @Column(name = "scope_exclude_names", columnDefinition = "TEXT")
    var scopeExcludeNames: String? = null,

    @Column(name = "scope_exclude_patterns", columnDefinition = "TEXT")
    var scopeExcludePatterns: String? = null
) {
    val parsedIncludeNames: List<String>
        get() = scopeIncludeNames?.split("||")?.filter { it.isNotBlank() } ?: emptyList()

    val parsedIncludePatterns: List<String>
        get() = scopeIncludePatterns?.split("||")?.filter { it.isNotBlank() } ?: emptyList()

    val parsedExcludeNames: List<String>
        get() = scopeExcludeNames?.split("||")?.filter { it.isNotBlank() } ?: emptyList()

    val parsedExcludePatterns: List<String>
        get() = scopeExcludePatterns?.split("||")?.filter { it.isNotBlank() } ?: emptyList()
}
