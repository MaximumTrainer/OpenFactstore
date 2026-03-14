package com.factstore.application

import com.factstore.core.domain.BuildProvenance
import com.factstore.core.domain.ProvenanceStatus
import com.factstore.core.port.inbound.IBuildProvenanceService
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.IBuildProvenanceRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.BuildProvenanceResponse
import com.factstore.dto.ProvenanceVerificationResponse
import com.factstore.dto.RecordProvenanceRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class BuildProvenanceService(
    private val provenanceRepository: IBuildProvenanceRepository,
    private val artifactRepository: IArtifactRepository,
    private val trailRepository: ITrailRepository
) : IBuildProvenanceService {

    private val log = LoggerFactory.getLogger(BuildProvenanceService::class.java)

    override fun recordProvenance(trailId: UUID, artifactId: UUID, request: RecordProvenanceRequest): BuildProvenanceResponse {
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")
        val artifact = artifactRepository.findById(artifactId)
            ?: throw NotFoundException("Artifact not found: $artifactId")
        if (artifact.trailId != trailId) throw NotFoundException("Artifact $artifactId does not belong to trail $trailId")
        if (provenanceRepository.existsByArtifactId(artifactId)) throw ConflictException("Provenance already recorded for artifact: $artifactId")

        val provenance = BuildProvenance(
            artifactId = artifactId,
            builderId = request.builderId,
            builderType = request.builderType,
            buildConfigUri = request.buildConfigUri,
            sourceRepositoryUri = request.sourceRepositoryUri,
            sourceCommitSha = request.sourceCommitSha,
            buildStartedOn = request.buildStartedOn,
            buildFinishedOn = request.buildFinishedOn,
            provenanceSignature = request.provenanceSignature,
            slsaLevel = request.slsaLevel
        )
        val saved = provenanceRepository.save(provenance)
        log.info("Recorded build provenance for artifact: $artifactId slsaLevel=${saved.slsaLevel}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun getProvenance(trailId: UUID, artifactId: UUID): BuildProvenanceResponse {
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")
        val artifact = artifactRepository.findById(artifactId)
            ?: throw NotFoundException("Artifact not found: $artifactId")
        if (artifact.trailId != trailId) throw NotFoundException("Artifact $artifactId does not belong to trail $trailId")
        val provenance = provenanceRepository.findByArtifactId(artifactId)
            ?: throw NotFoundException("No provenance recorded for artifact: $artifactId")
        return provenance.toResponse()
    }

    @Transactional(readOnly = true)
    override fun getProvenanceBySha256(sha256Digest: String): BuildProvenanceResponse {
        val artifacts = artifactRepository.findBySha256Digest(sha256Digest)
        if (artifacts.isEmpty()) throw NotFoundException("No artifact found with SHA256: $sha256Digest")
        val artifact = artifacts.first()
        val provenance = provenanceRepository.findByArtifactId(artifact.id)
            ?: throw NotFoundException("No provenance recorded for artifact with SHA256: $sha256Digest")
        return provenance.toResponse()
    }

    override fun verifyProvenance(trailId: UUID, artifactId: UUID): ProvenanceVerificationResponse {
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")
        val artifact = artifactRepository.findById(artifactId)
            ?: throw NotFoundException("Artifact not found: $artifactId")
        if (artifact.trailId != trailId) throw NotFoundException("Artifact $artifactId does not belong to trail $trailId")
        val provenance = provenanceRepository.findByArtifactId(artifactId)
            ?: return ProvenanceVerificationResponse(
                artifactId = artifactId,
                provenanceStatus = ProvenanceStatus.NO_PROVENANCE,
                message = "No provenance recorded for this artifact."
            )
        val status = provenance.provenanceStatus()
        val message = when (status) {
            ProvenanceStatus.PROVENANCE_VERIFIED -> "Provenance signature is present and the artifact is considered verified."
            ProvenanceStatus.PROVENANCE_UNVERIFIED -> "Provenance recorded but no cryptographic signature found."
            ProvenanceStatus.NO_PROVENANCE -> "No provenance recorded for this artifact."
        }
        log.info("Verified provenance for artifact: $artifactId status=$status")
        return ProvenanceVerificationResponse(artifactId = artifactId, provenanceStatus = status, message = message)
    }
}

fun BuildProvenance.provenanceStatus(): ProvenanceStatus =
    if (!provenanceSignature.isNullOrBlank()) ProvenanceStatus.PROVENANCE_VERIFIED
    else ProvenanceStatus.PROVENANCE_UNVERIFIED

fun BuildProvenance.toResponse() = BuildProvenanceResponse(
    id = id,
    artifactId = artifactId,
    builderId = builderId,
    builderType = builderType,
    buildConfigUri = buildConfigUri,
    sourceRepositoryUri = sourceRepositoryUri,
    sourceCommitSha = sourceCommitSha,
    buildStartedOn = buildStartedOn,
    buildFinishedOn = buildFinishedOn,
    provenanceSignature = provenanceSignature,
    slsaLevel = slsaLevel,
    provenanceStatus = provenanceStatus(),
    recordedAt = recordedAt
)
