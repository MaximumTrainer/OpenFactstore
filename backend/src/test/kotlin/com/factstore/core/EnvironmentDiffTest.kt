package com.factstore.core

import com.factstore.adapter.mock.InMemoryDriftReportRepository
import com.factstore.adapter.mock.InMemoryEnvironmentBaselineRepository
import com.factstore.adapter.mock.InMemoryEnvironmentRepository
import com.factstore.adapter.mock.InMemoryEnvironmentSnapshotRepository
import com.factstore.adapter.mock.InMemorySnapshotArtifactRepository
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

@Suppress("FunctionName")
class EnvironmentDiffTest {

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

    private fun createEnv(name: String = "diff-env") =
        environmentService.createEnvironment(CreateEnvironmentRequest(name, EnvironmentType.K8S))

    @Test
    fun `diffSnapshots between two identical snapshots has no added or removed entries`() {
        val env = createEnv()
        val artifacts = listOf(
            SnapshotArtifactRequest("sha256:aaa", "app", "v1.0", 1),
            SnapshotArtifactRequest("sha256:bbb", "db", "v2.0", 1)
        )
        val req = RecordSnapshotRequest(recordedBy = "ci-bot", artifacts = artifacts)
        environmentService.recordSnapshot(env.id, req)
        environmentService.recordSnapshot(env.id, req)

        val diff = environmentService.diffSnapshots(env.id, 1L, 2L)

        assertTrue(diff.added.isEmpty())
        assertTrue(diff.removed.isEmpty())
        assertTrue(diff.updated.isEmpty())
        assertEquals(2, diff.unchanged.size)
        assertEquals(env.id, diff.environmentId)
        assertEquals(1L, diff.fromSnapshotIndex)
        assertEquals(2L, diff.toSnapshotIndex)
    }

    @Test
    fun `diffSnapshots shows artifact added in second snapshot`() {
        val env = createEnv()
        environmentService.recordSnapshot(
            env.id, RecordSnapshotRequest(
                recordedBy = "ci-bot",
                artifacts = listOf(SnapshotArtifactRequest("sha256:aaa", "app", "v1.0", 1))
            )
        )
        environmentService.recordSnapshot(
            env.id, RecordSnapshotRequest(
                recordedBy = "ci-bot",
                artifacts = listOf(
                    SnapshotArtifactRequest("sha256:aaa", "app", "v1.0", 1),
                    SnapshotArtifactRequest("sha256:bbb", "new-service", "v1.0", 1)
                )
            )
        )

        val diff = environmentService.diffSnapshots(env.id, 1L, 2L)

        assertEquals(1, diff.added.size)
        assertEquals("new-service", diff.added[0].artifactName)
        assertNull(diff.added[0].sha256From)
        assertEquals("sha256:bbb", diff.added[0].sha256To)
        assertTrue(diff.removed.isEmpty())
        assertEquals(1, diff.unchanged.size)
        assertEquals("app", diff.unchanged[0].artifactName)
    }

    @Test
    fun `diffSnapshots shows artifact removed in second snapshot`() {
        val env = createEnv()
        environmentService.recordSnapshot(
            env.id, RecordSnapshotRequest(
                recordedBy = "ci-bot",
                artifacts = listOf(
                    SnapshotArtifactRequest("sha256:aaa", "app", "v1.0", 1),
                    SnapshotArtifactRequest("sha256:bbb", "old-service", "v1.0", 1)
                )
            )
        )
        environmentService.recordSnapshot(
            env.id, RecordSnapshotRequest(
                recordedBy = "ci-bot",
                artifacts = listOf(SnapshotArtifactRequest("sha256:aaa", "app", "v1.0", 1))
            )
        )

        val diff = environmentService.diffSnapshots(env.id, 1L, 2L)

        assertTrue(diff.added.isEmpty())
        assertEquals(1, diff.removed.size)
        assertEquals("old-service", diff.removed[0].artifactName)
        assertEquals("sha256:bbb", diff.removed[0].sha256From)
        assertNull(diff.removed[0].sha256To)
        assertEquals(1, diff.unchanged.size)
    }

    @Test
    fun `diffSnapshots shows artifact as updated when sha256 changes between snapshots`() {
        val env = createEnv()
        environmentService.recordSnapshot(
            env.id, RecordSnapshotRequest(
                recordedBy = "ci-bot",
                artifacts = listOf(SnapshotArtifactRequest("sha256:v1", "app", "v1.0", 1))
            )
        )
        environmentService.recordSnapshot(
            env.id, RecordSnapshotRequest(
                recordedBy = "ci-bot",
                artifacts = listOf(SnapshotArtifactRequest("sha256:v2", "app", "v2.0", 1))
            )
        )

        val diff = environmentService.diffSnapshots(env.id, 1L, 2L)

        assertEquals(1, diff.updated.size)
        assertEquals("app", diff.updated[0].artifactName)
        assertEquals("sha256:v1", diff.updated[0].sha256From)
        assertEquals("sha256:v2", diff.updated[0].sha256To)
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.removed.isEmpty())
        assertTrue(diff.unchanged.isEmpty())
    }

    @Test
    fun `diffSnapshots shows artifact as unchanged when only instance count changes`() {
        val env = createEnv()
        environmentService.recordSnapshot(
            env.id, RecordSnapshotRequest(
                recordedBy = "ci-bot",
                artifacts = listOf(SnapshotArtifactRequest("sha256:aaa", "app", "v1.0", instanceCount = 1))
            )
        )
        environmentService.recordSnapshot(
            env.id, RecordSnapshotRequest(
                recordedBy = "ci-bot",
                artifacts = listOf(SnapshotArtifactRequest("sha256:aaa", "app", "v1.0", instanceCount = 5))
            )
        )

        val diff = environmentService.diffSnapshots(env.id, 1L, 2L)

        assertEquals(1, diff.unchanged.size)
        assertEquals("app", diff.unchanged[0].artifactName)
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.removed.isEmpty())
        assertTrue(diff.updated.isEmpty())
    }

    @Test
    fun `diffSnapshots throws NotFoundException when fromSnapshot does not exist`() {
        val env = createEnv()
        environmentService.recordSnapshot(env.id, RecordSnapshotRequest(recordedBy = "ci-bot"))

        assertThrows<NotFoundException> {
            environmentService.diffSnapshots(env.id, 99L, 1L)
        }
    }

    @Test
    fun `diffSnapshots throws NotFoundException when toSnapshot does not exist`() {
        val env = createEnv()
        environmentService.recordSnapshot(env.id, RecordSnapshotRequest(recordedBy = "ci-bot"))

        assertThrows<NotFoundException> {
            environmentService.diffSnapshots(env.id, 1L, 99L)
        }
    }

    @Test
    fun `diffSnapshots on empty snapshots shows no differences`() {
        val env = createEnv()
        environmentService.recordSnapshot(env.id, RecordSnapshotRequest(recordedBy = "ci-bot"))
        environmentService.recordSnapshot(env.id, RecordSnapshotRequest(recordedBy = "ci-bot"))

        val diff = environmentService.diffSnapshots(env.id, 1L, 2L)

        assertTrue(diff.added.isEmpty())
        assertTrue(diff.removed.isEmpty())
        assertTrue(diff.updated.isEmpty())
        assertTrue(diff.unchanged.isEmpty())
    }
}
