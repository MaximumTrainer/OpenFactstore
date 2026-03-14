package com.factstore.core.port.inbound

import com.factstore.dto.*
import java.util.UUID

interface IRegulatoryComplianceService {
    fun createFramework(request: CreateFrameworkRequest): FrameworkResponse
    fun listFrameworks(): List<FrameworkResponse>
    fun getFramework(id: UUID): FrameworkResponse
    fun addControl(frameworkId: UUID, request: CreateControlRequest): ControlResponse

    fun createMapping(request: CreateMappingRequest): MappingResponse
    fun listMappings(): List<MappingResponse>

    fun assessTrail(request: AssessTrailRequest): AssessmentResponse
    fun listAssessments(): List<AssessmentResponse>
    fun getAssessment(id: UUID): AssessmentResponse

    fun generateReport(frameworkId: UUID): RegulatoryReportResponse
}
