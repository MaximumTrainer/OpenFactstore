package com.factstore.core

import com.factstore.adapter.mock.InMemoryEnvironmentRepository
import com.factstore.adapter.mock.InMemoryEnvironmentSnapshotRepository
import com.factstore.adapter.mock.InMemorySnapshotArtifactRepository
import com.factstore.application.EnvironmentService
import com.factstore.core.domain.EnvironmentType
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
import java.util.UUID

class EnvironmentServiceUnitTest {

    private lateinit var environmentService: EnvironmentService

    @BeforeEach
    fun setUp() {
        environmentService = EnvironmentService(
            InMemoryEnvironmentRepository(),
            InMemoryEnvironmentSnapshotRepository(),
            InMemorySnapshotArtifactRepository()
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
