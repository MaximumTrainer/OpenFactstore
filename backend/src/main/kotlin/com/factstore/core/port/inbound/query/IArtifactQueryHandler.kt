package com.factstore.core.port.inbound.query

import com.factstore.dto.query.ArtifactView
import java.util.UUID

interface IArtifactQueryHandler {
    fun listArtifactsForTrail(trailId: UUID): List<ArtifactView>
    fun findBySha256(sha256Digest: String): List<ArtifactView>
}
