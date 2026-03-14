package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.BuildProvenance
import com.factstore.core.port.outbound.IBuildProvenanceRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BuildProvenanceRepositoryJpa : JpaRepository<BuildProvenance, UUID> {
    fun findByArtifactId(artifactId: UUID): BuildProvenance?
    fun findByArtifactIdIn(artifactIds: List<UUID>): List<BuildProvenance>
    fun existsByArtifactId(artifactId: UUID): Boolean
}

@Component
class BuildProvenanceRepositoryAdapter(private val jpa: BuildProvenanceRepositoryJpa) : IBuildProvenanceRepository {
    override fun save(provenance: BuildProvenance): BuildProvenance = jpa.save(provenance)
    override fun findByArtifactId(artifactId: UUID): BuildProvenance? = jpa.findByArtifactId(artifactId)
    override fun findByArtifactIdIn(artifactIds: List<UUID>): List<BuildProvenance> = jpa.findByArtifactIdIn(artifactIds)
    override fun existsByArtifactId(artifactId: UUID): Boolean = jpa.existsByArtifactId(artifactId)
}
