package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.SecurityThreshold
import com.factstore.core.port.outbound.ISecurityThresholdRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SecurityThresholdRepositoryJpa : JpaRepository<SecurityThreshold, UUID> {
    fun findByFlowId(flowId: UUID): SecurityThreshold?
}

@Component
class SecurityThresholdRepositoryAdapter(private val jpa: SecurityThresholdRepositoryJpa) : ISecurityThresholdRepository {
    override fun save(threshold: SecurityThreshold): SecurityThreshold = jpa.save(threshold)
    override fun findById(id: UUID): SecurityThreshold? = jpa.findById(id).orElse(null)
    override fun findByFlowId(flowId: UUID): SecurityThreshold? = jpa.findByFlowId(flowId)
}
