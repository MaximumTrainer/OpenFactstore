package com.factstore

import com.factstore.testsupport.recordWithEvidence
import com.factstore.application.ArtifactService
import com.factstore.application.AssertService
import com.factstore.application.AttestationService
import com.factstore.application.FlowService
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.inbound.ITrailService
import com.factstore.dto.AssertRequest
import com.factstore.dto.ComplianceStatus
import com.factstore.dto.CreateArtifactRequest
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.FlowResponse
import com.factstore.dto.TrailResponse
import com.factstore.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Covers #158 (trail status must follow the assertion outcome) and
 * #163 (an assertion must be scoped to the execution being judged).
 */
@SpringBootTest
@Transactional
class AssertTrailScopeTest {

    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: ITrailService
    @Autowired lateinit var attestationService: AttestationService
    @Autowired lateinit var artifactService: ArtifactService
    @Autowired lateinit var assertService: AssertService

    private fun newFlow(vararg required: String): FlowResponse =
        flowService.createFlow(CreateFlowRequest("flow-${System.nanoTime()}", "desc", required.toList()))

    private fun newTrail(flowId: UUID): TrailResponse = trailService.createTrail(
        CreateTrailRequest(
            flowId = flowId,
            gitCommitSha = "commit-sha",
            gitBranch = "main",
            gitAuthor = "a",
            gitAuthorEmail = "a@example.com"
        )
    )

    private fun attest(trailId: UUID, type: String, status: AttestationStatus) {
        attestationService.recordWithEvidence(trailId, CreateAttestationRequest(type = type, status = status))
    }

    private fun registerArtifact(trailId: UUID, digest: String) {
        artifactService.reportArtifact(
            trailId,
            CreateArtifactRequest("myimage", "latest", digest, reportedBy = "ci")
        )
    }

    // --- #158 ------------------------------------------------------------

    @Test
    fun `a COMPLIANT assertion flips the trail status to COMPLIANT`() {
        val flow = newFlow("junit")
        val trail = newTrail(flow.id)
        attest(trail.id, "junit", AttestationStatus.PASSED)
        val digest = "sha256:c${System.nanoTime()}"
        registerArtifact(trail.id, digest)

        val result = assertService.assertCompliance(AssertRequest(digest, flow.id))

        assertEquals(ComplianceStatus.COMPLIANT, result.status)
        assertEquals(TrailStatus.COMPLIANT, trailService.getTrail(trail.id).status)
    }

    @Test
    fun `a NON_COMPLIANT assertion flips the trail status to NON_COMPLIANT`() {
        val flow = newFlow("junit", "snyk")
        val trail = newTrail(flow.id)
        attest(trail.id, "junit", AttestationStatus.PASSED)
        val digest = "sha256:n${System.nanoTime()}"
        registerArtifact(trail.id, digest)

        val result = assertService.assertCompliance(AssertRequest(digest, flow.id))

        assertEquals(ComplianceStatus.NON_COMPLIANT, result.status)
        assertEquals(TrailStatus.NON_COMPLIANT, trailService.getTrail(trail.id).status)
    }

    @Test
    fun `a trail moves back to COMPLIANT once the outstanding attestation lands`() {
        val flow = newFlow("junit", "snyk")
        val trail = newTrail(flow.id)
        attest(trail.id, "junit", AttestationStatus.PASSED)
        val digest = "sha256:r${System.nanoTime()}"
        registerArtifact(trail.id, digest)

        assertService.assertCompliance(AssertRequest(digest, flow.id))
        assertEquals(TrailStatus.NON_COMPLIANT, trailService.getTrail(trail.id).status)

        attest(trail.id, "snyk", AttestationStatus.PASSED)
        assertService.assertCompliance(AssertRequest(digest, flow.id))

        assertEquals(TrailStatus.COMPLIANT, trailService.getTrail(trail.id).status)
    }

    // --- #163 ------------------------------------------------------------

    @Test
    fun `the response reports the trail the verdict was computed from`() {
        val flow = newFlow("junit")
        val trail = newTrail(flow.id)
        attest(trail.id, "junit", AttestationStatus.PASSED)
        val digest = "sha256:t${System.nanoTime()}"
        registerArtifact(trail.id, digest)

        val result = assertService.assertCompliance(AssertRequest(digest, flow.id))

        assertEquals(trail.id, result.trailId)
    }

    @Test
    fun `a trail-scoped assert returns the correct verdict for each of two trails sharing a digest`() {
        val flow = newFlow("junit", "snyk")
        val digest = "sha256:shared${System.nanoTime()}"

        val compliantTrail = newTrail(flow.id)
        attest(compliantTrail.id, "junit", AttestationStatus.PASSED)
        attest(compliantTrail.id, "snyk", AttestationStatus.PASSED)
        registerArtifact(compliantTrail.id, digest)

        val failingTrail = newTrail(flow.id)
        attest(failingTrail.id, "junit", AttestationStatus.PASSED)
        registerArtifact(failingTrail.id, digest)

        val compliant = assertService.assertCompliance(AssertRequest(digest, flow.id, trailId = compliantTrail.id))
        assertEquals(ComplianceStatus.COMPLIANT, compliant.status)
        assertEquals(compliantTrail.id, compliant.trailId)

        val nonCompliant = assertService.assertCompliance(AssertRequest(digest, flow.id, trailId = failingTrail.id))
        assertEquals(ComplianceStatus.NON_COMPLIANT, nonCompliant.status)
        assertEquals(failingTrail.id, nonCompliant.trailId)
        assertTrue(nonCompliant.missingAttestationTypes.contains("snyk"))
    }

    @Test
    fun `a re-run of the same commit is not compliant on the strength of the previous run`() {
        val flow = newFlow("junit", "snyk")
        val digest = "sha256:rerun${System.nanoTime()}"

        val firstRun = newTrail(flow.id)
        attest(firstRun.id, "junit", AttestationStatus.PASSED)
        attest(firstRun.id, "snyk", AttestationStatus.PASSED)
        registerArtifact(firstRun.id, digest)
        assertEquals(ComplianceStatus.COMPLIANT, assertService.assertCompliance(AssertRequest(digest, flow.id)).status)

        // Second run: same commit, same digest, no attestations of its own yet.
        val secondRun = newTrail(flow.id)
        registerArtifact(secondRun.id, digest)

        val result = assertService.assertCompliance(AssertRequest(digest, flow.id))

        assertEquals(ComplianceStatus.NON_COMPLIANT, result.status)
        assertEquals(secondRun.id, result.trailId, "the most recent trail must decide the digest-only verdict")
        // The first run keeps the verdict it earned; only the judged trail is updated.
        assertEquals(TrailStatus.COMPLIANT, trailService.getTrail(firstRun.id).status)
        assertEquals(TrailStatus.NON_COMPLIANT, trailService.getTrail(secondRun.id).status)
    }

    @Test
    fun `attestations recorded before the image digest exists still count`() {
        val flow = newFlow("junit", "snyk")
        val trail = newTrail(flow.id)
        // Unit tests and SAST run before the image is pushed - no artifact yet.
        attest(trail.id, "junit", AttestationStatus.PASSED)
        attest(trail.id, "snyk", AttestationStatus.PASSED)

        val result = assertService.assertTrail(trail.id, flowId = null, sha256Digest = null)

        assertEquals(ComplianceStatus.COMPLIANT, result.status)
        assertEquals(trail.id, result.trailId)
        assertEquals(TrailStatus.COMPLIANT, trailService.getTrail(trail.id).status)
    }

    @Test
    fun `a digest belonging to a different trail is rejected when the assert is trail-scoped`() {
        val flow = newFlow("junit")
        val trailA = newTrail(flow.id)
        val trailB = newTrail(flow.id)
        val digestA = "sha256:a${System.nanoTime()}"
        registerArtifact(trailA.id, digestA)

        assertThrows<BadRequestException> {
            assertService.assertCompliance(AssertRequest(digestA, flow.id, trailId = trailB.id))
        }
    }

    @Test
    fun `a trail-scoped assert with a template evaluates only that trail`() {
        val templateYaml = """
            version: 1
            artifacts:
              - name: myimage
                attestations:
                  - name: unit-tests
                    type: junit
                  - name: security-scan
                    type: snyk
        """.trimIndent()
        val flow = flowService.createFlow(
            CreateFlowRequest("flow-tmpl-${System.nanoTime()}", "desc", emptyList(), templateYaml = templateYaml)
        )
        val digest = "sha256:tmplshared${System.nanoTime()}"

        val good = newTrail(flow.id)
        attestationService.recordWithEvidence(
            good.id,
            CreateAttestationRequest(type = "junit", status = AttestationStatus.PASSED, name = "unit-tests")
        )
        attestationService.recordWithEvidence(
            good.id,
            CreateAttestationRequest(type = "snyk", status = AttestationStatus.PASSED, name = "security-scan")
        )
        registerArtifact(good.id, digest)

        val bad = newTrail(flow.id)
        attestationService.recordWithEvidence(
            bad.id,
            CreateAttestationRequest(type = "junit", status = AttestationStatus.PASSED, name = "unit-tests")
        )
        registerArtifact(bad.id, digest)

        val goodResult = assertService.assertCompliance(AssertRequest(digest, flow.id, trailId = good.id))
        assertEquals(ComplianceStatus.COMPLIANT, goodResult.status)

        val badResult = assertService.assertCompliance(AssertRequest(digest, flow.id, trailId = bad.id))
        assertEquals(ComplianceStatus.NON_COMPLIANT, badResult.status)
        assertTrue(badResult.missingAttestationNames.contains("security-scan"))
    }
}
