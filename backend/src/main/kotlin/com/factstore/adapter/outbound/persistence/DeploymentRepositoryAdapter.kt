package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.Deployment
import com.factstore.core.port.outbound.IDeploymentRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class DeploymentRepositoryAdapter(
    private val jpa: DeploymentJpaRepository
) : IDeploymentRepository {
    override fun save(d: Deployment) = jpa.save(d)
    override fun findByArtifactSha256(sha256: String) = jpa.findByArtifactSha256(sha256)
    override fun findByEnvironmentId(id: UUID) = jpa.findByEnvironmentId(id)
    override fun existsByArtifactSha256AndEnvironmentId(sha256: String, id: UUID) =
        jpa.existsByArtifactSha256AndEnvironmentId(sha256, id)
}
