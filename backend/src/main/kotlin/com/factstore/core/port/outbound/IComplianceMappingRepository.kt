package com.factstore.core.port.outbound

import com.factstore.core.domain.ComplianceMapping
import java.util.UUID

interface IComplianceMappingRepository {
    fun save(mapping: ComplianceMapping): ComplianceMapping
    fun findById(id: UUID): ComplianceMapping?
    fun findAll(): List<ComplianceMapping>
    fun findByFlowId(flowId: UUID): List<ComplianceMapping>
    fun findByControlId(controlId: UUID): List<ComplianceMapping>
}
