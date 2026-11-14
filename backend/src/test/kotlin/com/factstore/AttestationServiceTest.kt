package com.factstore

import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.TrailStatus
import com.factstore.dto.*
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.application.*
import com.factstore.core.port.inbound.ICustomAttestationTypeService
import com.factstore.exception.BadRequestException
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
class AttestationServiceTest {

    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: TrailService
    @Autowired lateinit var artifactService: ArtifactService
    @Autowired lateinit var attestationService: AttestationService
    @Autowired lateinit var trailRepository: ITrailRepository
    @Autowired lateinit var customAttestationTypeService: ICustomAttestationTypeService

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

    private fun setupArtifact(trailId: UUID): UUID {
        val hexChars = "abcdef0123456789"
        val uniqueSuffix = System.nanoTime().toString().takeLast(16).map { hexChars[(it.code % 16)] }.joinToString("")
        val digest = "sha256:" + "a".repeat(48) + uniqueSuffix
        val artifact = artifactService.reportArtifact(
            trailId,
            CreateArtifactRequest(
                imageName = "my-image",
                imageTag = "v1.0.${System.nanoTime()}",
                sha256Digest = digest,
                reportedBy = "test"
            )
        )
        return artifact.id
    }

    @Test
    fun `record PASSED attestation of an unevaluated type succeeds as self-asserted`() {
        val trailId = setupTrail()
        val resp = attestationService.recordAttestation(trailId, CreateAttestationRequest("code-review", AttestationStatus.PASSED))
        assertEquals("code-review", resp.type)
        assertEquals(AttestationStatus.PASSED, resp.status)
        assertTrue(resp.selfAsserted)
        assertEquals(trailId, resp.trailId)
    }

    @Test
    fun `record FAILED attestation marks trail NON_COMPLIANT`() {
        val trailId = setupTrail()
        attestationService.recordAttestation(trailId, CreateAttestationRequest("snyk", AttestationStatus.FAILED))
        val trail = trailRepository.findById(trailId)!!
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

    @Test
    fun `should store and retrieve attestation data JSON`() {
        val trailId = setupTrail()
        val json = """{"tests": 42, "passed": 42}"""
        val resp = attestationService.recordAttestation(
            trailId,
            CreateAttestationRequest("junit", AttestationStatus.PASSED, attestationData = json)
        )
        assertEquals(json, resp.attestationData)

        val list = attestationService.listAttestations(trailId)
        assertEquals(json, list.first().attestationData)

        val respNoData = attestationService.recordAttestation(
            trailId,
            CreateAttestationRequest("snyk", AttestationStatus.PASSED)
        )
        assertNull(respNoData.attestationData)
    }

    @Test
    fun `should store and retrieve multiple external URLs on attestation`() {
        val trailId = setupTrail()
        val urls = listOf("https://jira.example.com/TICKET-1", "https://github.com/pr/42")
        val resp = attestationService.recordAttestation(
            trailId,
            CreateAttestationRequest("junit", AttestationStatus.PASSED, externalUrls = urls)
        )
        assertEquals(urls, resp.externalUrls)

        val list = attestationService.listAttestations(trailId)
        assertEquals(urls, list.first().externalUrls)
    }

    @Test
    fun `should store and retrieve key-value annotations on attestation`() {
        val trailId = setupTrail()
        val annotations = mapOf("env" to "production", "team" to "backend")
        val resp = attestationService.recordAttestation(
            trailId,
            CreateAttestationRequest("junit", AttestationStatus.PASSED, annotations = annotations)
        )
        assertEquals(annotations, resp.annotations)

        val list = attestationService.listAttestations(trailId)
        assertEquals(annotations, list.first().annotations)
    }

    @Test
    fun `should store and retrieve git commit info on attestation`() {
        val trailId = setupTrail()
        val sha = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
        val branch = "feature/my-branch"
        val repoUrl = "https://github.com/example/repo"
        val resp = attestationService.recordAttestation(
            trailId,
            CreateAttestationRequest(
                "junit", AttestationStatus.PASSED,
                gitCommitSha = sha, gitBranch = branch, gitRepoUrl = repoUrl
            )
        )
        assertEquals(sha, resp.gitCommitSha)
        assertEquals(branch, resp.gitBranch)
        assertEquals(repoUrl, resp.gitRepoUrl)

        val list = attestationService.listAttestations(trailId)
        val first = list.first()
        assertEquals(sha, first.gitCommitSha)
        assertEquals(branch, first.gitBranch)
        assertEquals(repoUrl, first.gitRepoUrl)
    }

    @Test
    fun `should validate attestationData against type schema when schema is set`() {
        customAttestationTypeService.createType(CreateCustomAttestationTypeRequest(
            name = "schema-validated-type",
            description = "desc",
            schemaJson = """{"type":"object","properties":{"passed":{"type":"boolean"}}}"""
        ))
        val trailId = setupTrail()
        val validJson = """{"passed": true}"""
        val resp = attestationService.recordAttestation(
            trailId,
            CreateAttestationRequest("schema-validated-type", AttestationStatus.PASSED, attestationData = validJson)
        )
        assertEquals(validJson, resp.attestationData)
    }

    @Test
    fun `should allow attestation without attestationData even when schema is set`() {
        customAttestationTypeService.createType(CreateCustomAttestationTypeRequest(
            name = "schema-optional-data-type",
            description = "desc",
            schemaJson = """{"type":"object"}"""
        ))
        val trailId = setupTrail()
        val resp = attestationService.recordAttestation(
            trailId,
            CreateAttestationRequest("schema-optional-data-type", AttestationStatus.PASSED)
        )
        assertNull(resp.attestationData)
    }

    @Test
    fun `should reject attestationData that does not conform to schema`() {
        customAttestationTypeService.createType(CreateCustomAttestationTypeRequest(
            name = "strict-schema-type",
            description = "desc",
            schemaJson = """{"type":"object"}"""
        ))
        val trailId = setupTrail()
        assertThrows<BadRequestException> {
            attestationService.recordAttestation(
                trailId,
                CreateAttestationRequest("strict-schema-type", AttestationStatus.PASSED, attestationData = "not-valid-json{")
            )
        }
    }

    @Test
    fun `should reject valid JSON attestationData that violates schema type constraints`() {
        customAttestationTypeService.createType(CreateCustomAttestationTypeRequest(
            name = "typed-schema-type",
            description = "desc",
            schemaJson = """{"type":"object","properties":{"count":{"type":"integer"}},"required":["count"]}"""
        ))
        val trailId = setupTrail()
        assertThrows<BadRequestException> {
            attestationService.recordAttestation(
                trailId,
                CreateAttestationRequest("typed-schema-type", AttestationStatus.PASSED, attestationData = """{"count": "not-an-integer"}""")
            )
        }
    }

    @Test
    fun `should reject valid JSON attestationData missing required schema fields`() {
        customAttestationTypeService.createType(CreateCustomAttestationTypeRequest(
            name = "required-fields-type",
            description = "desc",
            schemaJson = """{"type":"object","properties":{"passed":{"type":"boolean"}},"required":["passed"]}"""
        ))
        val trailId = setupTrail()
        assertThrows<BadRequestException> {
            attestationService.recordAttestation(
                trailId,
                CreateAttestationRequest("required-fields-type", AttestationStatus.PASSED, attestationData = """{"other": "field"}""")
            )
        }
    }

    @Test
    fun `should auto-set status to PASSED when jq expression evaluates to true`() {
        customAttestationTypeService.createType(CreateCustomAttestationTypeRequest(
            name = "jq-pass-type",
            description = "desc",
            jqExpression = ".passed == true"
        ))
        val trailId = setupTrail()
        val resp = attestationService.recordAttestation(
            trailId,
            CreateAttestationRequest("jq-pass-type", AttestationStatus.PENDING, attestationData = """{"passed": true}""")
        )
        assertEquals(AttestationStatus.PASSED, resp.status)
    }

    @Test
    fun `should auto-set status to FAILED when jq expression evaluates to false`() {
        customAttestationTypeService.createType(CreateCustomAttestationTypeRequest(
            name = "jq-fail-type",
            description = "desc",
            jqExpression = ".passed == true"
        ))
        val trailId = setupTrail()
        val resp = attestationService.recordAttestation(
            trailId,
            CreateAttestationRequest("jq-fail-type", AttestationStatus.PENDING, attestationData = """{"passed": false}""")
        )
        assertEquals(AttestationStatus.FAILED, resp.status)
    }

    // Issue #124: artifact-level attestations
    @Test
    fun `should record attestation at artifact level`() {
        val trailId = setupTrail()
        val artifactId = setupArtifact(trailId)
        val resp = attestationService.recordArtifactAttestation(
            artifactId,
            CreateArtifactAttestationRequest("snyk", AttestationStatus.PASSED)
        )
        assertEquals("snyk", resp.type)
        // #240: snyk is evaluated from its report, so the claim leaves it PENDING.
        assertEquals(AttestationStatus.PENDING, resp.status)
        assertEquals(artifactId, resp.artifactId)
        assertEquals(trailId, resp.trailId)
        assertNotNull(resp.artifactFingerprint)
    }

    @Test
    fun `should list attestations by artifact id`() {
        val trailId = setupTrail()
        val artifactId = setupArtifact(trailId)
        attestationService.recordArtifactAttestation(artifactId, CreateArtifactAttestationRequest("snyk", AttestationStatus.PASSED))
        attestationService.recordArtifactAttestation(artifactId, CreateArtifactAttestationRequest("junit", AttestationStatus.PASSED))
        val list = attestationService.listArtifactAttestations(artifactId)
        assertEquals(2, list.size)
        assertTrue(list.all { it.artifactId == artifactId })
    }

    @Test
    fun `should throw NotFoundException when recording artifact attestation for unknown artifact`() {
        assertThrows<NotFoundException> {
            attestationService.recordArtifactAttestation(
                UUID.randomUUID(),
                CreateArtifactAttestationRequest("snyk", AttestationStatus.PASSED)
            )
        }
    }

    // Issue #125: attestation override with justification
    @Test
    fun `should override a failed attestation with justification`() {
        val trailId = setupTrail()
        val failed = attestationService.recordAttestation(trailId, CreateAttestationRequest("snyk", AttestationStatus.FAILED))
        val override = attestationService.overrideAttestation(failed.id, OverrideAttestationRequest("Approved by security team"))
        assertEquals(AttestationStatus.PASSED, override.status)
        assertEquals(failed.id, override.overridesAttestationId)
        assertEquals("Approved by security team", override.justification)
        assertEquals(failed.type, override.type)
        assertEquals(trailId, override.trailId)
    }

    @Test
    fun `should preserve original attestation after override`() {
        val trailId = setupTrail()
        val failed = attestationService.recordAttestation(trailId, CreateAttestationRequest("snyk", AttestationStatus.FAILED))
        attestationService.overrideAttestation(failed.id, OverrideAttestationRequest("justified"))
        val allAttestations = attestationService.listAttestations(trailId)
        assertEquals(2, allAttestations.size)
        assertTrue(allAttestations.any { it.id == failed.id && it.status == AttestationStatus.FAILED })
    }

    @Test
    fun `should throw NotFoundException when overriding unknown attestation`() {
        assertThrows<NotFoundException> {
            attestationService.overrideAttestation(UUID.randomUUID(), OverrideAttestationRequest("justification"))
        }
    }
}
