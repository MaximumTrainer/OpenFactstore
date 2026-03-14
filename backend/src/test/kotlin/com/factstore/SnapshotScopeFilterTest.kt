package com.factstore

import com.factstore.application.ScopeFilter
import com.factstore.core.domain.EnvironmentType
import com.factstore.core.domain.SnapshotScope
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SnapshotScopeFilterTest {

    @Test
    fun `empty scope includes all artifacts`() {
        val scope = SnapshotScope()
        assertTrue(ScopeFilter.matches("production", scope, EnvironmentType.K8S))
        assertTrue(ScopeFilter.matches("internal-service", scope, EnvironmentType.K8S))
        assertTrue(ScopeFilter.matches("anything", scope, EnvironmentType.K8S))
    }

    @Test
    fun `include by name filters to matching artifacts only`() {
        val scope = SnapshotScope(includeNames = listOf("production", "staging"))
        assertTrue(ScopeFilter.matches("production", scope, EnvironmentType.K8S))
        assertTrue(ScopeFilter.matches("staging", scope, EnvironmentType.K8S))
        assertFalse(ScopeFilter.matches("development", scope, EnvironmentType.K8S))
    }

    @Test
    fun `include by regex pattern matches on K8S`() {
        val scope = SnapshotScope(includePatterns = listOf("^prod-.*"))
        assertTrue(ScopeFilter.matches("prod-api", scope, EnvironmentType.K8S))
        assertTrue(ScopeFilter.matches("prod-db", scope, EnvironmentType.K8S))
        assertFalse(ScopeFilter.matches("staging-api", scope, EnvironmentType.K8S))
    }

    @Test
    fun `exclude by name removes matching artifacts`() {
        val scope = SnapshotScope(excludeNames = listOf("internal-svc"))
        assertTrue(ScopeFilter.matches("production", scope, EnvironmentType.K8S))
        assertFalse(ScopeFilter.matches("internal-svc", scope, EnvironmentType.K8S))
    }

    @Test
    fun `exclude by regex removes matching artifacts on K8S`() {
        val scope = SnapshotScope(excludePatterns = listOf("^internal-.*"))
        assertTrue(ScopeFilter.matches("production", scope, EnvironmentType.K8S))
        assertFalse(ScopeFilter.matches("internal-api", scope, EnvironmentType.K8S))
        assertFalse(ScopeFilter.matches("internal-db", scope, EnvironmentType.K8S))
    }

    @Test
    fun `include names take precedence and exclusion still applies`() {
        val scope = SnapshotScope(
            includeNames = listOf("production", "staging", "internal-svc"),
            excludeNames = listOf("internal-svc")
        )
        assertTrue(ScopeFilter.matches("production", scope, EnvironmentType.K8S))
        assertTrue(ScopeFilter.matches("staging", scope, EnvironmentType.K8S))
        assertFalse(ScopeFilter.matches("internal-svc", scope, EnvironmentType.K8S))
        assertFalse(ScopeFilter.matches("other", scope, EnvironmentType.K8S))
    }

    @Test
    fun `Docker ignores include patterns but respects include names`() {
        val scope = SnapshotScope(
            includePatterns = listOf("^prod-.*"),
            includeNames = listOf("my-app")
        )
        // Pattern is ignored for DOCKER, only name matches
        assertTrue(ScopeFilter.matches("my-app", scope, EnvironmentType.DOCKER))
        assertFalse(ScopeFilter.matches("prod-api", scope, EnvironmentType.DOCKER))
    }

    @Test
    fun `Docker ignores exclude patterns but respects exclude names`() {
        val scope = SnapshotScope(excludePatterns = listOf("^internal-.*"), excludeNames = listOf("bad-svc"))
        // Pattern is ignored, so internal-api should be included
        assertTrue(ScopeFilter.matches("internal-api", scope, EnvironmentType.DOCKER))
        // Name exclusion still applies
        assertFalse(ScopeFilter.matches("bad-svc", scope, EnvironmentType.DOCKER))
    }

    @Test
    fun `S3 scope filter does not apply - all artifacts pass through`() {
        val scope = SnapshotScope(
            includeNames = listOf("specific-bucket"),
            excludeNames = listOf("everything")
        )
        assertTrue(ScopeFilter.matches("specific-bucket", scope, EnvironmentType.S3))
        assertTrue(ScopeFilter.matches("everything", scope, EnvironmentType.S3))
        assertTrue(ScopeFilter.matches("any-other-bucket", scope, EnvironmentType.S3))
    }

    @Test
    fun `GENERIC scope filter does not apply - all artifacts pass through`() {
        val scope = SnapshotScope(includeNames = listOf("only-this"))
        assertTrue(ScopeFilter.matches("only-this", scope, EnvironmentType.GENERIC))
        assertTrue(ScopeFilter.matches("something-else", scope, EnvironmentType.GENERIC))
    }

    @Test
    fun `applies returns false for S3 and GENERIC`() {
        assertFalse(ScopeFilter.applies(EnvironmentType.S3))
        assertFalse(ScopeFilter.applies(EnvironmentType.GENERIC))
    }

    @Test
    fun `applies returns true for K8S, ECS, LAMBDA, DOCKER`() {
        assertTrue(ScopeFilter.applies(EnvironmentType.K8S))
        assertTrue(ScopeFilter.applies(EnvironmentType.ECS))
        assertTrue(ScopeFilter.applies(EnvironmentType.LAMBDA))
        assertTrue(ScopeFilter.applies(EnvironmentType.DOCKER))
    }

    @Test
    fun `ECS supports both name and regex include and exclude`() {
        val scope = SnapshotScope(
            includePatterns = listOf("^svc-.*"),
            excludeNames = listOf("svc-deprecated")
        )
        assertTrue(ScopeFilter.matches("svc-api", scope, EnvironmentType.ECS))
        assertFalse(ScopeFilter.matches("svc-deprecated", scope, EnvironmentType.ECS))
        assertFalse(ScopeFilter.matches("other-task", scope, EnvironmentType.ECS))
    }

    @Test
    fun `LAMBDA supports both name and regex include and exclude`() {
        val scope = SnapshotScope(
            includeNames = listOf("my-lambda"),
            excludePatterns = listOf(".*-test$")
        )
        assertTrue(ScopeFilter.matches("my-lambda", scope, EnvironmentType.LAMBDA))
        assertFalse(ScopeFilter.matches("my-lambda-test", scope, EnvironmentType.LAMBDA))
        assertFalse(ScopeFilter.matches("other-lambda", scope, EnvironmentType.LAMBDA))
    }
}
