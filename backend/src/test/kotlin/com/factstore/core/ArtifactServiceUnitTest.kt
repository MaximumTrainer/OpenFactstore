package com.factstore.core

import com.factstore.adapter.mock.InMemoryArtifactRepository
import com.factstore.adapter.mock.InMemoryBuildProvenanceRepository
import com.factstore.adapter.mock.InMemoryFlowRepository
import com.factstore.adapter.mock.InMemoryTrailRepository
import com.factstore.application.ArtifactService
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.Flow
import com.factstore.core.domain.Trail
import com.factstore.core.port.inbound.IAuditService
import com.factstore.dto.AuditEventPage
import com.factstore.dto.AuditEventResponse
import com.factstore.dto.CreateArtifactRequest
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

@Suppress("FunctionName")
class ArtifactServiceUnitTest {

    private lateinit var artifactService: ArtifactService
    private lateinit var trailRepository: InMemoryTrailRepository
    private lateinit var flowRepository: InMemoryFlowRepository

    private val noopAuditService = object : IAuditService {
        override fun record(
            eventType: AuditEventType,
            actor: String,
            payload: Map<String, Any?>,
            trailId: UUID?,
            artifactSha256: String?,
            environmentId: UUID?
        ) = AuditEventResponse(
            id = UUID.randomUUID(),
            eventType = eventType,
            environmentId = environmentId,
            trailId = trailId,
            artifactSha256 = artifactSha256,
            actor = actor,
            payload = payload.toString(),
            occurredAt = Instant.now()
        )

        override fun getEvent(id: UUID): AuditEventResponse = throw UnsupportedOperationException("noop")

        override fun queryEvents(
            eventType: AuditEventType?,
            trailId: UUID?,
            actor: String?,
            from: Instant?,
            to: Instant?,
            page: Int,
            size: Int,
            sortDesc: Boolean
        ) = AuditEventPage(emptyList(), page, size, 0L, 0)

        override fun getEventsForTrail(trailId: UUID) = emptyList<AuditEventResponse>()
    }

    @BeforeEach
    fun setUp() {
        trailRepository = InMemoryTrailRepository()
        flowRepository = InMemoryFlowRepository()
        artifactService = ArtifactService(
            InMemoryArtifactRepository(),
            trailRepository,
            noopAuditService,
            InMemoryBuildProvenanceRepository()
        )
    }

    private fun createFlow(): Flow {
        val flow = Flow(name = "test-flow", description = "")
        flowRepository.save(flow)
        return flow
    }

    private fun createTrail(flowId: UUID): Trail {
        val trail = Trail(
            flowId = flowId,
            gitCommitSha = "abc123",
            gitBranch = "main",
            gitAuthor = "dev",
            gitAuthorEmail = "dev@example.com"
        )
        trailRepository.save(trail)
        return trail
    }

    @Test
    fun `reportArtifact happy path creates artifact with correct fields`() {
        val flow = createFlow()
        val trail = createTrail(flow.id)
        val req = CreateArtifactRequest(
            imageName = "my-app",
            imageTag = "v1.0.0",
            sha256Digest = "sha256:abc123",
            registry = "ghcr.io",
            reportedBy = "ci-bot"
        )

        val resp = artifactService.reportArtifact(trail.id, req)

        assertEquals("my-app", resp.imageName)
        assertEquals("v1.0.0", resp.imageTag)
        assertEquals("sha256:abc123", resp.sha256Digest)
        assertEquals("ghcr.io", resp.registry)
        assertEquals("ci-bot", resp.reportedBy)
        assertEquals(trail.id, resp.trailId)
        assertNotNull(resp.id)
    }

    @Test
    fun `reportArtifact throws NotFoundException when trail does not exist`() {
        val req = CreateArtifactRequest(
            imageName = "my-app",
            imageTag = "v1.0.0",
            sha256Digest = "sha256:abc123",
            reportedBy = "ci-bot"
        )
        assertThrows<NotFoundException> {
            artifactService.reportArtifact(UUID.randomUUID(), req)
        }
    }

    @Test
    fun `findBySha256 returns artifacts when found`() {
        val flow = createFlow()
        val trail = createTrail(flow.id)
        artifactService.reportArtifact(
            trail.id,
            CreateArtifactRequest("my-app", "v1.0.0", "sha256:findme", reportedBy = "ci-bot")
        )

        val result = artifactService.findBySha256("sha256:findme")

        assertEquals(1, result.size)
        assertEquals("sha256:findme", result[0].sha256Digest)
        assertEquals("my-app", result[0].imageName)
    }

    @Test
    fun `findBySha256 returns empty list when no artifact matches`() {
        val result = artifactService.findBySha256("sha256:nonexistent")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listArtifactsForTrail returns all artifacts belonging to the trail`() {
        val flow = createFlow()
        val trail = createTrail(flow.id)
        artifactService.reportArtifact(trail.id, CreateArtifactRequest("app1", "v1", "sha256:aaa", reportedBy = "bot"))
        artifactService.reportArtifact(trail.id, CreateArtifactRequest("app2", "v2", "sha256:bbb", reportedBy = "bot"))

        val result = artifactService.listArtifactsForTrail(trail.id)

        assertEquals(2, result.size)
        assertTrue(result.map { it.imageName }.containsAll(listOf("app1", "app2")))
    }

    @Test
    fun `listArtifactsForTrail returns empty list when trail has no artifacts`() {
        val flow = createFlow()
        val trail = createTrail(flow.id)

        val result = artifactService.listArtifactsForTrail(trail.id)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listArtifactsForTrail throws NotFoundException when trail does not exist`() {
        assertThrows<NotFoundException> {
            artifactService.listArtifactsForTrail(UUID.randomUUID())
        }
    }

    @Test
    fun `findBySha256 returns multiple artifacts with the same digest across different trails`() {
        val flow = createFlow()
        val trail1 = createTrail(flow.id)
        val trail2 = createTrail(flow.id)
        val digest = "sha256:shared"
        artifactService.reportArtifact(trail1.id, CreateArtifactRequest("app", "v1", digest, reportedBy = "bot"))
        artifactService.reportArtifact(trail2.id, CreateArtifactRequest("app", "v1", digest, reportedBy = "bot"))

        val result = artifactService.findBySha256(digest)

        assertEquals(2, result.size)
        assertTrue(result.all { it.sha256Digest == digest })
    }
}
