package com.factstore

import com.factstore.application.CiContextResolver
import com.factstore.application.CiProvider
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.command.CreateTrailCommand
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class CiContextResolverTest {

    private val flowId = UUID.randomUUID()

    private fun baseRequest() = CreateTrailRequest(
        flowId = flowId,
        gitAuthor = "dev",
        gitAuthorEmail = "dev@example.com"
    )

    private fun baseCommand() = CreateTrailCommand(
        flowId = flowId,
        gitAuthor = "dev",
        gitAuthorEmail = "dev@example.com"
    )

    // ── resolve() tests ──────────────────────────────────────────────────────

    @Test
    fun `resolve returns GITHUB_ACTIONS for github-actions`() {
        assertEquals(CiProvider.GITHUB_ACTIONS, CiContextResolver.resolve("github-actions"))
    }

    @Test
    fun `resolve returns GITHUB_ACTIONS for github_actions`() {
        assertEquals(CiProvider.GITHUB_ACTIONS, CiContextResolver.resolve("github_actions"))
    }

    @Test
    fun `resolve returns GITLAB_CI for gitlab-ci`() {
        assertEquals(CiProvider.GITLAB_CI, CiContextResolver.resolve("gitlab-ci"))
    }

    @Test
    fun `resolve returns GITLAB_CI for gitlab_ci`() {
        assertEquals(CiProvider.GITLAB_CI, CiContextResolver.resolve("gitlab_ci"))
    }

    @Test
    fun `resolve returns JENKINS for jenkins`() {
        assertEquals(CiProvider.JENKINS, CiContextResolver.resolve("jenkins"))
    }

    @Test
    fun `resolve returns CIRCLECI for circleci`() {
        assertEquals(CiProvider.CIRCLECI, CiContextResolver.resolve("circleci"))
    }

    @Test
    fun `resolve returns CIRCLECI for circle-ci`() {
        assertEquals(CiProvider.CIRCLECI, CiContextResolver.resolve("circle-ci"))
    }

    @Test
    fun `resolve returns AZURE_DEVOPS for azure-devops`() {
        assertEquals(CiProvider.AZURE_DEVOPS, CiContextResolver.resolve("azure-devops"))
    }

    @Test
    fun `resolve returns AZURE_DEVOPS for azure_devops`() {
        assertEquals(CiProvider.AZURE_DEVOPS, CiContextResolver.resolve("azure_devops"))
    }

    @Test
    fun `resolve returns null for unknown header`() {
        assertNull(CiContextResolver.resolve("unknown-ci"))
    }

    @Test
    fun `resolve returns null for null header`() {
        assertNull(CiContextResolver.resolve(null))
    }

    @Test
    fun `resolve is case-insensitive`() {
        assertEquals(CiProvider.GITHUB_ACTIONS, CiContextResolver.resolve("GitHub-Actions"))
        assertEquals(CiProvider.JENKINS, CiContextResolver.resolve("JENKINS"))
    }

    // ── getCommitSha() tests ─────────────────────────────────────────────────

    @Test
    fun `getCommitSha returns GITHUB_SHA for GITHUB_ACTIONS`() {
        val env = mapOf("GITHUB_SHA" to "abc123")
        assertEquals("abc123", CiContextResolver.getCommitSha(CiProvider.GITHUB_ACTIONS, env))
    }

    @Test
    fun `getCommitSha returns CI_COMMIT_SHA for GITLAB_CI`() {
        val env = mapOf("CI_COMMIT_SHA" to "def456")
        assertEquals("def456", CiContextResolver.getCommitSha(CiProvider.GITLAB_CI, env))
    }

    @Test
    fun `getCommitSha returns GIT_COMMIT for JENKINS`() {
        val env = mapOf("GIT_COMMIT" to "ghi789")
        assertEquals("ghi789", CiContextResolver.getCommitSha(CiProvider.JENKINS, env))
    }

    @Test
    fun `getCommitSha returns CIRCLE_SHA1 for CIRCLECI`() {
        val env = mapOf("CIRCLE_SHA1" to "jkl012")
        assertEquals("jkl012", CiContextResolver.getCommitSha(CiProvider.CIRCLECI, env))
    }

    @Test
    fun `getCommitSha returns BUILD_SOURCEVERSION for AZURE_DEVOPS`() {
        val env = mapOf("BUILD_SOURCEVERSION" to "mno345")
        assertEquals("mno345", CiContextResolver.getCommitSha(CiProvider.AZURE_DEVOPS, env))
    }

    // ── getBranch() tests ────────────────────────────────────────────────────

    @Test
    fun `getBranch returns GITHUB_REF_NAME for GITHUB_ACTIONS`() {
        val env = mapOf("GITHUB_REF_NAME" to "main")
        assertEquals("main", CiContextResolver.getBranch(CiProvider.GITHUB_ACTIONS, env))
    }

    @Test
    fun `getBranch returns CI_COMMIT_REF_NAME for GITLAB_CI`() {
        val env = mapOf("CI_COMMIT_REF_NAME" to "feature/x")
        assertEquals("feature/x", CiContextResolver.getBranch(CiProvider.GITLAB_CI, env))
    }

    @Test
    fun `getBranch returns GIT_BRANCH for JENKINS`() {
        val env = mapOf("GIT_BRANCH" to "release/1.0")
        assertEquals("release/1.0", CiContextResolver.getBranch(CiProvider.JENKINS, env))
    }

    @Test
    fun `getBranch returns CIRCLE_BRANCH for CIRCLECI`() {
        val env = mapOf("CIRCLE_BRANCH" to "develop")
        assertEquals("develop", CiContextResolver.getBranch(CiProvider.CIRCLECI, env))
    }

    @Test
    fun `getBranch returns BUILD_SOURCEBRANCH for AZURE_DEVOPS`() {
        val env = mapOf("BUILD_SOURCEBRANCH" to "refs/heads/main")
        assertEquals("refs/heads/main", CiContextResolver.getBranch(CiProvider.AZURE_DEVOPS, env))
    }

    // ── getBuildUrl() tests ──────────────────────────────────────────────────

    @Test
    fun `getBuildUrl constructs URL for GITHUB_ACTIONS`() {
        val env = mapOf(
            "GITHUB_SERVER_URL" to "https://github.com",
            "GITHUB_REPOSITORY" to "acme/myapp",
            "GITHUB_RUN_ID" to "9876543"
        )
        assertEquals(
            "https://github.com/acme/myapp/actions/runs/9876543",
            CiContextResolver.getBuildUrl(CiProvider.GITHUB_ACTIONS, env)
        )
    }

    @Test
    fun `getBuildUrl returns null for GITHUB_ACTIONS when env vars missing`() {
        assertNull(CiContextResolver.getBuildUrl(CiProvider.GITHUB_ACTIONS, emptyMap()))
    }

    @Test
    fun `getBuildUrl returns CI_JOB_URL for GITLAB_CI`() {
        val env = mapOf("CI_JOB_URL" to "https://gitlab.com/acme/myapp/-/jobs/42")
        assertEquals("https://gitlab.com/acme/myapp/-/jobs/42", CiContextResolver.getBuildUrl(CiProvider.GITLAB_CI, env))
    }

    @Test
    fun `getBuildUrl returns BUILD_URL for JENKINS`() {
        val env = mapOf("BUILD_URL" to "https://jenkins.example.com/job/myapp/5/")
        assertEquals("https://jenkins.example.com/job/myapp/5/", CiContextResolver.getBuildUrl(CiProvider.JENKINS, env))
    }

    @Test
    fun `getBuildUrl returns CIRCLE_BUILD_URL for CIRCLECI`() {
        val env = mapOf("CIRCLE_BUILD_URL" to "https://circleci.com/gh/acme/myapp/10")
        assertEquals("https://circleci.com/gh/acme/myapp/10", CiContextResolver.getBuildUrl(CiProvider.CIRCLECI, env))
    }

    @Test
    fun `getBuildUrl constructs URL for AZURE_DEVOPS`() {
        val env = mapOf(
            "SYSTEM_TEAMFOUNDATIONCOLLECTIONURI" to "https://dev.azure.com/acme/",
            "SYSTEM_TEAMPROJECT" to "myproject",
            "BUILD_BUILDID" to "100"
        )
        assertEquals(
            "https://dev.azure.com/acme/myproject/_build/results?buildId=100",
            CiContextResolver.getBuildUrl(CiProvider.AZURE_DEVOPS, env)
        )
    }

    // ── enrich() tests ───────────────────────────────────────────────────────

    @Test
    fun `enrich fills blank gitCommitSha from env`() {
        val env = mapOf("GITHUB_SHA" to "filled-sha", "GITHUB_REF_NAME" to "main")
        val result = CiContextResolver.enrich(baseRequest(), CiProvider.GITHUB_ACTIONS, env)
        assertEquals("filled-sha", result.gitCommitSha)
    }

    @Test
    fun `enrich fills blank gitBranch from env`() {
        val env = mapOf("GITHUB_REF_NAME" to "feature/ci")
        val result = CiContextResolver.enrich(baseRequest(), CiProvider.GITHUB_ACTIONS, env)
        assertEquals("feature/ci", result.gitBranch)
    }

    @Test
    fun `enrich fills blank buildUrl for GITHUB_ACTIONS`() {
        val env = mapOf(
            "GITHUB_SERVER_URL" to "https://github.com",
            "GITHUB_REPOSITORY" to "acme/app",
            "GITHUB_RUN_ID" to "1"
        )
        val result = CiContextResolver.enrich(baseRequest(), CiProvider.GITHUB_ACTIONS, env)
        assertEquals("https://github.com/acme/app/actions/runs/1", result.buildUrl)
    }

    @Test
    fun `enrich does NOT override already-set gitCommitSha`() {
        val request = baseRequest().copy(gitCommitSha = "client-provided-sha")
        val env = mapOf("GITHUB_SHA" to "env-sha")
        val result = CiContextResolver.enrich(request, CiProvider.GITHUB_ACTIONS, env)
        assertEquals("client-provided-sha", result.gitCommitSha)
    }

    @Test
    fun `enrich does NOT override already-set gitBranch`() {
        val request = baseRequest().copy(gitBranch = "client-branch")
        val env = mapOf("GITHUB_REF_NAME" to "env-branch")
        val result = CiContextResolver.enrich(request, CiProvider.GITHUB_ACTIONS, env)
        assertEquals("client-branch", result.gitBranch)
    }

    @Test
    fun `enrich does NOT override already-set buildUrl`() {
        val request = baseRequest().copy(buildUrl = "https://my-ci.example.com/build/42")
        val env = mapOf(
            "GITHUB_SERVER_URL" to "https://github.com",
            "GITHUB_REPOSITORY" to "acme/app",
            "GITHUB_RUN_ID" to "99"
        )
        val result = CiContextResolver.enrich(request, CiProvider.GITHUB_ACTIONS, env)
        assertEquals("https://my-ci.example.com/build/42", result.buildUrl)
    }

    @Test
    fun `enrich leaves fields null when env vars not present`() {
        val result = CiContextResolver.enrich(baseRequest(), CiProvider.GITLAB_CI, emptyMap())
        assertNull(result.gitCommitSha)
        assertNull(result.gitBranch)
        assertNull(result.buildUrl)
    }

    @Test
    fun `getBuildUrl returns null for GITHUB_ACTIONS when only GITHUB_REPOSITORY is missing`() {
        val env = mapOf(
            "GITHUB_SERVER_URL" to "https://github.com",
            "GITHUB_RUN_ID" to "9876543"
        )
        assertNull(CiContextResolver.getBuildUrl(CiProvider.GITHUB_ACTIONS, env))
    }

    @Test
    fun `getBuildUrl returns null for GITHUB_ACTIONS when only GITHUB_RUN_ID is missing`() {
        val env = mapOf(
            "GITHUB_SERVER_URL" to "https://github.com",
            "GITHUB_REPOSITORY" to "acme/myapp"
        )
        assertNull(CiContextResolver.getBuildUrl(CiProvider.GITHUB_ACTIONS, env))
    }

    @Test
    fun `getBuildUrl returns null for AZURE_DEVOPS when only SYSTEM_TEAMPROJECT is missing`() {
        val env = mapOf(
            "SYSTEM_TEAMFOUNDATIONCOLLECTIONURI" to "https://dev.azure.com/acme/",
            "BUILD_BUILDID" to "100"
        )
        assertNull(CiContextResolver.getBuildUrl(CiProvider.AZURE_DEVOPS, env))
    }

    @Test
    fun `getBuildUrl returns null for AZURE_DEVOPS when only BUILD_BUILDID is missing`() {
        val env = mapOf(
            "SYSTEM_TEAMFOUNDATIONCOLLECTIONURI" to "https://dev.azure.com/acme/",
            "SYSTEM_TEAMPROJECT" to "myproject"
        )
        assertNull(CiContextResolver.getBuildUrl(CiProvider.AZURE_DEVOPS, env))
    }

    @Test
    fun `getBuildUrl returns null for AZURE_DEVOPS when all env vars missing`() {
        assertNull(CiContextResolver.getBuildUrl(CiProvider.AZURE_DEVOPS, emptyMap()))
    }

    // ── enrich(CreateTrailCommand) tests ─────────────────────────────────────

    @Test
    fun `enrich command fills blank gitCommitSha from env`() {
        val env = mapOf("GITHUB_SHA" to "filled-sha", "GITHUB_REF_NAME" to "main")
        val result = CiContextResolver.enrich(baseCommand(), CiProvider.GITHUB_ACTIONS, env)
        assertEquals("filled-sha", result.gitCommitSha)
    }

    @Test
    fun `enrich command fills blank gitBranch from env`() {
        val env = mapOf("GITHUB_REF_NAME" to "feature/ci")
        val result = CiContextResolver.enrich(baseCommand(), CiProvider.GITHUB_ACTIONS, env)
        assertEquals("feature/ci", result.gitBranch)
    }

    @Test
    fun `enrich command fills blank buildUrl for GITHUB_ACTIONS`() {
        val env = mapOf(
            "GITHUB_SERVER_URL" to "https://github.com",
            "GITHUB_REPOSITORY" to "acme/app",
            "GITHUB_RUN_ID" to "1"
        )
        val result = CiContextResolver.enrich(baseCommand(), CiProvider.GITHUB_ACTIONS, env)
        assertEquals("https://github.com/acme/app/actions/runs/1", result.buildUrl)
    }

    @Test
    fun `enrich command does NOT override already-set gitCommitSha`() {
        val command = baseCommand().copy(gitCommitSha = "client-provided-sha")
        val env = mapOf("GITHUB_SHA" to "env-sha")
        val result = CiContextResolver.enrich(command, CiProvider.GITHUB_ACTIONS, env)
        assertEquals("client-provided-sha", result.gitCommitSha)
    }

    @Test
    fun `enrich command does NOT override already-set gitBranch`() {
        val command = baseCommand().copy(gitBranch = "client-branch")
        val env = mapOf("GITHUB_REF_NAME" to "env-branch")
        val result = CiContextResolver.enrich(command, CiProvider.GITHUB_ACTIONS, env)
        assertEquals("client-branch", result.gitBranch)
    }

    @Test
    fun `enrich command does NOT override already-set buildUrl`() {
        val command = baseCommand().copy(buildUrl = "https://my-ci.example.com/build/42")
        val env = mapOf(
            "GITHUB_SERVER_URL" to "https://github.com",
            "GITHUB_REPOSITORY" to "acme/app",
            "GITHUB_RUN_ID" to "99"
        )
        val result = CiContextResolver.enrich(command, CiProvider.GITHUB_ACTIONS, env)
        assertEquals("https://my-ci.example.com/build/42", result.buildUrl)
    }

    @Test
    fun `enrich command leaves fields null when env vars not present`() {
        val result = CiContextResolver.enrich(baseCommand(), CiProvider.GITLAB_CI, emptyMap())
        assertNull(result.gitCommitSha)
        assertNull(result.gitBranch)
        assertNull(result.buildUrl)
    }
}
