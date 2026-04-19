package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.Deployment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DeploymentJpaRepository : JpaRepository<Deployment, UUID> {
    fun findByArtifactSha256(sha256: String): List<Deployment>
    fun findByEnvironmentId(environmentId: UUID): List<Deployment>
    fun existsByArtifactSha256AndEnvironmentId(sha256: String, environmentId: UUID): Boolean
}
