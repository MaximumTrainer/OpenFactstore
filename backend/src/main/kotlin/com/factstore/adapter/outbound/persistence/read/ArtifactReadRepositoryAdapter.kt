package com.factstore.adapter.outbound.persistence.read

import com.factstore.core.domain.Artifact
import com.factstore.core.domain.ProvenanceStatus
import com.factstore.core.port.outbound.read.IArtifactReadRepository
import com.factstore.adapter.outbound.persistence.ArtifactRepositoryJpa
import com.factstore.application.provenanceStatus
import com.factstore.core.port.outbound.IBuildProvenanceRepository
import com.factstore.dto.query.ArtifactView
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ArtifactReadRepositoryAdapter(
    private val jpa: ArtifactRepositoryJpa,
    private val buildProvenanceRepository: IBuildProvenanceRepository
) : IArtifactReadRepository {

    override fun findByTrailId(trailId: UUID): List<ArtifactView> {
        val artifacts = jpa.findByTrailId(trailId)
        val provenanceByArtifactId = buildProvenanceRepository
            .findByArtifactIdIn(artifacts.map { it.id })
            .associateBy { it.artifactId }
        return artifacts.map { artifact ->
            artifact.toView(provenanceByArtifactId[artifact.id]?.provenanceStatus() ?: ProvenanceStatus.NO_PROVENANCE)
        }
    }

    override fun findBySha256Digest(sha256Digest: String): List<ArtifactView> {
        val artifacts = jpa.findBySha256Digest(sha256Digest)
        val provenanceByArtifactId = buildProvenanceRepository
            .findByArtifactIdIn(artifacts.map { it.id })
            .associateBy { it.artifactId }
        return artifacts.map { artifact ->
            artifact.toView(provenanceByArtifactId[artifact.id]?.provenanceStatus() ?: ProvenanceStatus.NO_PROVENANCE)
        }
    }
}

fun Artifact.toView(provenanceStatus: ProvenanceStatus = ProvenanceStatus.NO_PROVENANCE) = ArtifactView(
    id = id,
    trailId = trailId,
    imageName = imageName,
    imageTag = imageTag,
    sha256Digest = sha256Digest,
    registry = registry,
    reportedAt = reportedAt,
    reportedBy = reportedBy,
    orgSlug = orgSlug,
    provenanceStatus = provenanceStatus
)
