package com.factstore.application

import com.factstore.core.domain.Artifact
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.ProvenanceStatus
import com.factstore.core.port.inbound.IArtifactService
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.IBuildProvenanceRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.ArtifactResponse
import com.factstore.dto.CreateArtifactRequest
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class ArtifactService(
    private val artifactRepository: IArtifactRepository,
    private val trailRepository: ITrailRepository,
    private val auditService: IAuditService,
    private val buildProvenanceRepository: IBuildProvenanceRepository
) : IArtifactService {

    private val log = LoggerFactory.getLogger(ArtifactService::class.java)

    override fun reportArtifact(trailId: UUID, request: CreateArtifactRequest): ArtifactResponse {
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
        auditService.record(
            eventType = AuditEventType.ARTIFACT_DEPLOYED,
            actor = request.reportedBy,
            payload = mapOf(
                "artifactId" to saved.id.toString(),
                "trailId" to trailId.toString(),
                "imageName" to saved.imageName,
                "imageTag" to saved.imageTag,
                "sha256Digest" to saved.sha256Digest,
                "registry" to saved.registry
            ),
            trailId = trailId,
            artifactSha256 = saved.sha256Digest
        )
        log.info("Reported artifact: ${saved.id} digest=${saved.sha256Digest}")
        return saved.toResponse(ProvenanceStatus.NO_PROVENANCE)
    }

    @Transactional(readOnly = true)
    override fun listArtifactsForTrail(trailId: UUID): List<ArtifactResponse> {
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")
        val artifacts = artifactRepository.findByTrailId(trailId)
        val provenanceByArtifactId = buildProvenanceRepository
            .findByArtifactIdIn(artifacts.map { it.id })
            .associateBy { it.artifactId }
        return artifacts.map { artifact ->
            artifact.toResponse(provenanceByArtifactId[artifact.id]?.provenanceStatus() ?: ProvenanceStatus.NO_PROVENANCE)
        }
    }

    @Transactional(readOnly = true)
    override fun findBySha256(sha256Digest: String): List<ArtifactResponse> {
        val artifacts = artifactRepository.findBySha256Digest(sha256Digest)
        val provenanceByArtifactId = buildProvenanceRepository
            .findByArtifactIdIn(artifacts.map { it.id })
            .associateBy { it.artifactId }
        return artifacts.map { artifact ->
            artifact.toResponse(provenanceByArtifactId[artifact.id]?.provenanceStatus() ?: ProvenanceStatus.NO_PROVENANCE)
        }
    }
}

fun Artifact.toResponse(provenanceStatus: ProvenanceStatus = ProvenanceStatus.NO_PROVENANCE) = ArtifactResponse(
    id = id,
    trailId = trailId,
    imageName = imageName,
    imageTag = imageTag,
    sha256Digest = sha256Digest,
    registry = registry,
    reportedAt = reportedAt,
    reportedBy = reportedBy,
    provenanceStatus = provenanceStatus
)
