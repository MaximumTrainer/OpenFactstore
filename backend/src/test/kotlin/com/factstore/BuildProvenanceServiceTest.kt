package com.factstore

import com.factstore.application.ArtifactService
import com.factstore.application.BuildProvenanceService
import com.factstore.application.FlowService
import com.factstore.application.TrailService
import com.factstore.core.domain.BuilderType
import com.factstore.core.domain.ProvenanceStatus
import com.factstore.core.domain.SlsaLevel
import com.factstore.dto.CreateArtifactRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.RecordProvenanceRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Transactional
class BuildProvenanceServiceTest {

    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: TrailService
    @Autowired lateinit var artifactService: ArtifactService
    @Autowired lateinit var buildProvenanceService: BuildProvenanceService

    private fun setupArtifact(): Pair<UUID, UUID> {
        val flow = flowService.createFlow(CreateFlowRequest("flow-prov-${System.nanoTime()}", "desc"))
        val trail = trailService.createTrail(
            CreateTrailRequest(
                flowId = flow.id,
                gitCommitSha = "abc123",
                gitBranch = "main",
                gitAuthor = "author",
                gitAuthorEmail = "author@example.com"
            )
        )
        val artifact = artifactService.reportArtifact(
            trail.id,
            CreateArtifactRequest(
                imageName = "my-app",
                imageTag = "1.0.0",
                sha256Digest = "sha256:abc${System.nanoTime()}",
                reportedBy = "ci-bot"
            )
        )
        return Pair(trail.id, artifact.id)
    }

    @Test
    fun `record provenance successfully`() {
        val (trailId, artifactId) = setupArtifact()
        val response = buildProvenanceService.recordProvenance(
            trailId, artifactId,
            RecordProvenanceRequest(
                builderId = "github-runner-1",
                builderType = BuilderType.GITHUB_ACTIONS,
                buildConfigUri = ".github/workflows/build.yml",
                sourceRepositoryUri = "https://github.com/org/repo",
                sourceCommitSha = "abc123",
                slsaLevel = SlsaLevel.L2
            )
        )
        assertEquals(artifactId, response.artifactId)
        assertEquals("github-runner-1", response.builderId)
        assertEquals(BuilderType.GITHUB_ACTIONS, response.builderType)
        assertEquals(SlsaLevel.L2, response.slsaLevel)
        assertEquals(ProvenanceStatus.PROVENANCE_UNVERIFIED, response.provenanceStatus)
        assertNull(response.provenanceSignature)
    }

    @Test
    fun `record provenance with signature marks as verified`() {
        val (trailId, artifactId) = setupArtifact()
        val response = buildProvenanceService.recordProvenance(
            trailId, artifactId,
            RecordProvenanceRequest(
                builderId = "github-runner-2",
                builderType = BuilderType.GITHUB_ACTIONS,
                provenanceSignature = "MEYCIQDabcdefg"
            )
        )
        assertEquals(ProvenanceStatus.PROVENANCE_VERIFIED, response.provenanceStatus)
        assertEquals("MEYCIQDabcdefg", response.provenanceSignature)
    }

    @Test
    fun `record provenance twice throws ConflictException`() {
        val (trailId, artifactId) = setupArtifact()
        val req = RecordProvenanceRequest(builderId = "runner-1", builderType = BuilderType.GENERIC)
        buildProvenanceService.recordProvenance(trailId, artifactId, req)
        assertThrows(ConflictException::class.java) {
            buildProvenanceService.recordProvenance(trailId, artifactId, req)
        }
    }

    @Test
    fun `get provenance returns recorded data`() {
        val (trailId, artifactId) = setupArtifact()
        buildProvenanceService.recordProvenance(
            trailId, artifactId,
            RecordProvenanceRequest(
                builderId = "jenkins-1",
                builderType = BuilderType.JENKINS,
                sourceCommitSha = "deadbeef"
            )
        )
        val result = buildProvenanceService.getProvenance(trailId, artifactId)
        assertEquals(artifactId, result.artifactId)
        assertEquals("deadbeef", result.sourceCommitSha)
        assertEquals(BuilderType.JENKINS, result.builderType)
    }

    @Test
    fun `get provenance for artifact without provenance throws NotFoundException`() {
        val (trailId, artifactId) = setupArtifact()
        assertThrows(NotFoundException::class.java) {
            buildProvenanceService.getProvenance(trailId, artifactId)
        }
    }

    @Test
    fun `get provenance by sha256`() {
        val (trailId, artifactId) = setupArtifact()
        val sha = "sha256:unique-${System.nanoTime()}"
        val flow2 = flowService.createFlow(CreateFlowRequest("flow-prov2-${System.nanoTime()}", "desc"))
        val trail2 = trailService.createTrail(
            CreateTrailRequest(
                flowId = flow2.id,
                gitCommitSha = "commit2",
                gitBranch = "main",
                gitAuthor = "author",
                gitAuthorEmail = "author@example.com"
            )
        )
        val artifact2 = artifactService.reportArtifact(
            trail2.id,
            CreateArtifactRequest(imageName = "app", imageTag = "2.0", sha256Digest = sha, reportedBy = "ci")
        )
        buildProvenanceService.recordProvenance(
            trail2.id, artifact2.id,
            RecordProvenanceRequest(builderId = "circle-1", builderType = BuilderType.CIRCLE_CI)
        )
        val result = buildProvenanceService.getProvenanceBySha256(sha)
        assertEquals(artifact2.id, result.artifactId)
        assertEquals(BuilderType.CIRCLE_CI, result.builderType)
    }

    @Test
    fun `verify provenance returns NO_PROVENANCE when none recorded`() {
        val (trailId, artifactId) = setupArtifact()
        val result = buildProvenanceService.verifyProvenance(trailId, artifactId)
        assertEquals(ProvenanceStatus.NO_PROVENANCE, result.provenanceStatus)
    }

    @Test
    fun `verify provenance returns UNVERIFIED without signature`() {
        val (trailId, artifactId) = setupArtifact()
        buildProvenanceService.recordProvenance(
            trailId, artifactId,
            RecordProvenanceRequest(builderId = "runner", builderType = BuilderType.GENERIC)
        )
        val result = buildProvenanceService.verifyProvenance(trailId, artifactId)
        assertEquals(ProvenanceStatus.PROVENANCE_UNVERIFIED, result.provenanceStatus)
    }

    @Test
    fun `verify provenance returns VERIFIED with signature`() {
        val (trailId, artifactId) = setupArtifact()
        buildProvenanceService.recordProvenance(
            trailId, artifactId,
            RecordProvenanceRequest(
                builderId = "runner",
                builderType = BuilderType.GITHUB_ACTIONS,
                provenanceSignature = "MEQCIBz1..."
            )
        )
        val result = buildProvenanceService.verifyProvenance(trailId, artifactId)
        assertEquals(ProvenanceStatus.PROVENANCE_VERIFIED, result.provenanceStatus)
    }

    @Test
    fun `artifact response includes provenance status`() {
        val (trailId, artifactId) = setupArtifact()
        val artifacts = artifactService.listArtifactsForTrail(trailId)
        assertEquals(ProvenanceStatus.NO_PROVENANCE, artifacts.first().provenanceStatus)

        buildProvenanceService.recordProvenance(
            trailId, artifactId,
            RecordProvenanceRequest(builderId = "runner", builderType = BuilderType.GENERIC)
        )
        val updatedArtifacts = artifactService.listArtifactsForTrail(trailId)
        assertEquals(ProvenanceStatus.PROVENANCE_UNVERIFIED, updatedArtifacts.first().provenanceStatus)
    }

    @Test
    fun `record provenance for unknown trail throws NotFoundException`() {
        assertThrows(NotFoundException::class.java) {
            buildProvenanceService.recordProvenance(
                UUID.randomUUID(), UUID.randomUUID(),
                RecordProvenanceRequest(builderId = "runner", builderType = BuilderType.GENERIC)
            )
        }
    }

    @Test
    fun `record provenance for artifact not in trail throws NotFoundException`() {
        val (trailId, _) = setupArtifact()
        val (trailId2, artifactId2) = setupArtifact()
        assertThrows(NotFoundException::class.java) {
            buildProvenanceService.recordProvenance(
                trailId, artifactId2,
                RecordProvenanceRequest(builderId = "runner", builderType = BuilderType.GENERIC)
            )
        }
    }
}
