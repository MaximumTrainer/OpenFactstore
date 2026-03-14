package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.EnvironmentBaseline
import com.factstore.core.port.outbound.IEnvironmentBaselineRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EnvironmentBaselineRepositoryJpa : JpaRepository<EnvironmentBaseline, UUID> {
    fun findByEnvironmentIdAndIsActiveTrue(environmentId: UUID): EnvironmentBaseline?
    fun findAllByEnvironmentId(environmentId: UUID): List<EnvironmentBaseline>
}

@Component
class EnvironmentBaselineRepositoryAdapter(
    private val jpa: EnvironmentBaselineRepositoryJpa
) : IEnvironmentBaselineRepository {
    override fun save(baseline: EnvironmentBaseline): EnvironmentBaseline = jpa.save(baseline)
    override fun findById(id: UUID): EnvironmentBaseline? = jpa.findById(id).orElse(null)
    override fun findActiveByEnvironmentId(environmentId: UUID): EnvironmentBaseline? =
        jpa.findByEnvironmentIdAndIsActiveTrue(environmentId)
    override fun findAllByEnvironmentId(environmentId: UUID): List<EnvironmentBaseline> =
        jpa.findAllByEnvironmentId(environmentId)
}
