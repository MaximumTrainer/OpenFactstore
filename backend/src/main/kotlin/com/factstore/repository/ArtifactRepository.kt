package com.factstore.repository

import com.factstore.domain.Artifact
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ArtifactRepository : JpaRepository<Artifact, UUID> {
    fun findByTrailId(trailId: UUID): List<Artifact>
    fun findBySha256Digest(sha256Digest: String): List<Artifact>
}
