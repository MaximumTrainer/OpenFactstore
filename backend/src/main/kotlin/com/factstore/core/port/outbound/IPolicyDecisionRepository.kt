package com.factstore.core.port.outbound

import com.factstore.core.domain.PolicyDecision
import java.util.UUID

interface IPolicyDecisionRepository {
    fun save(decision: PolicyDecision): PolicyDecision
    fun findById(id: UUID): PolicyDecision?
    fun findAll(): List<PolicyDecision>
}
