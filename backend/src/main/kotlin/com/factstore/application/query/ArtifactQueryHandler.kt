package com.factstore.application.query

import com.factstore.core.port.inbound.query.IArtifactQueryHandler
import com.factstore.core.port.outbound.read.IArtifactReadRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.query.ArtifactView
import com.factstore.exception.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ArtifactQueryHandler(
    private val artifactReadRepository: IArtifactReadRepository,
    private val trailRepository: ITrailRepository
) : IArtifactQueryHandler {

    override fun listArtifactsForTrail(trailId: UUID): List<ArtifactView> {
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")
        return artifactReadRepository.findByTrailId(trailId)
    }

    override fun findBySha256(sha256Digest: String): List<ArtifactView> =
        artifactReadRepository.findBySha256Digest(sha256Digest)
}
