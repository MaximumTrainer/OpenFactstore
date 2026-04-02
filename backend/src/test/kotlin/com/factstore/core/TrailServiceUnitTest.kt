package com.factstore.core

import com.factstore.adapter.mock.InMemoryFlowRepository
import com.factstore.adapter.mock.InMemoryTrailRepository
import com.factstore.application.TrailService
import com.factstore.core.domain.Flow
import com.factstore.core.domain.TrailStatus
import com.factstore.dto.CreateTrailRequest
import com.factstore.exception.BadRequestException
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

@Suppress("FunctionName")
class TrailServiceUnitTest {

    private lateinit var trailService: TrailService
    private lateinit var trailRepository: InMemoryTrailRepository
    private lateinit var flowRepository: InMemoryFlowRepository

    @BeforeEach
    fun setUp() {
        trailRepository = InMemoryTrailRepository()
        flowRepository = InMemoryFlowRepository()
        trailService = TrailService(trailRepository, flowRepository)
    }

    private fun createFlow(name: String = "test-flow"): Flow {
        val flow = Flow(name = name, description = "")
        flowRepository.save(flow)
        return flow
    }

    private fun trailRequest(
        flowId: UUID,
        sha: String = "abc123",
        branch: String = "main",
        author: String = "alice",
        email: String = "alice@example.com",
        name: String? = null
    ) = CreateTrailRequest(
        flowId = flowId,
        gitCommitSha = sha,
        gitBranch = branch,
        gitAuthor = author,
        gitAuthorEmail = email,
        name = name
    )

    @Test
    fun `createTrail happy path stores trail with correct fields`() {
        val flow = createFlow()
        val req = trailRequest(
            flowId = flow.id,
            sha = "deadbeef",
            branch = "feature/x",
            author = "alice",
            email = "alice@example.com"
        )

        val resp = trailService.createTrail(req)

        assertEquals(flow.id, resp.flowId)
        assertEquals("deadbeef", resp.gitCommitSha)
        assertEquals("feature/x", resp.gitBranch)
        assertEquals("alice", resp.gitAuthor)
        assertEquals("alice@example.com", resp.gitAuthorEmail)
        assertEquals(TrailStatus.PENDING, resp.status)
        assertNotNull(resp.id)
    }

    @Test
    fun `createTrail stores name when name field is set`() {
        val flow = createFlow()
        val req = trailRequest(flow.id, name = "release-1.2.3")

        val resp = trailService.createTrail(req)

        assertEquals("release-1.2.3", resp.name)
    }

    @Test
    fun `createTrail throws NotFoundException when flow does not exist`() {
        val req = trailRequest(flowId = UUID.randomUUID())
        assertThrows<NotFoundException> { trailService.createTrail(req) }
    }

    @Test
    fun `createTrail throws BadRequestException when gitCommitSha is null`() {
        val flow = createFlow()
        val req = CreateTrailRequest(
            flowId = flow.id,
            gitCommitSha = null,
            gitBranch = "main",
            gitAuthor = "alice",
            gitAuthorEmail = "alice@example.com"
        )
        assertThrows<BadRequestException> { trailService.createTrail(req) }
    }

    @Test
    fun `createTrail throws BadRequestException when gitBranch is null`() {
        val flow = createFlow()
        val req = CreateTrailRequest(
            flowId = flow.id,
            gitCommitSha = "abc123",
            gitBranch = null,
            gitAuthor = "alice",
            gitAuthorEmail = "alice@example.com"
        )
        assertThrows<BadRequestException> { trailService.createTrail(req) }
    }

    @Test
    fun `getTrail returns trail when found`() {
        val flow = createFlow()
        val created = trailService.createTrail(trailRequest(flow.id))

        val found = trailService.getTrail(created.id)

        assertEquals(created.id, found.id)
        assertEquals("abc123", found.gitCommitSha)
    }

    @Test
    fun `getTrail throws NotFoundException for unknown id`() {
        assertThrows<NotFoundException> { trailService.getTrail(UUID.randomUUID()) }
    }

    @Test
    fun `listTrailsForFlow returns only trails belonging to that flow`() {
        val flowA = createFlow("flow-a")
        val flowB = createFlow("flow-b")
        trailService.createTrail(trailRequest(flowA.id, sha = "sha1"))
        trailService.createTrail(trailRequest(flowA.id, sha = "sha2"))
        trailService.createTrail(trailRequest(flowB.id, sha = "sha3"))

        val trails = trailService.listTrailsForFlow(flowA.id)

        assertEquals(2, trails.size)
        assertTrue(trails.all { it.flowId == flowA.id })
    }

    @Test
    fun `listTrailsForFlow throws NotFoundException when flow does not exist`() {
        assertThrows<NotFoundException> { trailService.listTrailsForFlow(UUID.randomUUID()) }
    }

    @Test
    fun `listTrails with flowId filters results to that flow`() {
        val flowA = createFlow("flow-a")
        val flowB = createFlow("flow-b")
        trailService.createTrail(trailRequest(flowA.id, sha = "sha1"))
        trailService.createTrail(trailRequest(flowB.id, sha = "sha2"))

        val result = trailService.listTrails(flowA.id)

        assertEquals(1, result.size)
        assertEquals(flowA.id, result[0].flowId)
    }

    @Test
    fun `listTrails without flowId returns all trails`() {
        val flowA = createFlow("flow-a")
        val flowB = createFlow("flow-b")
        trailService.createTrail(trailRequest(flowA.id, sha = "sha1"))
        trailService.createTrail(trailRequest(flowB.id, sha = "sha2"))

        val result = trailService.listTrails(null)

        assertEquals(2, result.size)
    }

    @Test
    fun `updateTrailStatus changes the trail status`() {
        val flow = createFlow()
        val created = trailService.createTrail(trailRequest(flow.id))
        assertEquals(TrailStatus.PENDING, created.status)

        trailService.updateTrailStatus(created.id, TrailStatus.COMPLIANT)

        val updated = trailService.getTrail(created.id)
        assertEquals(TrailStatus.COMPLIANT, updated.status)
    }

    @Test
    fun `updateTrailStatus throws NotFoundException when trail does not exist`() {
        assertThrows<NotFoundException> {
            trailService.updateTrailStatus(UUID.randomUUID(), TrailStatus.COMPLIANT)
        }
    }

    @Test
    fun `findByName returns trail when name and flowId match`() {
        val flow = createFlow()
        trailService.createTrail(trailRequest(flow.id, name = "my-named-trail"))

        val found = trailService.findByName(flow.id, "my-named-trail")

        assertEquals("my-named-trail", found.name)
        assertEquals(flow.id, found.flowId)
    }

    @Test
    fun `findByName throws NotFoundException when no trail with that name exists`() {
        val flow = createFlow()
        assertThrows<NotFoundException> {
            trailService.findByName(flow.id, "does-not-exist")
        }
    }

    @Test
    fun `findByName throws NotFoundException when name matches but flowId differs`() {
        val flowA = createFlow("flow-a")
        val flowB = createFlow("flow-b")
        trailService.createTrail(trailRequest(flowA.id, name = "shared-name"))

        assertThrows<NotFoundException> {
            trailService.findByName(flowB.id, "shared-name")
        }
    }
}
