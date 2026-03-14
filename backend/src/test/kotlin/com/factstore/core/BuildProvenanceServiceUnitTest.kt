package com.factstore.core

import com.factstore.adapter.mock.InMemoryArtifactRepository
import com.factstore.adapter.mock.InMemoryBuildProvenanceRepository
import com.factstore.adapter.mock.InMemoryTrailRepository
import com.factstore.application.BuildProvenanceService
import com.factstore.core.domain.Artifact
import com.factstore.core.domain.BuilderType
import com.factstore.core.domain.ProvenanceStatus
import com.factstore.core.domain.SlsaLevel
import com.factstore.core.domain.Trail
import com.factstore.core.domain.TrailStatus
import com.factstore.dto.RecordProvenanceRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * Unit tests for BuildProvenanceService that run without a Spring context.
 * Uses in-memory mock adapters to test all service logic in isolation.
 */
class BuildProvenanceServiceUnitTest {

    private lateinit var provenanceRepository: InMemoryBuildProvenanceRepository
    private lateinit var artifactRepository: InMemoryArtifactRepository
    private lateinit var trailRepository: InMemoryTrailRepository
    private lateinit var service: BuildProvenanceService

    @BeforeEach
    fun setUp() {
        provenanceRepository = InMemoryBuildProvenanceRepository()
        artifactRepository = InMemoryArtifactRepository()
        trailRepository = InMemoryTrailRepository()
        service = BuildProvenanceService(provenanceRepository, artifactRepository, trailRepository)
    }

    private fun makeTrail(): Trail {
        val trail = Trail(
            flowId = UUID.randomUUID(),
            gitCommitSha = "abc123",
            gitBranch = "main",
            gitAuthor = "author",
            gitAuthorEmail = "author@example.com",
            status = TrailStatus.PENDING
        )
        return trailRepository.save(trail)
    }

    private fun makeArtifact(trailId: UUID, sha256: String = "sha256:${UUID.randomUUID()}"): Artifact {
        val artifact = Artifact(
            trailId = trailId,
            imageName = "my-app",
            imageTag = "1.0.0",
            sha256Digest = sha256,
            reportedBy = "ci-bot"
        )
        return artifactRepository.save(artifact)
    }

    @Test
    fun `record provenance succeeds and returns response`() {
        val trail = makeTrail()
        val artifact = makeArtifact(trail.id)
        val req = RecordProvenanceRequest(
            builderId = "github-runner-1",
            builderType = BuilderType.GITHUB_ACTIONS,
            buildConfigUri = ".github/workflows/build.yml",
            sourceCommitSha = "abc123",
            slsaLevel = SlsaLevel.L2
        )

        val resp = service.recordProvenance(trail.id, artifact.id, req)

        assertEquals(artifact.id, resp.artifactId)
        assertEquals("github-runner-1", resp.builderId)
        assertEquals(BuilderType.GITHUB_ACTIONS, resp.builderType)
        assertEquals(SlsaLevel.L2, resp.slsaLevel)
        assertEquals(ProvenanceStatus.PROVENANCE_UNVERIFIED, resp.provenanceStatus)
    }

    @Test
    fun `record provenance with signature sets status to VERIFIED`() {
        val trail = makeTrail()
        val artifact = makeArtifact(trail.id)
        val req = RecordProvenanceRequest(
            builderId = "runner",
            builderType = BuilderType.GITHUB_ACTIONS,
            provenanceSignature = "MEQCIBz1abc..."
        )

        val resp = service.recordProvenance(trail.id, artifact.id, req)

        assertEquals(ProvenanceStatus.PROVENANCE_VERIFIED, resp.provenanceStatus)
    }

    @Test
    fun `record provenance for unknown trail throws NotFoundException`() {
        assertThrows<NotFoundException> {
            service.recordProvenance(
                UUID.randomUUID(), UUID.randomUUID(),
                RecordProvenanceRequest(builderId = "runner", builderType = BuilderType.GENERIC)
            )
        }
    }

    @Test
    fun `record provenance for artifact not belonging to trail throws NotFoundException`() {
        val trail1 = makeTrail()
        val trail2 = makeTrail()
        val artifact = makeArtifact(trail2.id)

        assertThrows<NotFoundException> {
            service.recordProvenance(
                trail1.id, artifact.id,
                RecordProvenanceRequest(builderId = "runner", builderType = BuilderType.GENERIC)
            )
        }
    }

    @Test
    fun `record provenance twice throws ConflictException`() {
        val trail = makeTrail()
        val artifact = makeArtifact(trail.id)
        val req = RecordProvenanceRequest(builderId = "runner", builderType = BuilderType.GENERIC)

        service.recordProvenance(trail.id, artifact.id, req)

        assertThrows<ConflictException> {
            service.recordProvenance(trail.id, artifact.id, req)
        }
    }

    @Test
    fun `get provenance returns recorded data`() {
        val trail = makeTrail()
        val artifact = makeArtifact(trail.id)
        service.recordProvenance(
            trail.id, artifact.id,
            RecordProvenanceRequest(
                builderId = "jenkins-1",
                builderType = BuilderType.JENKINS,
                sourceCommitSha = "deadbeef"
            )
        )

        val result = service.getProvenance(trail.id, artifact.id)

        assertEquals(artifact.id, result.artifactId)
        assertEquals("deadbeef", result.sourceCommitSha)
        assertEquals(BuilderType.JENKINS, result.builderType)
    }

    @Test
    fun `get provenance for artifact without provenance throws NotFoundException`() {
        val trail = makeTrail()
        val artifact = makeArtifact(trail.id)

        assertThrows<NotFoundException> {
            service.getProvenance(trail.id, artifact.id)
        }
    }

    @Test
    fun `get provenance by sha256 returns provenance for first matching artifact`() {
        val trail = makeTrail()
        val sha = "sha256:unique-abc"
        val artifact = makeArtifact(trail.id, sha256 = sha)
        service.recordProvenance(
            trail.id, artifact.id,
            RecordProvenanceRequest(builderId = "circle-1", builderType = BuilderType.CIRCLE_CI)
        )

        val result = service.getProvenanceBySha256(sha)

        assertEquals(artifact.id, result.artifactId)
        assertEquals(BuilderType.CIRCLE_CI, result.builderType)
    }

    @Test
    fun `get provenance by sha256 for unknown sha throws NotFoundException`() {
        assertThrows<NotFoundException> {
            service.getProvenanceBySha256("sha256:nonexistent")
        }
    }

    @Test
    fun `get provenance by sha256 for artifact without provenance throws NotFoundException`() {
        val trail = makeTrail()
        val sha = "sha256:no-prov"
        makeArtifact(trail.id, sha256 = sha)

        assertThrows<NotFoundException> {
            service.getProvenanceBySha256(sha)
        }
    }

    @Test
    fun `verify provenance returns NO_PROVENANCE when none recorded`() {
        val trail = makeTrail()
        val artifact = makeArtifact(trail.id)

        val result = service.verifyProvenance(trail.id, artifact.id)

        assertEquals(ProvenanceStatus.NO_PROVENANCE, result.provenanceStatus)
    }

    @Test
    fun `verify provenance returns UNVERIFIED without signature`() {
        val trail = makeTrail()
        val artifact = makeArtifact(trail.id)
        service.recordProvenance(
            trail.id, artifact.id,
            RecordProvenanceRequest(builderId = "runner", builderType = BuilderType.GENERIC)
        )

        val result = service.verifyProvenance(trail.id, artifact.id)

        assertEquals(ProvenanceStatus.PROVENANCE_UNVERIFIED, result.provenanceStatus)
    }

    @Test
    fun `verify provenance returns VERIFIED with non-blank signature`() {
        val trail = makeTrail()
        val artifact = makeArtifact(trail.id)
        service.recordProvenance(
            trail.id, artifact.id,
            RecordProvenanceRequest(
                builderId = "runner",
                builderType = BuilderType.GITHUB_ACTIONS,
                provenanceSignature = "MEQCIBz1..."
            )
        )

        val result = service.verifyProvenance(trail.id, artifact.id)

        assertEquals(ProvenanceStatus.PROVENANCE_VERIFIED, result.provenanceStatus)
    }

    @Test
    fun `verify provenance for unknown trail throws NotFoundException`() {
        assertThrows<NotFoundException> {
            service.verifyProvenance(UUID.randomUUID(), UUID.randomUUID())
        }
    }
}
