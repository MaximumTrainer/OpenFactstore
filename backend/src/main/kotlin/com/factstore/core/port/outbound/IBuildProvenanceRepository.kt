package com.factstore.core.port.outbound

import com.factstore.core.domain.BuildProvenance
import java.util.UUID

interface IBuildProvenanceRepository {
    fun save(provenance: BuildProvenance): BuildProvenance
    fun findByArtifactId(artifactId: UUID): BuildProvenance?
    fun findByArtifactIdIn(artifactIds: List<UUID>): List<BuildProvenance>
    fun existsByArtifactId(artifactId: UUID): Boolean
}
