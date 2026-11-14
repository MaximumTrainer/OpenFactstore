package com.factstore.attestation

import com.factstore.application.ArtifactService
import com.factstore.application.AssertService
import com.factstore.application.AttestationService
import com.factstore.application.CustomAttestationTypeService
import com.factstore.application.EvidenceCollectionService
import com.factstore.application.FlowService
import com.factstore.application.gates.GateEvaluationService
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.EnvironmentType
import com.factstore.core.port.inbound.IEnvironmentService
import com.factstore.core.port.inbound.ITrailService
import com.factstore.core.port.inbound.command.IAttestationCommandHandler
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.dto.AssertRequest
import com.factstore.dto.BulkEvidenceItem
import com.factstore.dto.BulkEvidenceRequest
import com.factstore.dto.ComplianceStatus
import com.factstore.dto.CreateArtifactAttestationRequest
import com.factstore.dto.CreateArtifactRequest
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreateCustomAttestationTypeRequest
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.UpdateFlowRequest
import com.factstore.dto.command.RecordAttestationCommand
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * #240: whether evidence passed is decided by the server from the evidence, not asserted by the
 * caller. A claim the server cannot check is recorded as self-asserted, labelled in assert and
 * gate output, and a flow can refuse to count it.
 */
@SpringBootTest
@Transactional
@WithMockUser(authorities = ["SCOPE_admin"])
class ServerSideVerdictTest {

    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: ITrailService
    @Autowired lateinit var attestationService: AttestationService
    @Autowired lateinit var attestationCommands: IAttestationCommandHandler
    @Autowired lateinit var attestationRepository: IAttestationRepository
    @Autowired lateinit var artifactService: ArtifactService
    @Autowired lateinit var assertService: AssertService
    @Autowired lateinit var evidenceCollection: EvidenceCollectionService
    @Autowired lateinit var customTypes: CustomAttestationTypeService
    @Autowired lateinit var gates: GateEvaluationService
    @Autowired lateinit var environments: IEnvironmentService

    private val failingJunit = """<testsuite name="unit" tests="3" failures="1" errors="0"><testcase name="a"/></testsuite>"""
    private val passingJunit = """<testsuite name="unit" tests="3" failures="0" errors="0"><testcase name="a"/></testsuite>"""

    private fun newFlow(required: List<String>, rejectSelfAsserted: Boolean = false, accepted: List<String> = emptyList()) =
        flowService.createFlow(
            CreateFlowRequest(
                "verdict-${System.nanoTime()}", "d", required,
                rejectSelfAsserted = rejectSelfAsserted, acceptedSelfAssertedTypes = accepted
            )
        )

    private fun newTrail(flowId: UUID) = trailService.createTrail(
        CreateTrailRequest(flowId = flowId, gitCommitSha = "c", gitBranch = "main", gitAuthor = "a", gitAuthorEmail = "a@example.com")
    ).id

    private fun digest() = "sha256:" + (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "")

    private fun reportArtifact(trailId: UUID): Pair<UUID, String> {
        val d = digest()
        val a = artifactService.reportArtifact(trailId, CreateArtifactRequest(imageName = "svc", imageTag = "1", sha256Digest = d, reportedBy = "ci"))
        return a.id to d
    }

    @Test
    fun `junit claimed PASSED with a failing report is FAILED`() {
        val trailId = newTrail(newFlow(listOf("junit")).id)
        val att = attestationService.recordAttestation(trailId, CreateAttestationRequest(type = "junit", status = AttestationStatus.PASSED))
        attestationService.uploadEvidence(trailId, att.id, "report.xml", "application/xml", failingJunit.toByteArray())

        assertEquals(AttestationStatus.FAILED, attestationRepository.findById(att.id)!!.status)
    }

    @Test
    fun `junit and snyk claimed PASSED without evidence are PENDING`() {
        val trailId = newTrail(newFlow(listOf("junit", "snyk")).id)
        listOf("junit", "snyk").forEach { type ->
            val att = attestationService.recordAttestation(trailId, CreateAttestationRequest(type = type, status = AttestationStatus.PASSED))
            assertEquals(AttestationStatus.PENDING, att.status, type)
            assertFalse(att.selfAsserted, type)
        }
    }

    @Test
    fun `evidence that passes turns a PENDING junit attestation into PASSED`() {
        val trailId = newTrail(newFlow(listOf("junit")).id)
        val att = attestationService.recordAttestation(trailId, CreateAttestationRequest(type = "junit"))
        attestationService.uploadEvidence(trailId, att.id, "report.xml", "application/xml", passingJunit.toByteArray())
        assertEquals(AttestationStatus.PASSED, attestationRepository.findById(att.id)!!.status)
    }

    @Test
    fun `artifact-level and v2 attestations follow the same rule`() {
        val trailId = newTrail(newFlow(listOf("junit")).id)
        val (artifactId, _) = reportArtifact(trailId)
        val artifactLevel = attestationService.recordArtifactAttestation(
            artifactId, CreateArtifactAttestationRequest(type = "junit", status = AttestationStatus.PASSED)
        )
        assertEquals(AttestationStatus.PENDING, artifactLevel.status)

        val v2 = attestationCommands.recordAttestation(
            RecordAttestationCommand(trailId = trailId, type = "snyk", status = AttestationStatus.PASSED)
        )
        assertEquals(AttestationStatus.PENDING, attestationRepository.findById(v2.id)!!.status)
    }

    @Test
    fun `a custom type with a jq expression ignores the claimed status`() {
        val type = "release-check-${System.nanoTime()}"
        customTypes.createType(CreateCustomAttestationTypeRequest(name = type, jqExpression = ".approved == true"))
        val trailId = newTrail(newFlow(listOf(type)).id)

        val att = attestationService.recordAttestation(
            trailId,
            CreateAttestationRequest(type = type, status = AttestationStatus.PASSED, attestationData = """{"approved": false}""")
        )
        assertEquals(AttestationStatus.FAILED, att.status)
    }

    @Test
    fun `evidence collect cannot pass an evaluated type by saying so`() {
        val trailId = newTrail(newFlow(listOf("junit")).id)
        val response = evidenceCollection.collectBulkEvidence(
            BulkEvidenceRequest(listOf(BulkEvidenceItem(trailId = trailId, evidenceType = "junit", tool = "ci", passed = true)))
        )
        val result = response.results.single()
        assertFalse(result.passed)
        assertEquals(AttestationStatus.PENDING, attestationRepository.findById(result.attestationId)!!.status)
    }

    @Test
    fun `evidence collect records a claim for an unevaluated type as self-asserted`() {
        val trailId = newTrail(newFlow(listOf("manual-review")).id)
        val result = evidenceCollection.collectBulkEvidence(
            BulkEvidenceRequest(listOf(BulkEvidenceItem(trailId = trailId, evidenceType = "manual-review", tool = "ci", passed = true)))
        ).results.single()
        val stored = attestationRepository.findById(result.attestationId)!!
        assertEquals(AttestationStatus.PASSED, stored.status)
        assertTrue(stored.selfAsserted)
    }

    @Test
    fun `assert labels attestations that passed only on the caller's word`() {
        val flow = newFlow(listOf("manual-review"))
        val trailId = newTrail(flow.id)
        attestationService.recordAttestation(trailId, CreateAttestationRequest(type = "manual-review", status = AttestationStatus.PASSED))
        val (_, d) = reportArtifact(trailId)

        val result = assertService.assertCompliance(AssertRequest(d, flow.id))
        assertEquals(ComplianceStatus.COMPLIANT, result.status)
        assertEquals(listOf("manual-review"), result.selfAssertedAttestationTypes)
        assertTrue(result.rejectedSelfAssertedAttestationTypes.isEmpty())
    }

    @Test
    fun `a flow configured to reject self-asserted evidence does not count it`() {
        val flow = newFlow(listOf("manual-review"), rejectSelfAsserted = true)
        val trailId = newTrail(flow.id)
        attestationService.recordAttestation(trailId, CreateAttestationRequest(type = "manual-review", status = AttestationStatus.PASSED))
        val (_, d) = reportArtifact(trailId)

        val result = assertService.assertCompliance(AssertRequest(d, flow.id))
        assertEquals(ComplianceStatus.NON_COMPLIANT, result.status)
        assertEquals(listOf("manual-review"), result.missingAttestationTypes)
        assertEquals(listOf("manual-review"), result.rejectedSelfAssertedAttestationTypes)
        assertTrue(result.details.contains("self-asserted"), result.details)
    }

    @Test
    fun `a flow that rejects self-assertion can still accept it for named types`() {
        val flow = newFlow(listOf("manual-review"), rejectSelfAsserted = true, accepted = listOf("manual-review"))
        val trailId = newTrail(flow.id)
        attestationService.recordAttestation(trailId, CreateAttestationRequest(type = "manual-review", status = AttestationStatus.PASSED))
        val (_, d) = reportArtifact(trailId)

        assertEquals(ComplianceStatus.COMPLIANT, assertService.assertCompliance(AssertRequest(d, flow.id)).status)
    }

    @Test
    fun `the self-asserted setting can be changed on an existing flow`() {
        val flow = newFlow(listOf("manual-review"))
        val updated = flowService.updateFlow(flow.id, UpdateFlowRequest(rejectSelfAsserted = true, acceptedSelfAssertedTypes = listOf("x")))
        assertTrue(updated.rejectSelfAsserted)
        assertEquals(listOf("x"), updated.acceptedSelfAssertedTypes)
    }

    @Test
    fun `gate evaluation labels and rejects self-asserted evidence`() {
        val env = "verdict-env-${System.nanoTime()}"
        environments.createEnvironment(CreateEnvironmentRequest(name = env, type = EnvironmentType.K8S))
        val mapper = jacksonObjectMapper()

        fun gate(flowId: UUID, d: String) = gates.evaluate(
            mapper.readTree("""{"artifact_sha256":"$d","target_environment":"$env","flow_id":"$flowId"}""")
        )

        val lenient = newFlow(listOf("manual-review"))
        val t1 = newTrail(lenient.id)
        attestationService.recordAttestation(t1, CreateAttestationRequest(type = "manual-review", status = AttestationStatus.PASSED))
        val (_, d1) = reportArtifact(t1)
        val approved = gate(lenient.id, d1)
        assertEquals("APPROVED", approved.status.name)
        assertEquals(listOf("manual-review"), approved.selfAsserted)

        val strict = newFlow(emptyList(), rejectSelfAsserted = true)
        val t2 = newTrail(strict.id)
        attestationService.recordAttestation(t2, CreateAttestationRequest(type = "manual-review", status = AttestationStatus.PASSED))
        val (_, d2) = reportArtifact(t2)
        val rejected = gates.evaluate(
            mapper.readTree("""{"artifact_sha256":"$d2","target_environment":"$env","flow_id":"${strict.id}","required_policies":["manual-review"]}""")
        )
        assertEquals("REJECTED", rejected.status.name)
        assertTrue("manual-review" in rejected.missingAttestations, rejected.missingAttestations.toString())
    }
}
