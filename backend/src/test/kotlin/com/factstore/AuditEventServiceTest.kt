package com.factstore

import com.factstore.application.AuditEventService
import com.factstore.application.FlowService
import com.factstore.application.TrailService
import com.factstore.application.AttestationService
import com.factstore.application.ArtifactService
import com.factstore.application.AssertService
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.AttestationStatus
import com.factstore.dto.*
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
class AuditEventServiceTest {

    @Autowired lateinit var auditEventService: AuditEventService
    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: TrailService
    @Autowired lateinit var attestationService: AttestationService
    @Autowired lateinit var artifactService: ArtifactService
    @Autowired lateinit var assertService: AssertService

    @Test
    fun `record audit event manually and retrieve by id`() {
        val event = auditEventService.record(
            eventType = AuditEventType.ATTESTATION_RECORDED,
            actor = "test-user",
            payload = mapOf("trailId" to "some-trail", "type" to "junit")
        )
        assertNotNull(event.id)
        assertEquals(AuditEventType.ATTESTATION_RECORDED, event.eventType)
        assertEquals("test-user", event.actor)
        assertTrue(event.payload.contains("junit"))

        val fetched = auditEventService.getEvent(event.id)
        assertEquals(event.id, fetched.id)
    }

    @Test
    fun `get non-existent audit event throws NotFoundException`() {
        assertThrows<NotFoundException> {
            auditEventService.getEvent(UUID.randomUUID())
        }
    }

    @Test
    fun `record attestation emits ATTESTATION_RECORDED audit event`() {
        val flow = flowService.createFlow(CreateFlowRequest("flow-audit-${System.nanoTime()}", "desc", listOf("junit")))
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id,
            gitCommitSha = "abc",
            gitBranch = "main",
            gitAuthor = "author",
            gitAuthorEmail = "a@b.com"
        ))
        attestationService.recordAttestation(trail.id, CreateAttestationRequest("junit", AttestationStatus.PASSED))

        val events = auditEventService.getEventsForTrail(trail.id)
        assertTrue(events.any { it.eventType == AuditEventType.ATTESTATION_RECORDED })
        assertTrue(events.any { it.trailId == trail.id })
    }

    @Test
    fun `report artifact emits ARTIFACT_DEPLOYED audit event`() {
        val flow = flowService.createFlow(CreateFlowRequest("flow-art-${System.nanoTime()}", "desc"))
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id,
            gitCommitSha = "abc",
            gitBranch = "main",
            gitAuthor = "author",
            gitAuthorEmail = "a@b.com"
        ))
        val digest = "sha256:${System.nanoTime()}"
        artifactService.reportArtifact(trail.id, CreateArtifactRequest("img", "1.0", digest, reportedBy = "ci"))

        val events = auditEventService.getEventsForTrail(trail.id)
        val deployEvent = events.find { it.eventType == AuditEventType.ARTIFACT_DEPLOYED }
        assertNotNull(deployEvent)
        assertEquals(digest, deployEvent!!.artifactSha256)
    }

    @Test
    fun `assert compliance emits GATE_ALLOWED for compliant artifact`() {
        val flow = flowService.createFlow(CreateFlowRequest("flow-gate-${System.nanoTime()}", "desc", listOf("junit")))
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id,
            gitCommitSha = "abc",
            gitBranch = "main",
            gitAuthor = "author",
            gitAuthorEmail = "a@b.com"
        ))
        attestationService.recordAttestation(trail.id, CreateAttestationRequest("junit", AttestationStatus.PASSED))
        val digest = "sha256:gate-${System.nanoTime()}"
        artifactService.reportArtifact(trail.id, CreateArtifactRequest("img", "1.0", digest, reportedBy = "ci"))

        assertService.assertCompliance(AssertRequest(digest, flow.id))

        val events = auditEventService.queryEvents(eventType = AuditEventType.GATE_ALLOWED)
        assertTrue(events.events.any { it.artifactSha256 == digest })
    }

    @Test
    fun `assert compliance emits GATE_BLOCKED for non-compliant artifact`() {
        val flow = flowService.createFlow(CreateFlowRequest("flow-blocked-${System.nanoTime()}", "desc", listOf("junit")))
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id,
            gitCommitSha = "abc",
            gitBranch = "main",
            gitAuthor = "author",
            gitAuthorEmail = "a@b.com"
        ))
        val digest = "sha256:blocked-${System.nanoTime()}"
        artifactService.reportArtifact(trail.id, CreateArtifactRequest("img", "1.0", digest, reportedBy = "ci"))
        // No attestations recorded - should be non-compliant

        assertService.assertCompliance(AssertRequest(digest, flow.id))

        val events = auditEventService.queryEvents(eventType = AuditEventType.GATE_BLOCKED)
        assertTrue(events.events.any { it.artifactSha256 == digest })
    }

    @Test
    fun `query events with filters returns matching events`() {
        auditEventService.record(
            eventType = AuditEventType.ENVIRONMENT_CREATED,
            actor = "alice",
            payload = mapOf("env" to "production")
        )
        auditEventService.record(
            eventType = AuditEventType.ENVIRONMENT_DELETED,
            actor = "bob",
            payload = mapOf("env" to "staging")
        )

        val aliceEvents = auditEventService.queryEvents(actor = "alice")
        assertTrue(aliceEvents.events.all { it.actor == "alice" })

        val envCreated = auditEventService.queryEvents(eventType = AuditEventType.ENVIRONMENT_CREATED)
        assertTrue(envCreated.events.all { it.eventType == AuditEventType.ENVIRONMENT_CREATED })
    }

    @Test
    fun `query events pagination works`() {
        repeat(5) {
            auditEventService.record(
                eventType = AuditEventType.POLICY_EVALUATED,
                actor = "paginator",
                payload = mapOf("i" to it)
            )
        }
        val page0 = auditEventService.queryEvents(actor = "paginator", page = 0, size = 2)
        assertEquals(2, page0.events.size)
        assertTrue(page0.totalElements >= 5)
        assertTrue(page0.totalPages >= 3)
    }

    @Test
    fun `get events for trail returns trail-scoped events only`() {
        val flow = flowService.createFlow(CreateFlowRequest("flow-trail-${System.nanoTime()}", "desc"))
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id,
            gitCommitSha = "abc",
            gitBranch = "main",
            gitAuthor = "author",
            gitAuthorEmail = "a@b.com"
        ))
        val trailId = trail.id
        auditEventService.record(
            eventType = AuditEventType.ATTESTATION_RECORDED,
            actor = "system",
            payload = mapOf("trailId" to trailId.toString()),
            trailId = trailId
        )

        val events = auditEventService.getEventsForTrail(trailId)
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.trailId == trailId })
    }
}
