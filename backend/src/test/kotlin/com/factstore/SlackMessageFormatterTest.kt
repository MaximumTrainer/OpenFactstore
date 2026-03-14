package com.factstore

import com.factstore.application.SlackMessageFormatter
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.TrailStatus
import com.factstore.dto.ArtifactResponse
import com.factstore.dto.AttestationResponse
import com.factstore.dto.FlowResponse
import com.factstore.dto.TrailResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SlackMessageFormatterTest {

    private lateinit var formatter: SlackMessageFormatter

    @BeforeEach
    fun setUp() {
        formatter = SlackMessageFormatter()
    }

    // ── formatHelp ──────────────────────────────────────────────────────────

    @Test
    fun `formatHelp contains all sub-commands`() {
        val help = formatter.formatHelp()
        assertTrue(help.contains("help"))
        assertTrue(help.contains("search"))
        assertTrue(help.contains("trail"))
        assertTrue(help.contains("env"))
        assertTrue(help.contains("approve"))
        assertTrue(help.contains("reject"))
    }

    // ── formatArtifactSearch ────────────────────────────────────────────────

    @Test
    fun `formatArtifactSearch with empty list returns no-match message`() {
        val result = formatter.formatArtifactSearch("abc123", emptyList())
        assertTrue(result.contains("abc123"))
        assertTrue(result.contains("No artifacts"))
    }

    @Test
    fun `formatArtifactSearch with artifacts lists them`() {
        val artifact = ArtifactResponse(
            id = UUID.randomUUID(),
            trailId = UUID.randomUUID(),
            imageName = "my-image",
            imageTag = "1.0.0",
            sha256Digest = "abc123def456",
            registry = null,
            reportedAt = Instant.now(),
            reportedBy = "ci-bot"
        )
        val result = formatter.formatArtifactSearch("abc123", listOf(artifact))
        assertTrue(result.contains("my-image"))
        assertTrue(result.contains("1.0.0"))
        assertTrue(result.contains("abc123def456"))
        assertTrue(result.contains("ci-bot"))
    }

    @Test
    fun `formatArtifactSearch truncates at 10 results and shows overflow count`() {
        val artifacts = (1..15).map { i ->
            ArtifactResponse(
                id = UUID.randomUUID(),
                trailId = UUID.randomUUID(),
                imageName = "image-$i",
                imageTag = "latest",
                sha256Digest = "sha-$i",
                registry = null,
                reportedAt = Instant.now(),
                reportedBy = "bot"
            )
        }
        val result = formatter.formatArtifactSearch("sha-", artifacts)
        assertTrue(result.contains("5 more"))
    }

    // ── formatTrailDetails ──────────────────────────────────────────────────

    @Test
    fun `formatTrailDetails includes trail metadata`() {
        val trailId = UUID.randomUUID()
        val flowId = UUID.randomUUID()
        val trail = TrailResponse(
            id = trailId, flowId = flowId,
            gitCommitSha = "deadbeef", gitBranch = "main",
            gitAuthor = "alice", gitAuthorEmail = "alice@example.com",
            pullRequestId = null, pullRequestReviewer = null, deploymentActor = null,
            status = TrailStatus.COMPLIANT, createdAt = Instant.now(), updatedAt = Instant.now()
        )
        val flow = FlowResponse(
            id = flowId, name = "my-flow", description = "",
            requiredAttestationTypes = listOf("BUILD"),
            tags = emptyMap(),
            createdAt = Instant.now(), updatedAt = Instant.now()
        )
        val attestations = listOf(
            AttestationResponse(
                id = UUID.randomUUID(), trailId = trailId, type = "BUILD",
                status = AttestationStatus.PASSED,
                evidenceFileHash = null, evidenceFileName = null, evidenceFileSizeBytes = null,
                details = null, name = null, evidenceUrl = null, compliant = true, createdAt = Instant.now()
            )
        )
        val result = formatter.formatTrailDetails(trail, flow, attestations)
        assertTrue(result.contains("deadbeef"))
        assertTrue(result.contains("my-flow"))
        assertTrue(result.contains("alice"))
        assertTrue(result.contains("COMPLIANT"))
        assertTrue(result.contains("BUILD"))
        assertTrue(result.contains("✅"))
    }

    @Test
    fun `formatTrailDetails with no attestations shows empty message`() {
        val trailId = UUID.randomUUID()
        val flowId = UUID.randomUUID()
        val trail = TrailResponse(
            id = trailId, flowId = flowId,
            gitCommitSha = "abc", gitBranch = "main",
            gitAuthor = "bob", gitAuthorEmail = "bob@example.com",
            pullRequestId = null, pullRequestReviewer = null, deploymentActor = null,
            status = TrailStatus.PENDING, createdAt = Instant.now(), updatedAt = Instant.now()
        )
        val flow = FlowResponse(
            id = flowId, name = "flow", description = "",
            requiredAttestationTypes = emptyList(),
            tags = emptyMap(),
            createdAt = Instant.now(), updatedAt = Instant.now()
        )
        val result = formatter.formatTrailDetails(trail, flow, emptyList())
        assertTrue(result.contains("No attestations"))
    }

    @Test
    fun `formatTrailDetails shows failed attestation with red cross`() {
        val trailId = UUID.randomUUID()
        val flowId = UUID.randomUUID()
        val trail = TrailResponse(
            id = trailId, flowId = flowId,
            gitCommitSha = "abc", gitBranch = "main",
            gitAuthor = "bob", gitAuthorEmail = "bob@example.com",
            pullRequestId = null, pullRequestReviewer = null, deploymentActor = null,
            status = TrailStatus.NON_COMPLIANT, createdAt = Instant.now(), updatedAt = Instant.now()
        )
        val flow = FlowResponse(
            id = flowId, name = "flow", description = "",
            requiredAttestationTypes = listOf("SECURITY_SCAN"),
            tags = emptyMap(),
            createdAt = Instant.now(), updatedAt = Instant.now()
        )
        val attestations = listOf(
            AttestationResponse(
                id = UUID.randomUUID(), trailId = trailId, type = "SECURITY_SCAN",
                status = AttestationStatus.FAILED,
                evidenceFileHash = null, evidenceFileName = null, evidenceFileSizeBytes = null,
                details = null, name = null, evidenceUrl = null, compliant = false, createdAt = Instant.now()
            )
        )
        val result = formatter.formatTrailDetails(trail, flow, attestations)
        assertTrue(result.contains("❌"))
        assertTrue(result.contains("SECURITY_SCAN"))
    }

    // ── formatTrailNonCompliant ──────────────────────────────────────────────

    @Test
    fun `formatTrailNonCompliant includes all details`() {
        val trailId = UUID.randomUUID()
        val result = formatter.formatTrailNonCompliant(
            trailId, "my-flow",
            listOf("TEST", "SCAN"), listOf("BUILD"),
            "https://factstore.example.com/trails/$trailId"
        )
        assertTrue(result.contains(trailId.toString()))
        assertTrue(result.contains("my-flow"))
        assertTrue(result.contains("TEST"))
        assertTrue(result.contains("SCAN"))
        assertTrue(result.contains("BUILD"))
        assertTrue(result.contains("https://"))
        assertTrue(result.contains("🚨"))
    }

    @Test
    fun `formatTrailNonCompliant without url omits link`() {
        val result = formatter.formatTrailNonCompliant(
            UUID.randomUUID(), "flow", listOf("BUILD"), emptyList(), null
        )
        assertFalse(result.contains("http"))
    }

    // ── formatApprovalRequested ──────────────────────────────────────────────

    @Test
    fun `formatApprovalRequested includes all fields`() {
        val result = formatter.formatApprovalRequested(
            "APR-42", "sha256:abc123", "production", listOf("alice", "bob")
        )
        assertTrue(result.contains("APR-42"))
        assertTrue(result.contains("sha256:abc123"))
        assertTrue(result.contains("production"))
        assertTrue(result.contains("alice"))
        assertTrue(result.contains("bob"))
        assertTrue(result.contains("✅"))
    }

    // ── formatApprovalDecision ───────────────────────────────────────────────

    @Test
    fun `formatApprovalDecision for approved shows green check`() {
        val result = formatter.formatApprovalDecision("APR-42", "approved", "alice", "LGTM")
        assertTrue(result.contains("✅"))
        assertTrue(result.contains("APR-42"))
        assertTrue(result.contains("alice"))
        assertTrue(result.contains("LGTM"))
    }

    @Test
    fun `formatApprovalDecision for rejected shows red cross`() {
        val result = formatter.formatApprovalDecision("APR-99", "rejected", "bob", null)
        assertTrue(result.contains("❌"))
        assertTrue(result.contains("APR-99"))
        assertTrue(result.contains("bob"))
        assertFalse(result.contains("Comment"))
    }

    // ── formatUnknownCommand ─────────────────────────────────────────────────

    @Test
    fun `formatUnknownCommand includes the bad input`() {
        val result = formatter.formatUnknownCommand("deploy production")
        assertTrue(result.contains("deploy production"))
        assertTrue(result.contains("help"))
    }
}
