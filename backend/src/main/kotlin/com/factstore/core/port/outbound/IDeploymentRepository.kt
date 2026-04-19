package com.factstore.core.port.outbound

import com.factstore.core.domain.Deployment
import java.util.UUID

interface IDeploymentRepository {
    fun save(deployment: Deployment): Deployment
    fun findByArtifactSha256(sha256: String): List<Deployment>
    fun findByEnvironmentId(environmentId: UUID): List<Deployment>
    fun existsByArtifactSha256AndEnvironmentId(sha256: String, environmentId: UUID): Boolean
}
