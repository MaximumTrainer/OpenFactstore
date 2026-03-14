package com.factstore

import com.factstore.application.LogicalEnvironmentService
import com.factstore.core.domain.EnvironmentType
import com.factstore.dto.ComplianceStatus
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.dto.CreateLogicalEnvironmentRequest
import com.factstore.dto.RecordSnapshotRequest
import com.factstore.dto.SnapshotArtifactRequest
import com.factstore.dto.CreateLogicalEnvironmentRequest
import com.factstore.dto.UpdateLogicalEnvironmentRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class LogicalEnvironmentServiceTest {

    @Autowired
    lateinit var logicalEnvironmentService: LogicalEnvironmentService

    @Autowired
    lateinit var environmentService: com.factstore.application.EnvironmentService

    @Test
    fun `create logical environment succeeds`() {
        val resp = logicalEnvironmentService.createLogicalEnvironment(
            CreateLogicalEnvironmentRequest("prod-logical", "Production logical environment")
        )
        assertEquals("prod-logical", resp.name)
        assertEquals("Production logical environment", resp.description)
        assertNotNull(resp.id)
        assertTrue(resp.members.isEmpty())
    @Test
    fun `create logical environment succeeds`() {
        val req = CreateLogicalEnvironmentRequest("prod-group", "All production envs")
        val resp = logicalEnvironmentService.createLogicalEnvironment(req)
        assertEquals("prod-group", resp.name)
        assertEquals("All production envs", resp.description)
        assertNotNull(resp.id)
    }

    @Test
    fun `create logical environment with duplicate name throws ConflictException`() {
        logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("dup-env"))
        assertThrows<ConflictException> {
            logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("dup-env"))
        logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("dup-le"))
        assertThrows<ConflictException> {
            logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("dup-le"))
        }
    }

    @Test
    fun `list logical environments returns all created`() {
        logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("lenv-a"))
        logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("lenv-b"))
        val envs = logicalEnvironmentService.listLogicalEnvironments()
        assertTrue(envs.size >= 2)
    }

    @Test
    fun `get logical environment by unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            logicalEnvironmentService.getLogicalEnvironment(java.util.UUID.randomUUID())
    fun `get logical environment by unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            logicalEnvironmentService.getLogicalEnvironment(UUID.randomUUID())
        }
    }

    @Test
    fun `update logical environment name and description`() {
        val created = logicalEnvironmentService.createLogicalEnvironment(
            CreateLogicalEnvironmentRequest("update-me", "original desc")
        )
        val updated = logicalEnvironmentService.updateLogicalEnvironment(
            created.id,
            UpdateLogicalEnvironmentRequest(name = "updated-name", description = "new desc")
        )
        assertEquals("updated-name", updated.name)
        assertEquals("new desc", updated.description)
    }

    @Test
    fun `delete logical environment removes it`() {
        val created = logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("delete-me"))
        logicalEnvironmentService.deleteLogicalEnvironment(created.id)
        assertThrows<NotFoundException> {
            logicalEnvironmentService.getLogicalEnvironment(created.id)
        }
    }

    @Test
    fun `add member to logical environment succeeds`() {
        val physicalEnv = environmentService.createEnvironment(
            CreateEnvironmentRequest("phys-env-1", EnvironmentType.K8S)
        )
        val logicalEnv = logicalEnvironmentService.createLogicalEnvironment(
            CreateLogicalEnvironmentRequest("logical-with-member")
        )
        val updated = logicalEnvironmentService.addMember(logicalEnv.id, physicalEnv.id)
        assertEquals(1, updated.members.size)
        assertEquals(physicalEnv.id, updated.members[0].physicalEnvId)
        assertEquals(physicalEnv.name, updated.members[0].physicalEnvName)
    }

    @Test
    fun `add non-existent physical environment throws NotFoundException`() {
        val logicalEnv = logicalEnvironmentService.createLogicalEnvironment(
            CreateLogicalEnvironmentRequest("logical-no-phys")
        )
        assertThrows<NotFoundException> {
            logicalEnvironmentService.addMember(logicalEnv.id, java.util.UUID.randomUUID())
        }
    }

    @Test
    fun `add duplicate member throws ConflictException`() {
        val physicalEnv = environmentService.createEnvironment(
            CreateEnvironmentRequest("phys-dup", EnvironmentType.S3)
        )
        val logicalEnv = logicalEnvironmentService.createLogicalEnvironment(
            CreateLogicalEnvironmentRequest("logical-dup-member")
        )
        logicalEnvironmentService.addMember(logicalEnv.id, physicalEnv.id)
        assertThrows<ConflictException> {
            logicalEnvironmentService.addMember(logicalEnv.id, physicalEnv.id)
        }
    }

    @Test
    fun `remove member from logical environment succeeds`() {
        val physicalEnv = environmentService.createEnvironment(
            CreateEnvironmentRequest("phys-remove", EnvironmentType.LAMBDA)
        )
        val logicalEnv = logicalEnvironmentService.createLogicalEnvironment(
            CreateLogicalEnvironmentRequest("logical-remove-member")
        )
        logicalEnvironmentService.addMember(logicalEnv.id, physicalEnv.id)
        logicalEnvironmentService.removeMember(logicalEnv.id, physicalEnv.id)
        val result = logicalEnvironmentService.getLogicalEnvironment(logicalEnv.id)
        assertTrue(result.members.isEmpty())
    }

    @Test
    fun `remove non-member throws NotFoundException`() {
        val logicalEnv = logicalEnvironmentService.createLogicalEnvironment(
            CreateLogicalEnvironmentRequest("logical-no-remove")
        )
        assertThrows<NotFoundException> {
            logicalEnvironmentService.removeMember(logicalEnv.id, java.util.UUID.randomUUID())
        }
    }

    @Test
    fun `merged snapshot for empty logical environment has NON_COMPLIANT status`() {
        val logicalEnv = logicalEnvironmentService.createLogicalEnvironment(
            CreateLogicalEnvironmentRequest("empty-logical")
        )
        val merged = logicalEnvironmentService.getMergedSnapshot(logicalEnv.id)
        assertEquals(ComplianceStatus.NON_COMPLIANT, merged.complianceStatus)
        assertTrue(merged.mergedArtifacts.isEmpty())
        assertTrue(merged.memberSnapshots.isEmpty())
    }

    @Test
    fun `merged snapshot returns union of artifacts from all members`() {
        val env1 = environmentService.createEnvironment(
            CreateEnvironmentRequest("merge-env-1", EnvironmentType.K8S)
        )
        val env2 = environmentService.createEnvironment(
            CreateEnvironmentRequest("merge-env-2", EnvironmentType.S3)
        )
        environmentService.recordSnapshot(
            env1.id,
            RecordSnapshotRequest(
                recordedBy = "ci-bot",
                artifacts = listOf(SnapshotArtifactRequest("sha256:aaa", "app", "v1", 1))
            )
        )
        environmentService.recordSnapshot(
            env2.id,
            RecordSnapshotRequest(
                recordedBy = "ci-bot",
                artifacts = listOf(SnapshotArtifactRequest("sha256:bbb", "db", "v2", 2))
            )
        )
        val logicalEnv = logicalEnvironmentService.createLogicalEnvironment(
            CreateLogicalEnvironmentRequest("merged-logical")
        )
        logicalEnvironmentService.addMember(logicalEnv.id, env1.id)
        logicalEnvironmentService.addMember(logicalEnv.id, env2.id)

        val merged = logicalEnvironmentService.getMergedSnapshot(logicalEnv.id)
        assertEquals(ComplianceStatus.COMPLIANT, merged.complianceStatus)
        assertEquals(2, merged.mergedArtifacts.size)
        assertEquals(2, merged.memberSnapshots.size)
        assertTrue(merged.mergedArtifacts.any { it.artifactSha256 == "sha256:aaa" })
        assertTrue(merged.mergedArtifacts.any { it.artifactSha256 == "sha256:bbb" })
    }

    @Test
    fun `merged snapshot is NON_COMPLIANT when any member has no snapshot`() {
        val env1 = environmentService.createEnvironment(
            CreateEnvironmentRequest("noncompliant-env-1", EnvironmentType.K8S)
        )
        val env2 = environmentService.createEnvironment(
            CreateEnvironmentRequest("noncompliant-env-2", EnvironmentType.S3)
        )
        // Only env1 has a snapshot
        environmentService.recordSnapshot(
            env1.id,
            RecordSnapshotRequest(recordedBy = "ci-bot")
        )
        val logicalEnv = logicalEnvironmentService.createLogicalEnvironment(
            CreateLogicalEnvironmentRequest("noncompliant-logical")
        )
        logicalEnvironmentService.addMember(logicalEnv.id, env1.id)
        logicalEnvironmentService.addMember(logicalEnv.id, env2.id)

        val merged = logicalEnvironmentService.getMergedSnapshot(logicalEnv.id)
        assertEquals(ComplianceStatus.NON_COMPLIANT, merged.complianceStatus)
    }

    @Test
    fun `merged snapshot member summaries include physical env names`() {
        val env1 = environmentService.createEnvironment(
            CreateEnvironmentRequest("named-env-1", EnvironmentType.K8S)
        )
        environmentService.recordSnapshot(env1.id, RecordSnapshotRequest(recordedBy = "ci-bot"))

        val logicalEnv = logicalEnvironmentService.createLogicalEnvironment(
            CreateLogicalEnvironmentRequest("named-logical")
        )
        logicalEnvironmentService.addMember(logicalEnv.id, env1.id)

        val merged = logicalEnvironmentService.getMergedSnapshot(logicalEnv.id)
        assertEquals(1, merged.memberSnapshots.size)
        assertEquals("named-env-1", merged.memberSnapshots[0].physicalEnvName)
        assertEquals(1L, merged.memberSnapshots[0].snapshotIndex)
    fun `list logical environments returns all`() {
        logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("le-a"))
        logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("le-b"))
        val envs = logicalEnvironmentService.listLogicalEnvironments()
        assertTrue(envs.size >= 2)
    }

    @Test
    fun `update logical environment updates fields`() {
        val created = logicalEnvironmentService.createLogicalEnvironment(
            CreateLogicalEnvironmentRequest("le-upd", "old desc")
        )
        val updated = logicalEnvironmentService.updateLogicalEnvironment(
            created.id, UpdateLogicalEnvironmentRequest(description = "new desc")
        )
        assertEquals("new desc", updated.description)
        assertEquals("le-upd", updated.name)
    }

    @Test
    fun `update logical environment with duplicate name throws ConflictException`() {
        logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("le-existing"))
        val second = logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("le-second"))
        assertThrows<ConflictException> {
            logicalEnvironmentService.updateLogicalEnvironment(
                second.id, UpdateLogicalEnvironmentRequest(name = "le-existing")
            )
        }
    }

    @Test
    fun `delete logical environment removes it`() {
        val created = logicalEnvironmentService.createLogicalEnvironment(CreateLogicalEnvironmentRequest("le-del"))
        logicalEnvironmentService.deleteLogicalEnvironment(created.id)
        assertThrows<NotFoundException> { logicalEnvironmentService.getLogicalEnvironment(created.id) }
    }

    @Test
    fun `delete non-existent logical environment throws NotFoundException`() {
        assertThrows<NotFoundException> { logicalEnvironmentService.deleteLogicalEnvironment(UUID.randomUUID()) }
    }
}
