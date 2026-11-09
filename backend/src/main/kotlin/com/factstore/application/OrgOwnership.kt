package com.factstore.application

import com.factstore.core.port.outbound.IEnvironmentRepository
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.ITrailRepository
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Which organisation a resource belongs to, for resources that record it only through a parent
 * (#207): a trail's own `orgSlug` or else its flow's, and an environment's.
 */
@Component
class OrgOwnership(
    private val trailRepository: ITrailRepository,
    private val flowRepository: IFlowRepository,
    private val environmentRepository: IEnvironmentRepository
) {
    fun ofTrail(trailId: UUID?): String? {
        val trail = trailId?.let { trailRepository.findById(it) } ?: return null
        return trail.orgSlug ?: flowRepository.findById(trail.flowId)?.orgSlug
    }

    fun ofEnvironment(environmentId: UUID?): String? =
        environmentId?.let { environmentRepository.findById(it)?.orgSlug }
}
