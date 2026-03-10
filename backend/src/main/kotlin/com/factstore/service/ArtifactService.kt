package com.factstore.service

import com.factstore.domain.Artifact
import com.factstore.dto.ArtifactResponse
import com.factstore.dto.CreateArtifactRequest
import com.factstore.exception.NotFoundException
import com.factstore.repository.ArtifactRepository
import com.factstore.repository.TrailRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class ArtifactService(
    private val artifactRepository: ArtifactRepository,
    private val trailRepository: TrailRepository
) {
    private val log = LoggerFactory.getLogger(ArtifactService::class.java)

    fun reportArtifact(trailId: UUID, request: CreateArtifactRequest): ArtifactResponse {
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")
        val artifact = Artifact(
            trailId = trailId,
            imageName = request.imageName,
            imageTag = request.imageTag,
            sha256Digest = request.sha256Digest,
            registry = request.registry,
            reportedBy = request.reportedBy
        )
        val saved = artifactRepository.save(artifact)
        log.info("Reported artifact: ${saved.id} digest=${saved.sha256Digest}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun listArtifactsForTrail(trailId: UUID): List<ArtifactResponse> {
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")
        return artifactRepository.findByTrailId(trailId).map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun findBySha256(sha256Digest: String): List<ArtifactResponse> =
        artifactRepository.findBySha256Digest(sha256Digest).map { it.toResponse() }
}

fun Artifact.toResponse() = ArtifactResponse(
    id = id,
    trailId = trailId,
    imageName = imageName,
    imageTag = imageTag,
    sha256Digest = sha256Digest,
    registry = registry,
    reportedAt = reportedAt,
    reportedBy = reportedBy
)
