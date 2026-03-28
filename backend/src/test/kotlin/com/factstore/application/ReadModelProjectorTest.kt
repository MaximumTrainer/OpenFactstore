package com.factstore.application

import com.factstore.adapter.mock.InMemoryEventStore
import com.factstore.core.domain.Artifact
import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.Flow
import com.factstore.core.domain.Trail
import com.factstore.core.domain.event.DomainEvent
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.ITrailRepository
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
    fun `project malformed payload returns false`() {
        assertFalse(projector.project("FlowCreated", "not-json"))
    }

    // ── Simple in-memory repository implementations for testing ───────────────

    private class InMemoryFlowRepository : IFlowRepository {
        private val store = mutableMapOf<UUID, Flow>()
        override fun save(flow: Flow): Flow { store[flow.id] = flow; return flow }
        override fun findById(id: UUID): Flow? = store[id]
        override fun findAll(): List<Flow> = store.values.toList()
        override fun findAllByIds(ids: Collection<UUID>) = ids.mapNotNull { store[it] }
        override fun existsById(id: UUID): Boolean = store.containsKey(id)
        override fun existsByName(name: String): Boolean = store.values.any { it.name == name }
        override fun deleteById(id: UUID) { store.remove(id) }
        override fun countAll(): Long = store.size.toLong()
        override fun findAllByOrgSlug(orgSlug: String) = store.values.filter { it.orgSlug == orgSlug }
    }

    private class InMemoryTrailRepository : ITrailRepository {
        private val store = mutableMapOf<UUID, Trail>()
        override fun save(trail: Trail): Trail { store[trail.id] = trail; return trail }
        override fun findById(id: UUID): Trail? = store[id]
        override fun findAll(): List<Trail> = store.values.toList()
        override fun existsById(id: UUID): Boolean = store.containsKey(id)
        override fun findByFlowId(flowId: UUID) = store.values.filter { it.flowId == flowId }
        override fun searchByQuery(query: String) = emptyList<Trail>()
        override fun findByFlowIdAndCreatedAtBetween(flowId: UUID, from: java.time.Instant, to: java.time.Instant) = emptyList<Trail>()
        override fun findByFlowIdAndCreatedAtAfter(flowId: UUID, from: java.time.Instant) = emptyList<Trail>()
        override fun findByFlowIdAndCreatedAtBefore(flowId: UUID, to: java.time.Instant) = emptyList<Trail>()
        override fun findByCreatedAtBetween(from: java.time.Instant, to: java.time.Instant) = emptyList<Trail>()
        override fun findByCreatedAtAfter(from: java.time.Instant) = emptyList<Trail>()
        override fun findByCreatedAtBefore(to: java.time.Instant) = emptyList<Trail>()
        override fun countAll(): Long = store.size.toLong()
        override fun countByStatus(status: com.factstore.core.domain.TrailStatus): Long = 0
    }

    private class InMemoryArtifactRepository : IArtifactRepository {
        private val store = mutableMapOf<UUID, Artifact>()
        override fun save(artifact: Artifact): Artifact { store[artifact.id] = artifact; return artifact }
        override fun findById(id: UUID): Artifact? = store[id]
        override fun findByTrailId(trailId: UUID) = store.values.filter { it.trailId == trailId }
        override fun findBySha256Digest(sha256Digest: String) = store.values.filter { it.sha256Digest == sha256Digest }
        override fun findBySha256DigestStartingWith(prefix: String) = store.values.filter { it.sha256Digest.startsWith(prefix) }
        override fun findAll(): List<Artifact> = store.values.toList()
        override fun searchByQuery(query: String) = emptyList<Artifact>()
    }

    private class InMemoryAttestationRepository : IAttestationRepository {
        private val store = mutableMapOf<UUID, Attestation>()
        override fun save(attestation: Attestation): Attestation { store[attestation.id] = attestation; return attestation }
        override fun findById(id: UUID): Attestation? = store[id]
        override fun findByTrailId(trailId: UUID) = store.values.filter { it.trailId == trailId }
        override fun findByTrailIdIn(trailIds: Collection<UUID>) = store.values.filter { it.trailId in trailIds }
        override fun findAll(): List<Attestation> = store.values.toList()
        override fun findByArtifactFingerprint(fingerprint: String) = store.values.filter { it.artifactFingerprint == fingerprint }
    }
}
