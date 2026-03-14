package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.ComplianceAssessment
import com.factstore.core.port.outbound.IComplianceAssessmentRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ComplianceAssessmentRepositoryJpa : JpaRepository<ComplianceAssessment, UUID> {
    fun findByTrailId(trailId: UUID): List<ComplianceAssessment>
    fun findByFrameworkId(frameworkId: UUID): List<ComplianceAssessment>
}

@Component
class ComplianceAssessmentRepositoryAdapter(private val jpa: ComplianceAssessmentRepositoryJpa) : IComplianceAssessmentRepository {
    override fun save(assessment: ComplianceAssessment): ComplianceAssessment = jpa.save(assessment)
    override fun findById(id: UUID): ComplianceAssessment? = jpa.findById(id).orElse(null)
    override fun findAll(): List<ComplianceAssessment> = jpa.findAll()
    override fun findByTrailId(trailId: UUID): List<ComplianceAssessment> = jpa.findByTrailId(trailId)
    override fun findByFrameworkId(frameworkId: UUID): List<ComplianceAssessment> = jpa.findByFrameworkId(frameworkId)
}
