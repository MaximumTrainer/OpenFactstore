package com.factstore.application

import com.factstore.core.domain.EnvironmentType
import com.factstore.core.domain.SnapshotScope

object ScopeFilter {

    private val PATTERN_UNSUPPORTED = setOf(EnvironmentType.DOCKER)
    private val SCOPE_UNSUPPORTED = setOf(EnvironmentType.S3, EnvironmentType.GENERIC)

    fun applies(environmentType: EnvironmentType): Boolean =
        environmentType !in SCOPE_UNSUPPORTED

    fun matches(name: String, scope: SnapshotScope, environmentType: EnvironmentType): Boolean {
        if (!applies(environmentType)) return true

        val supportsPatterns = environmentType !in PATTERN_UNSUPPORTED
        val effectiveIncludePatterns = if (supportsPatterns) scope.includePatterns else emptyList()
        val effectiveExcludePatterns = if (supportsPatterns) scope.excludePatterns else emptyList()

        val hasIncludes = scope.includeNames.isNotEmpty() || effectiveIncludePatterns.isNotEmpty()
        val included = if (!hasIncludes) {
            true
        } else {
            name in scope.includeNames || effectiveIncludePatterns.any { name.matches(it.toRegex()) }
        }

        if (!included) return false

        val excluded = name in scope.excludeNames ||
            effectiveExcludePatterns.any { name.matches(it.toRegex()) }

        return !excluded
    }
}
