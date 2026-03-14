package com.factstore.adapter.mock

import com.factstore.core.domain.ComplianceAssessment
import com.factstore.core.port.outbound.IComplianceAssessmentRepository
import java.util.UUID

class InMemoryComplianceAssessmentRepository : IComplianceAssessmentRepository {
    private val store = mutableMapOf<UUID, ComplianceAssessment>()
    override fun save(assessment: ComplianceAssessment): ComplianceAssessment { store[assessment.id] = assessment; return assessment }
    override fun findById(id: UUID): ComplianceAssessment? = store[id]
    override fun findAll(): List<ComplianceAssessment> = store.values.toList()
    override fun findByTrailId(trailId: UUID): List<ComplianceAssessment> = store.values.filter { it.trailId == trailId }
    override fun findByFrameworkId(frameworkId: UUID): List<ComplianceAssessment> = store.values.filter { it.frameworkId == frameworkId }
}
