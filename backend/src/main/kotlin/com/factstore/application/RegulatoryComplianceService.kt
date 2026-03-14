package com.factstore.application

import com.factstore.core.domain.*
import com.factstore.core.port.inbound.IRegulatoryComplianceService
import com.factstore.core.port.outbound.*
import com.factstore.dto.*
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class RegulatoryComplianceService(
    private val frameworkRepository: IRegulatoryFrameworkRepository,
    private val controlRepository: IRegulatoryControlRepository,
    private val mappingRepository: IComplianceMappingRepository,
    private val assessmentRepository: IComplianceAssessmentRepository,
    private val attestationRepository: IAttestationRepository,
    private val trailRepository: ITrailRepository
) : IRegulatoryComplianceService {

    private val log = LoggerFactory.getLogger(RegulatoryComplianceService::class.java)
    private val objectMapper = jacksonObjectMapper()

    @PostConstruct
    fun seedBuiltinFrameworks() {
        seedFramework(
            name = "PCI-DSS v4.0",
            version = "4.0",
            description = "Payment Card Industry Data Security Standard v4.0",
            controls = listOf(
                Triple("PCI-6.3.3", "Apply security patches within defined timelines", listOf("SECURITY_SCAN", "PATCH_ATTESTATION")),
                Triple("PCI-6.4.1", "Web-facing applications protected against attacks", listOf("DAST_SCAN", "SECURITY_SCAN")),
                Triple("PCI-11.3.1", "Internal vulnerability scans performed periodically", listOf("SECURITY_SCAN")),
                Triple("PCI-12.3.4", "Hardware and software reviewed for security vulnerabilities", listOf("SECURITY_SCAN", "BUILD_ATTESTATION"))
            )
        )
        seedFramework(
            name = "SOX",
            version = "2002",
            description = "Sarbanes-Oxley Act IT General Controls",
            controls = listOf(
                Triple("SOX-ITGC-1", "Change management controls for production systems", listOf("CHANGE_APPROVAL", "DEPLOYMENT_ATTESTATION")),
                Triple("SOX-ITGC-2", "Access controls and segregation of duties", listOf("ACCESS_REVIEW", "APPROVAL")),
                Triple("SOX-ITGC-3", "Software development lifecycle controls", listOf("CODE_REVIEW", "BUILD_ATTESTATION")),
                Triple("SOX-ITGC-4", "Audit logging and monitoring", listOf("AUDIT_LOG_ATTESTATION"))
            )
        )
        seedFramework(
            name = "GDPR",
            version = "2018",
            description = "General Data Protection Regulation compliance controls",
            controls = listOf(
                Triple("GDPR-25", "Data protection by design and by default", listOf("PRIVACY_REVIEW", "SECURITY_SCAN")),
                Triple("GDPR-32", "Security of processing — technical measures", listOf("SECURITY_SCAN", "ENCRYPTION_ATTESTATION")),
                Triple("GDPR-35", "Data protection impact assessment", listOf("DPIA_ATTESTATION")),
                Triple("GDPR-33", "Notification of personal data breach procedures", listOf("INCIDENT_RESPONSE_ATTESTATION"))
            )
        )
    }

    private fun seedFramework(name: String, version: String, description: String, controls: List<Triple<String, String, List<String>>>) {
        if (frameworkRepository.existsByName(name)) return
        val framework = frameworkRepository.save(RegulatoryFramework(name = name, version = version, description = description))
        controls.forEach { (controlId, title, evidenceTypes) ->
            controlRepository.save(
                RegulatoryControl(
                    frameworkId = framework.id,
                    controlId = controlId,
                    title = title,
                    requiredEvidenceTypes = evidenceTypes.joinToString("||")
                )
            )
        }
        log.info("Seeded built-in framework: $name")
    }

    override fun createFramework(request: CreateFrameworkRequest): FrameworkResponse {
        val framework = frameworkRepository.save(
            RegulatoryFramework(
                name = request.name,
                version = request.version,
                description = request.description,
                orgSlug = request.orgSlug
            )
        )
        log.info("Created regulatory framework: ${framework.id} name=${framework.name}")
        return framework.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listFrameworks(): List<FrameworkResponse> =
        frameworkRepository.findAll().map { it.toResponse(controlRepository.findByFrameworkId(it.id)) }

    @Transactional(readOnly = true)
    override fun getFramework(id: UUID): FrameworkResponse {
        val framework = frameworkRepository.findById(id) ?: throw NotFoundException("Framework not found: $id")
        val controls = controlRepository.findByFrameworkId(id)
        return framework.toResponse(controls)
    }

    override fun addControl(frameworkId: UUID, request: CreateControlRequest): ControlResponse {
        frameworkRepository.findById(frameworkId) ?: throw NotFoundException("Framework not found: $frameworkId")
        val control = controlRepository.save(
            RegulatoryControl(
                frameworkId = frameworkId,
                controlId = request.controlId,
                title = request.title,
                description = request.description,
                requiredEvidenceTypes = request.requiredEvidenceTypes.joinToString("||").ifEmpty { null }
            )
        )
        return control.toResponse()
    }

    override fun createMapping(request: CreateMappingRequest): MappingResponse {
        controlRepository.findById(request.regulatoryControlId)
            ?: throw NotFoundException("Control not found: ${request.regulatoryControlId}")
        val mapping = mappingRepository.save(
            ComplianceMapping(
                regulatoryControlId = request.regulatoryControlId,
                flowId = request.flowId,
                attestationType = request.attestationType,
                isMandatory = request.isMandatory
            )
        )
        return mapping.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listMappings(): List<MappingResponse> =
        mappingRepository.findAll().map { it.toResponse() }

    override fun assessTrail(request: AssessTrailRequest): AssessmentResponse {
        val framework = frameworkRepository.findById(request.frameworkId)
            ?: throw NotFoundException("Framework not found: ${request.frameworkId}")
        trailRepository.findById(request.trailId)
            ?: throw NotFoundException("Trail not found: ${request.trailId}")

        val controls = controlRepository.findByFrameworkId(framework.id)
        val attestations = attestationRepository.findByTrailId(request.trailId)
        val passedTypes = attestations
            .filter { it.status == AttestationStatus.PASSED }
            .map { it.type }
            .toSet()

        val controlResults = controls.map { control ->
            val required = control.requiredEvidenceTypes
                ?.split("||")
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            val satisfied = required.filter { it in passedTypes }
            val missing = required.filter { it !in passedTypes }

            val status = when {
                required.isEmpty() || missing.isEmpty() -> "SATISFIED"
                satisfied.isEmpty() -> "NOT_SATISFIED"
                else -> "PARTIAL"
            }

            ControlResult(
                controlId = control.controlId,
                title = control.title,
                status = status,
                satisfiedBy = satisfied,
                missingEvidence = missing
            )
        }

        val mandatoryControls = controls.filter { c ->
            // A control is considered mandatory unless all mappings for it are non-mandatory
            val mappings = mappingRepository.findByControlId(c.id)
            mappings.isEmpty() || mappings.any { it.isMandatory }
        }

        val mandatoryResults = controlResults.filter { cr ->
            mandatoryControls.any { it.controlId == cr.controlId }
        }

        val overallStatus = when {
            mandatoryResults.isEmpty() -> AssessmentStatus.COMPLIANT
            mandatoryResults.all { it.status == "SATISFIED" } -> AssessmentStatus.COMPLIANT
            mandatoryResults.all { it.status == "NOT_SATISFIED" } -> AssessmentStatus.NON_COMPLIANT
            else -> AssessmentStatus.PARTIAL
        }

        val controlResultsJson = objectMapper.writeValueAsString(controlResults)

        val assessment = assessmentRepository.save(
            ComplianceAssessment(
                frameworkId = framework.id,
                trailId = request.trailId,
                overallStatus = overallStatus,
                controlResultsJson = controlResultsJson,
                orgSlug = request.orgSlug
            )
        )

        log.info("Assessed trail ${request.trailId} against framework ${framework.name}: $overallStatus")
        return assessment.toResponse(controlResults)
    }

    @Transactional(readOnly = true)
    override fun listAssessments(): List<AssessmentResponse> =
        assessmentRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getAssessment(id: UUID): AssessmentResponse {
        val assessment = assessmentRepository.findById(id)
            ?: throw NotFoundException("Assessment not found: $id")
        return assessment.toResponse()
    }

    @Transactional(readOnly = true)
    override fun generateReport(frameworkId: UUID): RegulatoryReportResponse {
        val framework = frameworkRepository.findById(frameworkId)
            ?: throw NotFoundException("Framework not found: $frameworkId")
        val assessments = assessmentRepository.findByFrameworkId(frameworkId)
        return RegulatoryReportResponse(
            frameworkId = framework.id,
            frameworkName = framework.name,
            frameworkVersion = framework.version,
            assessments = assessments.map { it.toResponse() },
            generatedAt = Instant.now()
        )
    }

    private fun ComplianceAssessment.toResponse(results: List<ControlResult>? = null): AssessmentResponse {
        val controlResults = results ?: run {
            if (controlResultsJson != null) {
                try { objectMapper.readValue<List<ControlResult>>(controlResultsJson!!) } catch (e: Exception) { emptyList() }
            } else emptyList()
        }
        return AssessmentResponse(
            id = id,
            frameworkId = frameworkId,
            trailId = trailId,
            overallStatus = overallStatus,
            controlResults = controlResults,
            orgSlug = orgSlug,
            assessedAt = assessedAt
        )
    }
}

fun RegulatoryFramework.toResponse(controls: List<RegulatoryControl> = emptyList()) = FrameworkResponse(
    id = id,
    name = name,
    version = version,
    description = description,
    isActive = isActive,
    orgSlug = orgSlug,
    controls = controls.map { it.toResponse() },
    createdAt = createdAt
)

fun RegulatoryControl.toResponse() = ControlResponse(
    id = id,
    frameworkId = frameworkId,
    controlId = controlId,
    title = title,
    description = description,
    requiredEvidenceTypes = requiredEvidenceTypes?.split("||")?.filter { it.isNotBlank() } ?: emptyList(),
    createdAt = createdAt
)

fun ComplianceMapping.toResponse() = MappingResponse(
    id = id,
    regulatoryControlId = regulatoryControlId,
    flowId = flowId,
    attestationType = attestationType,
    isMandatory = isMandatory,
    createdAt = createdAt
)
