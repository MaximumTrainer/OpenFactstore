package com.factstore.core.port.outbound

import com.factstore.core.domain.ComplianceAssessment
import java.util.UUID

interface IComplianceAssessmentRepository {
    fun save(assessment: ComplianceAssessment): ComplianceAssessment
    fun findById(id: UUID): ComplianceAssessment?
    fun findAll(): List<ComplianceAssessment>
    fun findByTrailId(trailId: UUID): List<ComplianceAssessment>
    fun findByFrameworkId(frameworkId: UUID): List<ComplianceAssessment>
}
