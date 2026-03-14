package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.ComplianceMapping
import com.factstore.core.port.outbound.IComplianceMappingRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ComplianceMappingRepositoryJpa : JpaRepository<ComplianceMapping, UUID> {
    fun findByFlowId(flowId: UUID): List<ComplianceMapping>
    fun findByRegulatoryControlId(controlId: UUID): List<ComplianceMapping>
}

@Component
class ComplianceMappingRepositoryAdapter(private val jpa: ComplianceMappingRepositoryJpa) : IComplianceMappingRepository {
    override fun save(mapping: ComplianceMapping): ComplianceMapping = jpa.save(mapping)
    override fun findById(id: UUID): ComplianceMapping? = jpa.findById(id).orElse(null)
    override fun findAll(): List<ComplianceMapping> = jpa.findAll()
    override fun findByFlowId(flowId: UUID): List<ComplianceMapping> = jpa.findByFlowId(flowId)
    override fun findByControlId(controlId: UUID): List<ComplianceMapping> = jpa.findByRegulatoryControlId(controlId)
}
