package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.PolicyDecision
import com.factstore.core.port.outbound.IPolicyDecisionRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PolicyDecisionRepositoryJpa : JpaRepository<PolicyDecision, UUID>

@Component
class PolicyDecisionRepositoryAdapter(private val jpa: PolicyDecisionRepositoryJpa) : IPolicyDecisionRepository {
    override fun save(decision: PolicyDecision): PolicyDecision = jpa.save(decision)
    override fun findById(id: UUID): PolicyDecision? = jpa.findById(id).orElse(null)
    override fun findAll(): List<PolicyDecision> = jpa.findAll()
}
