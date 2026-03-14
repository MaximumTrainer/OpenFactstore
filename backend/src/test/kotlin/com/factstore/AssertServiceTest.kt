package com.factstore

import com.factstore.core.domain.AttestationStatus
import com.factstore.dto.*
import com.factstore.application.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class AssertServiceTest {

    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: TrailService
    @Autowired lateinit var attestationService: AttestationService
    @Autowired lateinit var artifactService: ArtifactService
    @Autowired lateinit var assertService: AssertService

    private fun createFullSetup(requiredTypes: List<String>, attestedTypes: List<Pair<String, AttestationStatus>>): Pair<java.util.UUID, String> {
        val flow = flowService.createFlow(CreateFlowRequest("flow-${System.nanoTime()}", "desc", requiredTypes))
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id,
            gitCommitSha = "abc123",
            gitBranch = "main",
            gitAuthor = "author",
            gitAuthorEmail = "author@example.com"
        ))
        for ((type, status) in attestedTypes) {
            attestationService.recordAttestation(trail.id, CreateAttestationRequest(type = type, status = status))
        }
        val digest = "sha256:${System.nanoTime()}"
        artifactService.reportArtifact(trail.id, CreateArtifactRequest(
            imageName = "myimage",
            imageTag = "latest",
            sha256Digest = digest,
            reportedBy = "ci"
        ))
        return Pair(flow.id, digest)
    }

    @Test
    fun `all required attestations passed returns COMPLIANT`() {
        val (flowId, digest) = createFullSetup(
            listOf("junit", "snyk"),
            listOf("junit" to AttestationStatus.PASSED, "snyk" to AttestationStatus.PASSED)
        )
        val result = assertService.assertCompliance(AssertRequest(digest, flowId))
        assertEquals(ComplianceStatus.COMPLIANT, result.status)
        assertTrue(result.missingAttestationTypes.isEmpty())
    }

    @Test
    fun `missing required attestation returns NON_COMPLIANT`() {
        val (flowId, digest) = createFullSetup(
            listOf("junit", "snyk"),
            listOf("junit" to AttestationStatus.PASSED)
        )
        val result = assertService.assertCompliance(AssertRequest(digest, flowId))
        assertEquals(ComplianceStatus.NON_COMPLIANT, result.status)
        assertTrue(result.missingAttestationTypes.contains("snyk"))
    }

    @Test
    fun `failed attestation not counted as passed`() {
        val (flowId, digest) = createFullSetup(
            listOf("junit"),
            listOf("junit" to AttestationStatus.FAILED)
        )
        val result = assertService.assertCompliance(AssertRequest(digest, flowId))
        assertEquals(ComplianceStatus.NON_COMPLIANT, result.status)
        assertTrue(result.missingAttestationTypes.contains("junit"))
    }

    @Test
    fun `no artifacts found returns NON_COMPLIANT`() {
        val flow = flowService.createFlow(CreateFlowRequest("flow-no-art-${System.nanoTime()}", "desc", listOf("junit")))
        val result = assertService.assertCompliance(AssertRequest("sha256:nonexistent", flow.id))
        assertEquals(ComplianceStatus.NON_COMPLIANT, result.status)
    }

    @Test
    fun `flow with no required types returns COMPLIANT`() {
        val flow = flowService.createFlow(CreateFlowRequest("flow-empty-${System.nanoTime()}", "desc", emptyList()))
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id,
            gitCommitSha = "abc",
            gitBranch = "main",
            gitAuthor = "a",
            gitAuthorEmail = "a@b.com"
        ))
        val digest = "sha256:empty${System.nanoTime()}"
        artifactService.reportArtifact(trail.id, CreateArtifactRequest("img", "1.0", digest, reportedBy = "ci"))
        val result = assertService.assertCompliance(AssertRequest(digest, flow.id))
        assertEquals(ComplianceStatus.COMPLIANT, result.status)
    }

    @Test
    fun `assert with template - all named attestations present returns COMPLIANT`() {
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
        val flow = flowService.createFlow(CreateFlowRequest("flow-tmpl-${System.nanoTime()}", "desc", emptyList(), templateYaml = templateYaml))
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id, gitCommitSha = "abc", gitBranch = "main", gitAuthor = "a", gitAuthorEmail = "a@b.com"
        ))
        attestationService.recordAttestation(trail.id, CreateAttestationRequest(type = "junit", status = AttestationStatus.PASSED, name = "unit-tests"))
        attestationService.recordAttestation(trail.id, CreateAttestationRequest(type = "snyk", status = AttestationStatus.PASSED, name = "security-scan"))
        val digest = "sha256:tmpl${System.nanoTime()}"
        artifactService.reportArtifact(trail.id, CreateArtifactRequest("myimage", "latest", digest, reportedBy = "ci"))
        val result = assertService.assertCompliance(AssertRequest(digest, flow.id))
        assertEquals(ComplianceStatus.COMPLIANT, result.status)
        assertTrue(result.missingAttestationNames.isEmpty())
    }

    @Test
    fun `assert with template - missing named attestation returns NON_COMPLIANT`() {
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
        val flow = flowService.createFlow(CreateFlowRequest("flow-tmpl-nc-${System.nanoTime()}", "desc", emptyList(), templateYaml = templateYaml))
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id, gitCommitSha = "abc", gitBranch = "main", gitAuthor = "a", gitAuthorEmail = "a@b.com"
        ))
        attestationService.recordAttestation(trail.id, CreateAttestationRequest(type = "junit", status = AttestationStatus.PASSED, name = "unit-tests"))
        // security-scan missing
        val digest = "sha256:tmpl-nc${System.nanoTime()}"
        artifactService.reportArtifact(trail.id, CreateArtifactRequest("myimage", "latest", digest, reportedBy = "ci"))
        val result = assertService.assertCompliance(AssertRequest(digest, flow.id))
        assertEquals(ComplianceStatus.NON_COMPLIANT, result.status)
        assertTrue(result.missingAttestationNames.contains("security-scan"))
    }

    @Test
    fun `assert with trail template override`() {
        val flowTemplateYaml = """
version: 1
artifacts:
  - name: myimage
    attestations:
      - name: unit-tests
        type: junit
""".trimIndent()
        val trailTemplateYaml = """
version: 1
artifacts:
  - name: myimage
    attestations:
      - name: security-scan
        type: snyk
""".trimIndent()
        val flow = flowService.createFlow(CreateFlowRequest("flow-trail-tmpl-${System.nanoTime()}", "desc", emptyList(), templateYaml = flowTemplateYaml))
        val trail = trailService.createTrail(CreateTrailRequest(
            flowId = flow.id, gitCommitSha = "abc", gitBranch = "main", gitAuthor = "a", gitAuthorEmail = "a@b.com",
            templateYaml = trailTemplateYaml
        ))
        attestationService.recordAttestation(trail.id, CreateAttestationRequest(type = "snyk", status = AttestationStatus.PASSED, name = "security-scan"))
        val digest = "sha256:trail-tmpl${System.nanoTime()}"
        artifactService.reportArtifact(trail.id, CreateArtifactRequest("myimage", "latest", digest, reportedBy = "ci"))
        val result = assertService.assertCompliance(AssertRequest(digest, flow.id))
        assertEquals(ComplianceStatus.COMPLIANT, result.status)
    }

    @Test
    fun `assert with no template falls back to requiredAttestationTypes`() {
        val (flowId, digest) = createFullSetup(
            listOf("junit", "snyk"),
            listOf("junit" to AttestationStatus.PASSED, "snyk" to AttestationStatus.PASSED)
        )
        val result = assertService.assertCompliance(AssertRequest(digest, flowId))
        assertEquals(ComplianceStatus.COMPLIANT, result.status)
    }
}
