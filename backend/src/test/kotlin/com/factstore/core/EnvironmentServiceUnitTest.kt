package com.factstore.core

import com.factstore.adapter.mock.InMemoryEnvironmentRepository
import com.factstore.adapter.mock.InMemoryEnvironmentSnapshotRepository
import com.factstore.adapter.mock.InMemorySnapshotArtifactRepository
import com.factstore.adapter.mock.InMemoryEnvironmentBaselineRepository
import com.factstore.adapter.mock.InMemoryDriftReportRepository
import com.factstore.application.EnvironmentService
import com.factstore.core.domain.Artifact
import com.factstore.core.domain.Attestation
import com.factstore.core.domain.Deployment
import com.factstore.core.domain.EnvironmentType
import com.factstore.core.domain.Flow
import com.factstore.core.domain.PolicyAttachment
import com.factstore.core.domain.Trail
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IDeploymentRepository
import com.factstore.core.port.outbound.IEventPublisher
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.IPolicyAttachmentRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.core.port.outbound.SupplyChainEvent
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.dto.RecordSnapshotRequest
import com.factstore.dto.SnapshotArtifactRequest
import com.factstore.dto.UpdateEnvironmentRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID

class EnvironmentServiceUnitTest {

    private lateinit var environmentService: EnvironmentService

    @BeforeEach
    fun setUp() {
        val noopDeploymentRepo = object : IDeploymentRepository {
            override fun save(deployment: Deployment) = deployment
            override fun findByArtifactSha256(sha256: String) = emptyList<Deployment>()
            override fun findByEnvironmentId(environmentId: UUID) = emptyList<Deployment>()
            override fun existsByArtifactSha256AndEnvironmentId(sha256: String, environmentId: UUID) = false
        }
        val noopEventPublisher = object : IEventPublisher {
            override fun publish(event: SupplyChainEvent) {}
        }
        val noopPolicyAttachmentRepo = object : IPolicyAttachmentRepository {
            override fun save(attachment: PolicyAttachment) = attachment
            override fun findById(id: UUID): PolicyAttachment? = null
            override fun findAll() = emptyList<PolicyAttachment>()
            override fun existsById(id: UUID) = false
            override fun existsByPolicyIdAndEnvironmentId(policyId: UUID, environmentId: UUID) = false
            override fun deleteById(id: UUID) {}
            override fun findByEnvironmentId(environmentId: UUID) = emptyList<PolicyAttachment>()
        }
        val noopArtifactRepo = object : IArtifactRepository {
            override fun save(artifact: Artifact) = artifact
            override fun findById(id: UUID): Artifact? = null
            override fun findByTrailId(trailId: UUID) = emptyList<Artifact>()
            override fun findBySha256Digest(sha256Digest: String) = emptyList<Artifact>()
            override fun findBySha256DigestStartingWith(prefix: String) = emptyList<Artifact>()
            override fun findAll() = emptyList<Artifact>()
            override fun searchByQuery(query: String) = emptyList<Artifact>()
        }
        val noopAttestationRepo = object : IAttestationRepository {
            override fun save(attestation: Attestation) = attestation
            override fun findById(id: UUID): Attestation? = null
            override fun findByTrailId(trailId: UUID) = emptyList<Attestation>()
            override fun findByTrailId(trailId: UUID, pageable: Pageable): Page<Attestation> =
                PageImpl(emptyList())
            override fun findByTrailIdIn(trailIds: Collection<UUID>) = emptyList<Attestation>()
            override fun findAll() = emptyList<Attestation>()
            override fun findByArtifactFingerprint(fingerprint: String) = emptyList<Attestation>()
        }
        val noopFlowRepo = object : IFlowRepository {
            override fun save(flow: Flow) = flow
            override fun findById(id: UUID): Flow? = null
            override fun findAll() = emptyList<Flow>()
            override fun findAll(pageable: Pageable): Page<Flow> = PageImpl(emptyList())
            override fun findAllByIds(ids: Collection<UUID>) = emptyList<Flow>()
            override fun existsById(id: UUID) = false
            override fun existsByName(name: String) = false
            override fun deleteById(id: UUID) {}
            override fun countAll() = 0L
            override fun findAllByOrgSlug(orgSlug: String) = emptyList<Flow>()
        }
        val noopTrailRepo = object : ITrailRepository {
            override fun save(trail: Trail) = trail
            override fun findById(id: UUID): Trail? = null
            override fun findAll() = emptyList<Trail>()
            override fun existsById(id: UUID) = false
            override fun findByFlowId(flowId: UUID) = emptyList<Trail>()
            override fun findByFlowId(flowId: UUID, pageable: Pageable): Page<Trail> = PageImpl(emptyList())
            override fun searchByQuery(query: String) = emptyList<Trail>()
            override fun findByFlowIdAndCreatedAtBetween(flowId: UUID, from: Instant, to: Instant) = emptyList<Trail>()
            override fun findByFlowIdAndCreatedAtAfter(flowId: UUID, from: Instant) = emptyList<Trail>()
            override fun findByFlowIdAndCreatedAtBefore(flowId: UUID, to: Instant) = emptyList<Trail>()
            override fun findByCreatedAtBetween(from: Instant, to: Instant) = emptyList<Trail>()
            override fun findByCreatedAtAfter(from: Instant) = emptyList<Trail>()
            override fun findByCreatedAtBefore(to: Instant) = emptyList<Trail>()
            override fun countAll() = 0L
            override fun countByStatus(status: com.factstore.core.domain.TrailStatus) = 0L
            override fun findByFlowIdAndName(flowId: UUID, name: String): Trail? = null
        }

        environmentService = EnvironmentService(
            InMemoryEnvironmentRepository(),
            InMemoryEnvironmentSnapshotRepository(),
            InMemorySnapshotArtifactRepository(),
            InMemoryEnvironmentBaselineRepository(),
            InMemoryDriftReportRepository(),
            noopDeploymentRepo,
            noopEventPublisher,
            noopPolicyAttachmentRepo,
            noopArtifactRepo,
            noopAttestationRepo,
            noopFlowRepo,
            noopTrailRepo
        )
    }

    @Test
    fun `create environment succeeds and returns response with generated id`() {
        val req = CreateEnvironmentRequest("production", EnvironmentType.K8S, "Production cluster")
        val resp = environmentService.createEnvironment(req)
        assertEquals("production", resp.name)
        assertEquals(EnvironmentType.K8S, resp.type)
        assertEquals("Production cluster", resp.description)
        assertNotNull(resp.id)
    }

    @Test
    fun `create environment with duplicate name throws ConflictException`() {
        environmentService.createEnvironment(CreateEnvironmentRequest("prod", EnvironmentType.K8S))
        assertThrows<ConflictException> {
            environmentService.createEnvironment(CreateEnvironmentRequest("prod", EnvironmentType.S3))
        }
    }

    @Test
    fun `get environment by unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            environmentService.getEnvironment(UUID.randomUUID())
        }
    }

    @Test
    fun `list environments returns all created environments`() {
        environmentService.createEnvironment(CreateEnvironmentRequest("env-a", EnvironmentType.K8S))
        environmentService.createEnvironment(CreateEnvironmentRequest("env-b", EnvironmentType.LAMBDA))
        val envs = environmentService.listEnvironments()
        assertEquals(2, envs.size)
        assertTrue(envs.map { it.name }.containsAll(listOf("env-a", "env-b")))
    }

    @Test
    fun `update environment changes the specified fields`() {
        val created = environmentService.createEnvironment(
            CreateEnvironmentRequest("update-me", EnvironmentType.GENERIC, "old desc")
        )
        val updated = environmentService.updateEnvironment(
            created.id,
            UpdateEnvironmentRequest(description = "new desc", type = EnvironmentType.K8S)
        )
        assertEquals("new desc", updated.description)
        assertEquals(EnvironmentType.K8S, updated.type)
    }

    @Test
    fun `update environment name to duplicate name throws ConflictException`() {
        environmentService.createEnvironment(CreateEnvironmentRequest("existing", EnvironmentType.K8S))
        val other = environmentService.createEnvironment(CreateEnvironmentRequest("other", EnvironmentType.S3))
        assertThrows<ConflictException> {
            environmentService.updateEnvironment(other.id, UpdateEnvironmentRequest(name = "existing"))
        }
    }

    @Test
    fun `delete environment removes it from storage`() {
        val created = environmentService.createEnvironment(
            CreateEnvironmentRequest("del-env", EnvironmentType.GENERIC)
        )
        environmentService.deleteEnvironment(created.id)
        assertThrows<NotFoundException> { environmentService.getEnvironment(created.id) }
    }

    @Test
    fun `delete non-existent environment throws NotFoundException`() {
        assertThrows<NotFoundException> { environmentService.deleteEnvironment(UUID.randomUUID()) }
    }

    @Test
    fun `record snapshot increments snapshotIndex monotonically`() {
        val env = environmentService.createEnvironment(
            CreateEnvironmentRequest("snap-env", EnvironmentType.K8S)
        )
        val req = RecordSnapshotRequest(
            recordedBy = "ci-bot",
            artifacts = listOf(
                SnapshotArtifactRequest("sha256:abc", "my-app", "v1.0", 2)
            )
        )
        val snap1 = environmentService.recordSnapshot(env.id, req)
        val snap2 = environmentService.recordSnapshot(env.id, req)
        assertEquals(1L, snap1.snapshotIndex)
        assertEquals(2L, snap2.snapshotIndex)
    }

    @Test
    fun `record snapshot stores artifacts`() {
        val env = environmentService.createEnvironment(
            CreateEnvironmentRequest("artifact-env", EnvironmentType.K8S)
        )
        val snap = environmentService.recordSnapshot(
            env.id,
            RecordSnapshotRequest(
                recordedBy = "ci-bot",
                artifacts = listOf(
                    SnapshotArtifactRequest("sha256:abc", "my-app", "v1.0", 3),
                    SnapshotArtifactRequest("sha256:def", "my-db", "v2.0", 1)
                )
            )
        )
        assertEquals(2, snap.artifacts.size)
        assertEquals("sha256:abc", snap.artifacts[0].artifactSha256)
        assertEquals(3, snap.artifacts[0].instanceCount)
    }

    @Test
    fun `get latest snapshot returns the most recent one`() {
        val env = environmentService.createEnvironment(
            CreateEnvironmentRequest("latest-env", EnvironmentType.K8S)
        )
        val req = RecordSnapshotRequest(recordedBy = "ci-bot")
        environmentService.recordSnapshot(env.id, req)
        environmentService.recordSnapshot(env.id, req)
        val latest = environmentService.getLatestSnapshot(env.id)
        assertEquals(2L, latest.snapshotIndex)
    }

    @Test
    fun `get latest snapshot for environment with no snapshots throws NotFoundException`() {
        val env = environmentService.createEnvironment(
            CreateEnvironmentRequest("empty-env", EnvironmentType.K8S)
        )
        assertThrows<NotFoundException> { environmentService.getLatestSnapshot(env.id) }
    }

    @Test
    fun `get snapshot by index returns correct snapshot`() {
        val env = environmentService.createEnvironment(
            CreateEnvironmentRequest("idx-env", EnvironmentType.K8S)
        )
        val req = RecordSnapshotRequest(recordedBy = "ci-bot")
        environmentService.recordSnapshot(env.id, req)
        environmentService.recordSnapshot(env.id, req)
        val snap = environmentService.getSnapshot(env.id, 1L)
        assertEquals(1L, snap.snapshotIndex)
    }

    @Test
    fun `get snapshot by unknown index throws NotFoundException`() {
        val env = environmentService.createEnvironment(
            CreateEnvironmentRequest("notfound-env", EnvironmentType.K8S)
        )
        assertThrows<NotFoundException> { environmentService.getSnapshot(env.id, 99L) }
    }

    @Test
    fun `list snapshots returns all snapshots in order`() {
        val env = environmentService.createEnvironment(
            CreateEnvironmentRequest("list-env", EnvironmentType.K8S)
        )
        val req = RecordSnapshotRequest(recordedBy = "ci-bot")
        repeat(3) { environmentService.recordSnapshot(env.id, req) }
        val snapshots = environmentService.listSnapshots(env.id)
        assertEquals(3, snapshots.size)
        assertEquals(listOf(1L, 2L, 3L), snapshots.map { it.snapshotIndex })
    }

    @Test
    fun `record snapshot for non-existent environment throws NotFoundException`() {
        assertThrows<NotFoundException> {
            environmentService.recordSnapshot(UUID.randomUUID(), RecordSnapshotRequest(recordedBy = "bot"))
        }
    }
}
