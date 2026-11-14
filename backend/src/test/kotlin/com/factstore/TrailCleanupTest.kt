package com.factstore

import com.factstore.application.ArtifactService
import com.factstore.application.AttestationService
import com.factstore.application.FlowService
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.AuditEventType
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.inbound.ITrailCleanupService
import com.factstore.core.port.inbound.ITrailService
import com.factstore.dto.CleanupMode
import com.factstore.dto.CreateArtifactRequest
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.TrailCleanupRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * #161: obsolete trails and flows must be removable, with an explicit cascade, an audit
 * record, and a dry run for bulk cleanup. Soft delete (archive) is the default; hard
 * deletion is the deliberate, audited exception.
 */
@SpringBootTest
@Transactional
class TrailCleanupTest {

    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: ITrailService
    @Autowired lateinit var cleanupService: ITrailCleanupService
    @Autowired lateinit var attestationService: AttestationService
    @Autowired lateinit var artifactService: ArtifactService
    @Autowired lateinit var auditService: IAuditService
    @Autowired lateinit var objectMapper: ObjectMapper

    private fun newFlowId(vararg required: String): UUID =
        flowService.createFlow(CreateFlowRequest("flow-${System.nanoTime()}", "desc", required.toList())).id

    private fun newTrail(flowId: UUID, tags: Map<String, String> = emptyMap()) = trailService.createTrail(
        CreateTrailRequest(
            flowId = flowId,
            gitCommitSha = "commit-sha",
            gitBranch = "main",
            gitAuthor = "a",
            gitAuthorEmail = "a@example.com",
            tags = tags
        )
    )

    // --- Soft delete ------------------------------------------------------

    @Test
    fun `an archived trail drops out of the default listing but is still retrievable`() {
        val flowId = newFlowId("junit")
        val trail = newTrail(flowId)

        cleanupService.archiveTrail(trail.id)

        assertTrue(trailService.listTrails(flowId).none { it.id == trail.id })
        assertTrue(trailService.listTrails(flowId, includeArchived = true).any { it.id == trail.id })
        assertNotNull(trailService.getTrail(trail.id).archivedAt, "the evidence is retained, just hidden")
    }

    @Test
    fun `unarchiving brings a trail back`() {
        val flowId = newFlowId("junit")
        val trail = newTrail(flowId)

        cleanupService.archiveTrail(trail.id)
        cleanupService.unarchiveTrail(trail.id)

        assertNull(trailService.getTrail(trail.id).archivedAt)
        assertTrue(trailService.listTrails(flowId).any { it.id == trail.id })
    }

    @Test
    fun `archiving and unarchiving are audited`() {
        val flowId = newFlowId("junit")
        val trail = newTrail(flowId)

        cleanupService.archiveTrail(trail.id)
        cleanupService.unarchiveTrail(trail.id)

        assertTrue(auditEventsFor(trail.id).any { it == AuditEventType.TRAIL_ARCHIVED })
        assertTrue(auditEventsFor(trail.id).any { it == AuditEventType.TRAIL_UNARCHIVED })
    }

    private fun auditEventsFor(trailId: UUID): List<AuditEventType> =
        auditService.getEventsForTrail(trailId).map { it.eventType }

    // --- No hard delete (#245) -------------------------------------------

    @Test
    fun `there is no hard delete - bulk cleanup in DELETE mode is refused, even as a dry run`() {
        val flowId = newFlowId("junit")
        val trail = newTrail(flowId)

        for (dryRun in listOf(true, false)) {
            val error = assertThrows<com.factstore.exception.BadRequestException> {
                cleanupService.cleanup(TrailCleanupRequest(flowId = flowId, mode = CleanupMode.DELETE, dryRun = dryRun))
            }
            assertTrue(error.message!!.contains("ARCHIVE"))
        }
        assertNotNull(trailService.getTrail(trail.id))
    }

    @Test
    fun `the cascade can be inspected without removing anything`() {
        val flowId = newFlowId("junit")
        val trail = newTrail(flowId)
        attestationService.recordAttestation(
            trail.id,
            CreateAttestationRequest(type = "junit", status = AttestationStatus.PASSED)
        )

        val cascade = cleanupService.cascadeFor(trail.id)

        assertEquals(1, cascade.attestations)
        assertNotNull(trailService.getTrail(trail.id), "inspecting must not remove anything")
    }

    // --- Bulk cleanup -----------------------------------------------------

    @Test
    fun `a dry run reports what would be removed and removes nothing`() {
        val flowId = newFlowId("junit")
        newTrail(flowId)
        newTrail(flowId)

        val result = cleanupService.cleanup(
            TrailCleanupRequest(flowId = flowId, mode = CleanupMode.ARCHIVE, dryRun = true)
        )

        assertTrue(result.dryRun)
        assertEquals(2, result.trailCount)
        assertEquals(2, trailService.listTrails(flowId).size, "a dry run must not remove anything")
    }

    @Test
    fun `cleanup by flow archives every trail in that flow by default`() {
        val flowId = newFlowId("junit")
        newTrail(flowId)
        newTrail(flowId)
        val otherFlowId = newFlowId("junit")
        val untouched = newTrail(otherFlowId)

        val result = cleanupService.cleanup(TrailCleanupRequest(flowId = flowId, dryRun = false))

        assertEquals(CleanupMode.ARCHIVE, result.mode)
        assertEquals(2, result.trailCount)
        assertTrue(trailService.listTrails(flowId).isEmpty())
        assertTrue(trailService.listTrails(otherFlowId).any { it.id == untouched.id })
    }

    @Test
    fun `cleanup by tag selects only the tagged trails`() {
        val flowId = newFlowId("junit")
        val throwaway = newTrail(flowId, tags = mapOf("env" to "demo"))
        val keep = newTrail(flowId, tags = mapOf("env" to "prod"))

        val result = cleanupService.cleanup(
            TrailCleanupRequest(flowId = flowId, tagKey = "env", tagValue = "demo", dryRun = false)
        )

        assertEquals(1, result.trailCount)
        assertTrue(result.trailIds.contains(throwaway.id))
        assertTrue(trailService.listTrails(flowId).map { it.id }.contains(keep.id))
    }

    @Test
    fun `cleanup older-than does not touch trails newer than the cutoff`() {
        val flowId = newFlowId("junit")
        newTrail(flowId)

        val result = cleanupService.cleanup(
            TrailCleanupRequest(
                flowId = flowId,
                olderThan = Instant.now().minus(1, ChronoUnit.DAYS),
                dryRun = true
            )
        )

        assertEquals(0, result.trailCount)
    }

    @Test
    fun `cleanup requires at least one selector so it cannot wipe everything by accident`() {
        assertThrows<com.factstore.exception.BadRequestException> {
            cleanupService.cleanup(TrailCleanupRequest(dryRun = true))
        }
    }

    // --- Flow deletion ----------------------------------------------------

    @Test
    fun `deleting a flow that still has trails is refused`() {
        val flowId = newFlowId("junit")
        newTrail(flowId)

        val error = assertThrows<ConflictException> { flowService.deleteFlow(flowId) }

        assertTrue(error.message!!.contains("archive", ignoreCase = true), "the error must point at the safe path")
        assertNotNull(flowService.getFlow(flowId))
    }

    @Test
    fun `a flow with trails cannot be deleted even with force`() {
        val flowId = newFlowId("junit")
        newTrail(flowId)

        val error = assertThrows<ConflictException> { flowService.deleteFlow(flowId, force = true) }

        assertTrue(error.message!!.contains("archive", ignoreCase = true))
        assertNotNull(flowService.getFlow(flowId))
    }

    @Test
    fun `an empty flow deletes without ceremony and is audited`() {
        val flowId = newFlowId("junit")

        flowService.deleteFlow(flowId)

        assertThrows<NotFoundException> { flowService.getFlow(flowId) }
        val deleted = auditService.queryEvents(eventType = AuditEventType.FLOW_DELETED, page = 0, size = 50)
            .events
            .any { objectMapper.readValue(it.payload, Map::class.java)["flowId"] == flowId.toString() }
        assertTrue(deleted)
    }

    @Test
    fun `an archived trail is excluded from a flow trail listing but not lost`() {
        val flowId = newFlowId("junit")
        val trail = newTrail(flowId)
        cleanupService.archiveTrail(trail.id)

        assertFalse(trailService.listTrailsForFlow(flowId).any { it.id == trail.id })
        assertTrue(trailService.listTrailsForFlow(flowId, includeArchived = true).any { it.id == trail.id })
    }
}
