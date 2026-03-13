package com.factstore.core.port.outbound

import com.factstore.core.domain.Artifact
import java.util.UUID

interface IArtifactRepository {
    fun save(artifact: Artifact): Artifact
    fun findByTrailId(trailId: UUID): List<Artifact>
    fun findBySha256Digest(sha256Digest: String): List<Artifact>
    fun findBySha256DigestStartingWith(prefix: String): List<Artifact>
    fun findAll(): List<Artifact>
    fun searchByQuery(query: String): List<Artifact>
}
