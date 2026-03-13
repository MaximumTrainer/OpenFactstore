package com.factstore

import com.factstore.application.EnvironmentService
import com.factstore.core.domain.EnvironmentType
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.dto.RecordSnapshotRequest
import com.factstore.dto.SnapshotArtifactRequest
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class EnvironmentServiceTest {

    @Autowired
    lateinit var environmentService: EnvironmentService

    @Test
    fun `create environment succeeds`() {
        val resp = environmentService.createEnvironment(
            CreateEnvironmentRequest("prod", EnvironmentType.K8S, "Production cluster")
        )
        assertEquals("prod", resp.name)
        assertEquals(EnvironmentType.K8S, resp.type)
        assertEquals("Production cluster", resp.description)
        assertNotNull(resp.id)
    }

    @Test
    fun `list environments returns all created`() {
        environmentService.createEnvironment(CreateEnvironmentRequest("env-a", EnvironmentType.K8S))
        environmentService.createEnvironment(CreateEnvironmentRequest("env-b", EnvironmentType.S3))
        val envs = environmentService.listEnvironments()
        assertTrue(envs.size >= 2)
    }

    @Test
    fun `get environment by unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            environmentService.getEnvironment(java.util.UUID.randomUUID())
        }
    }

    @Test
    fun `record snapshot assigns incrementing snapshotIndex`() {
        val env = environmentService.createEnvironment(
            CreateEnvironmentRequest("snap-env", EnvironmentType.K8S)
        )
        val req = RecordSnapshotRequest(
            recordedBy = "ci-bot",
            artifacts = listOf(SnapshotArtifactRequest("sha256:abc", "my-app", "v1.0", 2))
        )
        val snap1 = environmentService.recordSnapshot(env.id, req)
        val snap2 = environmentService.recordSnapshot(env.id, req)
        assertEquals(1L, snap1.snapshotIndex)
        assertEquals(2L, snap2.snapshotIndex)
    }

    @Test
    fun `record snapshot stores artifacts correctly`() {
        val env = environmentService.createEnvironment(
            CreateEnvironmentRequest("artifact-env", EnvironmentType.K8S)
        )
        val snap = environmentService.recordSnapshot(
            env.id,
            RecordSnapshotRequest(
                recordedBy = "ci-bot",
                artifacts = listOf(
                    SnapshotArtifactRequest("sha256:abc", "app", "v1", 3),
                    SnapshotArtifactRequest("sha256:def", "db", "v2", 1)
                )
            )
        )
        assertEquals(2, snap.artifacts.size)
        assertEquals(3, snap.artifacts.first { it.artifactSha256 == "sha256:abc" }.instanceCount)
    }

    @Test
    fun `list snapshots returns snapshots in ascending index order`() {
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
    fun `get latest snapshot returns highest index`() {
        val env = environmentService.createEnvironment(
            CreateEnvironmentRequest("latest-env", EnvironmentType.K8S)
        )
        val req = RecordSnapshotRequest(recordedBy = "ci-bot")
        repeat(3) { environmentService.recordSnapshot(env.id, req) }
        val latest = environmentService.getLatestSnapshot(env.id)
        assertEquals(3L, latest.snapshotIndex)
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
        repeat(3) { environmentService.recordSnapshot(env.id, req) }
        val snap = environmentService.getSnapshot(env.id, 2L)
        assertEquals(2L, snap.snapshotIndex)
    }

    @Test
    fun `get snapshot by non-existent index throws NotFoundException`() {
        val env = environmentService.createEnvironment(
            CreateEnvironmentRequest("miss-env", EnvironmentType.K8S)
        )
        assertThrows<NotFoundException> { environmentService.getSnapshot(env.id, 99L) }
    }
}
