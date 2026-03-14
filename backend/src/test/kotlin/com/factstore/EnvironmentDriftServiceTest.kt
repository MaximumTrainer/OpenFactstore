package com.factstore

import com.factstore.application.EnvironmentService
import com.factstore.core.domain.DriftPolicy
import com.factstore.core.domain.EnvironmentType
import com.factstore.dto.CreateBaselineRequest
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
class EnvironmentDriftServiceTest {

    @Autowired
    lateinit var environmentService: EnvironmentService

    private fun createEnv(name: String = "drift-env-${System.nanoTime()}") =
        environmentService.createEnvironment(
            CreateEnvironmentRequest(name, EnvironmentType.K8S, "Test environment")
        )

    private fun snapshot(envId: java.util.UUID, vararg artifacts: SnapshotArtifactRequest) =
        environmentService.recordSnapshot(
            envId,
            RecordSnapshotRequest("ci-bot", artifacts.toList())
        )

    @Test
    fun `diffSnapshots returns added artifacts`() {
        val env = createEnv()
        snapshot(env.id, SnapshotArtifactRequest("sha:aaa", "app-a", "v1"))
        snapshot(env.id, SnapshotArtifactRequest("sha:aaa", "app-a", "v1"), SnapshotArtifactRequest("sha:bbb", "app-b", "v1"))

        val diff = environmentService.diffSnapshots(env.id, 1L, 2L)

        assertEquals(env.id, diff.environmentId)
        assertEquals(1L, diff.fromSnapshotIndex)
        assertEquals(2L, diff.toSnapshotIndex)
        assertEquals(1, diff.added.size)
        assertEquals("app-b", diff.added[0].artifactName)
        assertEquals("sha:bbb", diff.added[0].sha256To)
        assertNull(diff.added[0].sha256From)
        assertTrue(diff.removed.isEmpty())
        assertTrue(diff.updated.isEmpty())
        assertEquals(1, diff.unchanged.size)
    }

    @Test
    fun `diffSnapshots returns removed artifacts`() {
        val env = createEnv()
        snapshot(env.id, SnapshotArtifactRequest("sha:aaa", "app-a", "v1"), SnapshotArtifactRequest("sha:bbb", "app-b", "v1"))
        snapshot(env.id, SnapshotArtifactRequest("sha:aaa", "app-a", "v1"))

        val diff = environmentService.diffSnapshots(env.id, 1L, 2L)

        assertTrue(diff.added.isEmpty())
        assertEquals(1, diff.removed.size)
        assertEquals("app-b", diff.removed[0].artifactName)
        assertEquals("sha:bbb", diff.removed[0].sha256From)
        assertNull(diff.removed[0].sha256To)
        assertTrue(diff.updated.isEmpty())
    }

    @Test
    fun `diffSnapshots returns updated artifacts`() {
        val env = createEnv()
        snapshot(env.id, SnapshotArtifactRequest("sha:v1", "app-a", "v1"))
        snapshot(env.id, SnapshotArtifactRequest("sha:v2", "app-a", "v2"))

        val diff = environmentService.diffSnapshots(env.id, 1L, 2L)

        assertTrue(diff.added.isEmpty())
        assertTrue(diff.removed.isEmpty())
        assertEquals(1, diff.updated.size)
        assertEquals("app-a", diff.updated[0].artifactName)
        assertEquals("sha:v1", diff.updated[0].sha256From)
        assertEquals("sha:v2", diff.updated[0].sha256To)
        assertTrue(diff.unchanged.isEmpty())
    }

    @Test
    fun `diffSnapshots throws NotFoundException for missing snapshot`() {
        val env = createEnv()
        snapshot(env.id, SnapshotArtifactRequest("sha:aaa", "app-a", "v1"))

        assertThrows<NotFoundException> {
            environmentService.diffSnapshots(env.id, 1L, 99L)
        }
    }

    @Test
    fun `createBaseline sets active baseline for environment`() {
        val env = createEnv()
        snapshot(env.id, SnapshotArtifactRequest("sha:aaa", "app-a", "v1"))

        val baseline = environmentService.createBaseline(env.id, CreateBaselineRequest(approvedBy = "admin"))

        assertEquals(env.id, baseline.environmentId)
        assertEquals("admin", baseline.approvedBy)
        assertTrue(baseline.isActive)
        assertNotNull(baseline.snapshotId)
    }

    @Test
    fun `createBaseline deactivates previous baseline`() {
        val env = createEnv()
        snapshot(env.id, SnapshotArtifactRequest("sha:aaa", "app-a", "v1"))

        val first = environmentService.createBaseline(env.id, CreateBaselineRequest(approvedBy = "admin"))
        val second = environmentService.createBaseline(env.id, CreateBaselineRequest(approvedBy = "admin2"))

        assertTrue(second.isActive)
        // Current baseline should be the second one
        val current = environmentService.getCurrentBaseline(env.id)
        assertEquals(second.id, current.id)
        assertEquals("admin2", current.approvedBy)
    }

    @Test
    fun `getCurrentBaseline throws when no baseline exists`() {
        val env = createEnv()

        assertThrows<NotFoundException> {
            environmentService.getCurrentBaseline(env.id)
        }
    }

    @Test
    fun `checkDrift detects no drift when current snapshot matches baseline`() {
        val env = createEnv()
        val artifacts = listOf(SnapshotArtifactRequest("sha:aaa", "app-a", "v1"))
        snapshot(env.id, *artifacts.toTypedArray())
        environmentService.createBaseline(env.id, CreateBaselineRequest(approvedBy = "admin"))

        val report = environmentService.checkDrift(env.id)

        assertFalse(report.hasDrift)
        assertTrue(report.added.isEmpty())
        assertTrue(report.removed.isEmpty())
        assertTrue(report.updated.isEmpty())
    }

    @Test
    fun `checkDrift detects drift when artifacts change`() {
        val env = createEnv()
        snapshot(env.id, SnapshotArtifactRequest("sha:v1", "app-a", "v1"))
        environmentService.createBaseline(env.id, CreateBaselineRequest(approvedBy = "admin"))
        // Record a new snapshot with changed artifact
        snapshot(env.id, SnapshotArtifactRequest("sha:v2", "app-a", "v2"))

        val report = environmentService.checkDrift(env.id)

        assertTrue(report.hasDrift)
        assertEquals(1, report.updated.size)
        assertEquals("app-a", report.updated[0].artifactName)
        assertEquals("sha:v1", report.updated[0].sha256From)
        assertEquals("sha:v2", report.updated[0].sha256To)
    }

    @Test
    fun `checkDrift throws when no active baseline`() {
        val env = createEnv()
        snapshot(env.id, SnapshotArtifactRequest("sha:aaa", "app-a", "v1"))

        assertThrows<NotFoundException> {
            environmentService.checkDrift(env.id)
        }
    }

    @Test
    fun `listDriftHistory returns all reports`() {
        val env = createEnv()
        snapshot(env.id, SnapshotArtifactRequest("sha:v1", "app-a", "v1"))
        environmentService.createBaseline(env.id, CreateBaselineRequest(approvedBy = "admin"))
        environmentService.checkDrift(env.id)
        snapshot(env.id, SnapshotArtifactRequest("sha:v2", "app-a", "v2"))
        environmentService.checkDrift(env.id)

        val history = environmentService.listDriftHistory(env.id)

        assertEquals(2, history.size)
    }

    @Test
    fun `createEnvironment respects driftPolicy`() {
        val env = environmentService.createEnvironment(
            CreateEnvironmentRequest("policy-env-${System.nanoTime()}", EnvironmentType.K8S, driftPolicy = DriftPolicy.BLOCK)
        )
        assertEquals(DriftPolicy.BLOCK, env.driftPolicy)
    }
}
