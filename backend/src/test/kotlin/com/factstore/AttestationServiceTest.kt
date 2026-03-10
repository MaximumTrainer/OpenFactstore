package com.factstore

import com.factstore.domain.AttestationStatus
import com.factstore.domain.TrailStatus
import com.factstore.dto.*
import com.factstore.repository.TrailRepository
import com.factstore.service.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class AttestationServiceTest {

    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: TrailService
    @Autowired lateinit var attestationService: AttestationService
    @Autowired lateinit var trailRepository: TrailRepository

    private fun setupTrail(): UUID {
        val flow = flowService.createFlow(CreateFlowRequest("flow-att-${System.nanoTime()}", "desc", listOf("junit")))
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id,
            gitCommitSha = "abc",
            gitBranch = "main",
            gitAuthor = "author",
            gitAuthorEmail = "a@b.com"
        ))
        return trail.id
    }

    @Test
    fun `record PASSED attestation succeeds`() {
        val trailId = setupTrail()
        val resp = attestationService.recordAttestation(trailId, CreateAttestationRequest("junit", AttestationStatus.PASSED))
        assertEquals("junit", resp.type)
        assertEquals(AttestationStatus.PASSED, resp.status)
        assertEquals(trailId, resp.trailId)
    }

    @Test
    fun `record FAILED attestation marks trail NON_COMPLIANT`() {
        val trailId = setupTrail()
        attestationService.recordAttestation(trailId, CreateAttestationRequest("snyk", AttestationStatus.FAILED))
        val trail = trailRepository.findById(trailId).get()
        assertEquals(TrailStatus.NON_COMPLIANT, trail.status)
    }

    @Test
    fun `list attestations for trail`() {
        val trailId = setupTrail()
        attestationService.recordAttestation(trailId, CreateAttestationRequest("junit", AttestationStatus.PASSED))
        attestationService.recordAttestation(trailId, CreateAttestationRequest("snyk", AttestationStatus.PASSED))
        val list = attestationService.listAttestations(trailId)
        assertEquals(2, list.size)
    }

    @Test
    fun `upload evidence updates attestation hash`() {
        val trailId = setupTrail()
        val att = attestationService.recordAttestation(trailId, CreateAttestationRequest("junit", AttestationStatus.PASSED))
        val content = "test evidence content".toByteArray()
        val ev = attestationService.uploadEvidence(trailId, att.id, "report.txt", "text/plain", content)
        assertNotNull(ev.sha256Hash)
        assertTrue(ev.sha256Hash.length == 64) // SHA256 hex
        assertEquals("report.txt", ev.fileName)
        assertEquals(content.size.toLong(), ev.fileSizeBytes)
    }

    @Test
    fun `record attestation for unknown trail throws NotFoundException`() {
        org.junit.jupiter.api.assertThrows<com.factstore.exception.NotFoundException> {
            attestationService.recordAttestation(UUID.randomUUID(), CreateAttestationRequest("junit", AttestationStatus.PASSED))
        }
    }
}
