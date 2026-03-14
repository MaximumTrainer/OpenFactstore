package com.factstore.adapter.mock

import com.factstore.core.domain.ComplianceMapping
import com.factstore.core.port.outbound.IComplianceMappingRepository
import java.util.UUID

class InMemoryComplianceMappingRepository : IComplianceMappingRepository {
    private val store = mutableMapOf<UUID, ComplianceMapping>()
    override fun save(mapping: ComplianceMapping): ComplianceMapping { store[mapping.id] = mapping; return mapping }
    override fun findById(id: UUID): ComplianceMapping? = store[id]
    override fun findAll(): List<ComplianceMapping> = store.values.toList()
    override fun findByFlowId(flowId: UUID): List<ComplianceMapping> = store.values.filter { it.flowId == flowId }
    override fun findByControlId(controlId: UUID): List<ComplianceMapping> = store.values.filter { it.regulatoryControlId == controlId }
}
