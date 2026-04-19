package com.factstore.application

import com.factstore.adapter.mock.InMemoryArtifactRepository
import com.factstore.adapter.mock.InMemoryAttestationRepository
import com.factstore.adapter.mock.InMemoryFlowRepository
import com.factstore.adapter.mock.InMemoryTrailRepository
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.Flow
import com.factstore.core.domain.event.DomainEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class ReadModelProjectorTest {

    private lateinit var objectMapper: ObjectMapper
    private lateinit var projector: ReadModelProjector
    private lateinit var flowRepo: InMemoryFlowRepository
    private lateinit var trailRepo: InMemoryTrailRepository
    private lateinit var artifactRepo: InMemoryArtifactRepository
    private lateinit var attestationRepo: InMemoryAttestationRepository

    @BeforeEach
    fun setUp() {
        objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        flowRepo = InMemoryFlowRepository()
        trailRepo = InMemoryTrailRepository()
        artifactRepo = InMemoryArtifactRepository()
        attestationRepo = InMemoryAttestationRepository()
        projector = ReadModelProjector(flowRepo, trailRepo, artifactRepo, attestationRepo, objectMapper)
    }

    @Test
    fun `project FlowCreated creates flow in read model`() {
        val flowId = UUID.randomUUID()
        val event = DomainEvent.FlowCreated(
            aggregateId = flowId,
            name = "my-flow",
            description = "test flow",
            orgSlug = "acme",
            requiredAttestationTypes = listOf("snyk"),
            tags = mapOf("env" to "prod"),
            requiresApproval = true,
            requiredApproverRoles = listOf("admin")
        )
        val payload = objectMapper.writeValueAsString(event)

        val result = projector.project("FlowCreated", payload)

        assertTrue(result)
        val flow = flowRepo.findById(flowId)
        assertNotNull(flow)
        assertEquals("my-flow", flow!!.name)
        assertEquals("test flow", flow.description)
        assertEquals("acme", flow.orgSlug)
        assertEquals(listOf("snyk"), flow.requiredAttestationTypes)
        assertEquals(mapOf("env" to "prod"), flow.tags)
        assertTrue(flow.requiresApproval)
        assertEquals(listOf("admin"), flow.requiredApproverRoles)
    }

    @Test
    fun `project FlowUpdated updates existing flow`() {
        val flowId = UUID.randomUUID()
        flowRepo.save(Flow(id = flowId, name = "original", description = "old"))

        val event = DomainEvent.FlowUpdated(
            aggregateId = flowId,
            name = "updated-name",
            description = "new desc"
        )
        val payload = objectMapper.writeValueAsString(event)

        assertTrue(projector.project("FlowUpdated", payload))

        val flow = flowRepo.findById(flowId)!!
        assertEquals("updated-name", flow.name)
        assertEquals("new desc", flow.description)
    }

    @Test
    fun `project FlowDeleted removes flow from read model`() {
        val flowId = UUID.randomUUID()
        flowRepo.save(Flow(id = flowId, name = "to-delete", description = "bye"))

        val event = DomainEvent.FlowDeleted(aggregateId = flowId)
        val payload = objectMapper.writeValueAsString(event)

        assertTrue(projector.project("FlowDeleted", payload))
        assertNull(flowRepo.findById(flowId))
    }

    @Test
    fun `project FlowCreated is idempotent`() {
        val flowId = UUID.randomUUID()
        flowRepo.save(Flow(id = flowId, name = "existing", description = "already here"))

        val event = DomainEvent.FlowCreated(
            aggregateId = flowId,
            name = "duplicate",
            description = "should be skipped"
        )
        val payload = objectMapper.writeValueAsString(event)

        assertTrue(projector.project("FlowCreated", payload))
        assertEquals("existing", flowRepo.findById(flowId)!!.name)
    }

    @Test
    fun `project TrailCreated creates trail in read model`() {
        val trailId = UUID.randomUUID()
        val flowId = UUID.randomUUID()
        val event = DomainEvent.TrailCreated(
            aggregateId = trailId,
            flowId = flowId,
            gitCommitSha = "abc123",
            gitBranch = "main",
            gitAuthor = "dev",
            gitAuthorEmail = "dev@test.com",
            buildUrl = "https://ci.example.com/123"
        )
        val payload = objectMapper.writeValueAsString(event)

        assertTrue(projector.project("TrailCreated", payload))

        val trail = trailRepo.findById(trailId)
        assertNotNull(trail)
        assertEquals(flowId, trail!!.flowId)
        assertEquals("abc123", trail.gitCommitSha)
        assertEquals("main", trail.gitBranch)
    }

    @Test
    fun `project ArtifactReported creates artifact in read model`() {
        val artifactId = UUID.randomUUID()
        val trailId = UUID.randomUUID()
        val event = DomainEvent.ArtifactReported(
            aggregateId = artifactId,
            trailId = trailId,
            imageName = "myapp",
            imageTag = "v1.0",
            sha256Digest = "sha256:abc",
            reportedBy = "ci-bot"
        )
        val payload = objectMapper.writeValueAsString(event)

        assertTrue(projector.project("ArtifactReported", payload))

        val artifact = artifactRepo.findById(artifactId)
        assertNotNull(artifact)
        assertEquals("myapp", artifact!!.imageName)
    }

    @Test
    fun `project AttestationRecorded creates attestation in read model`() {
        val attestationId = UUID.randomUUID()
        val trailId = UUID.randomUUID()
        val event = DomainEvent.AttestationRecorded(
            aggregateId = attestationId,
            trailId = trailId,
            type = "snyk-scan",
            status = "PASSED",
            details = "No vulnerabilities"
        )
        val payload = objectMapper.writeValueAsString(event)

        assertTrue(projector.project("AttestationRecorded", payload))

        val attestation = attestationRepo.findById(attestationId)
        assertNotNull(attestation)
        assertEquals("snyk-scan", attestation!!.type)
        assertEquals(AttestationStatus.PASSED, attestation.status)
    }

    @Test
    fun `project unknown event type returns false`() {
        assertFalse(projector.project("UnknownEvent", "{}"))
    }

    @Test
    fun `project EvidenceUploaded updates attestation in read model`() {
        val attestationId = UUID.randomUUID()
        val trailId = UUID.randomUUID()
        // Pre-create attestation
        val attestation = com.factstore.core.domain.Attestation(
            id = attestationId,
            trailId = trailId,
            type = "snyk-scan",
            status = AttestationStatus.PASSED
        )
        attestationRepo.save(attestation)

        val event = DomainEvent.EvidenceUploaded(
            aggregateId = attestationId,
            trailId = trailId,
            fileName = "report.pdf",
            contentType = "application/pdf",
            sha256Hash = "sha256:abc123",
            fileSizeBytes = 42_000L
        )
        val payload = objectMapper.writeValueAsString(event)

        assertTrue(projector.project("EvidenceUploaded", payload))

        val updated = attestationRepo.findById(attestationId)
        assertNotNull(updated)
        assertEquals("report.pdf", updated!!.evidenceFileName)
        assertEquals("sha256:abc123", updated.evidenceFileHash)
        assertEquals(42_000L, updated.evidenceFileSizeBytes)
    }

    @Test
    fun `project EvidenceUploaded for unknown attestation is skipped`() {
        val unknownId = UUID.randomUUID()
        val event = DomainEvent.EvidenceUploaded(
            aggregateId = unknownId,
            trailId = UUID.randomUUID(),
            fileName = "report.pdf",
            contentType = "application/pdf",
            sha256Hash = "sha256:abc123",
            fileSizeBytes = 42_000L
        )
        val payload = objectMapper.writeValueAsString(event)

        // Should succeed (not crash) but skip because attestation doesn't exist
        assertTrue(projector.project("EvidenceUploaded", payload))
    }

    @Test
    fun `project malformed payload returns false`() {
        assertFalse(projector.project("FlowCreated", "not-json"))
    }
}
