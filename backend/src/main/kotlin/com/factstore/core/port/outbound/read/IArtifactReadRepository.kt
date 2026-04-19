package com.factstore.core.port.outbound.read

import com.factstore.dto.query.ArtifactView
import java.util.UUID

interface IArtifactReadRepository {
    fun findByTrailId(trailId: UUID): List<ArtifactView>
    fun findBySha256Digest(sha256Digest: String): List<ArtifactView>
}
