package com.factstore

import com.factstore.testsupport.recordWithEvidence
import com.factstore.application.AttestationService
import com.factstore.application.ArtifactService
import com.factstore.application.DeploymentPolicyService
import com.factstore.application.FlowService
import com.factstore.application.TrailService
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.GateDecision
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
class DeploymentPolicyServiceTest {

    @Autowired lateinit var deploymentPolicyService: DeploymentPolicyService
    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: TrailService
    @Autowired lateinit var artifactService: ArtifactService
    @Autowired lateinit var attestationService: AttestationService

    private fun createFlow(name: String = "gate-test-flow-${UUID.randomUUID()}") =
        flowService.createFlow(CreateFlowRequest(name = name, description = "desc"))

    private fun createTrail(flowId: UUID) = trailService.createTrail(
        CreateTrailRequest(
            flowId = flowId,
            gitCommitSha = "sha-${UUID.randomUUID()}",
            gitBranch = "main",
            gitAuthor = "Test User",
            gitAuthorEmail = "test@example.com"
        )
    )

    @Test
    fun `create policy succeeds`() {
        val flow = createFlow()
        val response = deploymentPolicyService.createPolicy(
            CreateDeploymentPolicyRequest(
                name = "my-policy",
                description = "test",
                flowId = flow.id,
                enforceProvenance = true,
                requiredAttestationTypes = listOf("junit", "snyk")
            )
        )
        assertEquals("my-policy", response.name)
        assertEquals(flow.id, response.flowId)
        assertTrue(response.enforceProvenance)
        assertEquals(listOf("junit", "snyk"), response.requiredAttestationTypes)
        assertTrue(response.isActive)
    }

    @Test
    fun `evaluate gate returns ALLOWED when no policies exist`() {
        val response = deploymentPolicyService.evaluateGate(
            GateEvaluateRequest(artifactSha256 = "sha256:nonexistent-${UUID.randomUUID()}")
        )
        assertEquals(GateDecision.ALLOWED, response.decision)
        assertEquals(0, response.policiesEvaluated)
        assertTrue(response.blockReasons.isEmpty())
    }

    @Test
    fun `evaluate gate returns BLOCKED when required attestation missing`() {
        val flow = createFlow()
        val trail = createTrail(flow.id)
        artifactService.reportArtifact(
            trail.id,
            CreateArtifactRequest(
                imageName = "my-image",
                imageTag = "v1",
                sha256Digest = "sha256:deadbeef001",
                reportedBy = "ci"
            )
        )
        // Record a FAILED attestation — junit is missing as PASSED
        attestationService.recordWithEvidence(trail.id, CreateAttestationRequest("snyk", AttestationStatus.FAILED))

        deploymentPolicyService.createPolicy(
            CreateDeploymentPolicyRequest(
                name = "strict-policy",
                flowId = flow.id,
                requiredAttestationTypes = listOf("junit")
            )
        )

        val response = deploymentPolicyService.evaluateGate(
            GateEvaluateRequest(artifactSha256 = "sha256:deadbeef001")
        )

        assertEquals(GateDecision.BLOCKED, response.decision)
        assertTrue(response.blockReasons.any { it.contains("junit") })
    }

    @Test
    fun `evaluate gate returns ALLOWED when all required attestations passed`() {
        val flow = createFlow()
        val trail = createTrail(flow.id)
        artifactService.reportArtifact(
            trail.id,
            CreateArtifactRequest(
                imageName = "my-image",
                imageTag = "v2",
                sha256Digest = "sha256:allgood001",
                reportedBy = "ci"
            )
        )
        attestationService.recordWithEvidence(trail.id, CreateAttestationRequest("junit", AttestationStatus.PASSED))
        attestationService.recordWithEvidence(trail.id, CreateAttestationRequest("snyk", AttestationStatus.PASSED))

        deploymentPolicyService.createPolicy(
            CreateDeploymentPolicyRequest(
                name = "require-attestations",
                flowId = flow.id,
                requiredAttestationTypes = listOf("junit", "snyk")
            )
        )

        val response = deploymentPolicyService.evaluateGate(
            GateEvaluateRequest(artifactSha256 = "sha256:allgood001")
        )

        assertEquals(GateDecision.ALLOWED, response.decision)
        assertTrue(response.blockReasons.isEmpty())
    }

    @Test
    fun `delete policy removes it`() {
        val flow = createFlow()
        val policy = deploymentPolicyService.createPolicy(
            CreateDeploymentPolicyRequest(name = "to-delete", flowId = flow.id)
        )
        deploymentPolicyService.deletePolicy(policy.id)

        assertThrows<NotFoundException> {
            deploymentPolicyService.getPolicy(policy.id)
        }
    }

    @Test
    fun `list gate results returns all results`() {
        val sha = "sha256:results-test-${UUID.randomUUID()}"
        deploymentPolicyService.evaluateGate(GateEvaluateRequest(artifactSha256 = sha))

        val results = deploymentPolicyService.listGateResults()
        assertTrue(results.any { it.artifactSha256 == sha })
    }
}
