package com.factstore.application

import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.CoverageReport
import com.factstore.core.port.inbound.IEvidenceCollectionService
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.ICoverageReportRepository
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.BulkEvidenceItem
import com.factstore.dto.BulkEvidenceRequest
import com.factstore.dto.BulkEvidenceResponse
import com.factstore.dto.BulkEvidenceResult
import com.factstore.dto.CoverageReportResponse
import com.factstore.dto.EvidenceGapItem
import com.factstore.dto.EvidenceGapsResponse
import com.factstore.dto.EvidenceSummaryResponse
import com.factstore.dto.ReportCoverageRequest
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class EvidenceCollectionService(
    private val trailRepository: ITrailRepository,
    private val flowRepository: IFlowRepository,
    private val attestationRepository: IAttestationRepository,
    private val coverageReportRepository: ICoverageReportRepository
) : IEvidenceCollectionService {

    private val log = LoggerFactory.getLogger(EvidenceCollectionService::class.java)

    override fun reportCoverage(trailId: UUID, request: ReportCoverageRequest): CoverageReportResponse {
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")
        val passed = determineCoveragePassed(request)
        val report = CoverageReport(
            trailId = trailId,
            tool = request.tool,
            lineCoverage = request.lineCoverage,
            branchCoverage = request.branchCoverage,
            minCoverage = request.minCoverage,
            passed = passed,
            reportFileName = request.reportFileName,
            details = request.details
        )
        val saved = coverageReportRepository.save(report)

        // Record as a "test-coverage" attestation (base type) so compliance evaluation
        // against Flow.requiredAttestationTypes works with exact matching. Tool details
        // are captured in the attestation's details field.
        val attestationStatus = if (passed) AttestationStatus.PASSED else AttestationStatus.FAILED
        val attestation = Attestation(
            trailId = trailId,
            type = "test-coverage",
            status = attestationStatus,
            details = buildCoverageDetails(request, passed)
        )
        attestationRepository.save(attestation)

        if (!passed) {
            markTrailNonCompliant(trailId)
        }

        log.info("Reported coverage for trail: $trailId tool=${request.tool} passed=$passed")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun getCoverageReports(trailId: UUID): List<CoverageReportResponse> {
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")
        return coverageReportRepository.findByTrailId(trailId).map { it.toResponse() }
    }

    override fun collectBulkEvidence(request: BulkEvidenceRequest): BulkEvidenceResponse {
        val results = mutableListOf<BulkEvidenceResult>()
        var failed = 0

        for (item in request.items) {
            try {
                val result = processBulkEvidenceItem(item)
                results.add(result)
            } catch (e: Exception) {
                log.warn("Failed to process bulk evidence item for trail ${item.trailId}: ${e.message}")
                failed++
            }
        }

        log.info("Bulk evidence collection: accepted=${results.size} failed=$failed")
        return BulkEvidenceResponse(results = results, accepted = results.size, failed = failed)
    }

    @Transactional(readOnly = true)
    override fun getEvidenceSummary(trailId: UUID): EvidenceSummaryResponse {
        val trail = trailRepository.findById(trailId) ?: throw NotFoundException("Trail not found: $trailId")
        val flow = flowRepository.findById(trail.flowId) ?: throw NotFoundException("Flow not found: ${trail.flowId}")
        val attestations = attestationRepository.findByTrailId(trailId)
        val coverageReports = coverageReportRepository.findByTrailId(trailId)

        val collectedTypes = attestations.map { it.type }.distinct()
        val requiredTypes = flow.requiredAttestationTypes
        val missingTypes = requiredTypes.filter { required -> required !in collectedTypes }
        val isComplete = missingTypes.isEmpty()

        return EvidenceSummaryResponse(
            trailId = trailId,
            collectedTypes = collectedTypes,
            coverageReports = coverageReports.map { it.toResponse() },
            totalAttestations = attestations.size,
            passedAttestations = attestations.count { it.status == AttestationStatus.PASSED },
            failedAttestations = attestations.count { it.status == AttestationStatus.FAILED },
            pendingAttestations = attestations.count { it.status == AttestationStatus.PENDING },
            isComplete = isComplete,
            missingRequiredTypes = missingTypes
        )
    }

    @Transactional(readOnly = true)
    override fun getEvidenceGaps(): EvidenceGapsResponse {
        val trails = trailRepository.findAll()
        if (trails.isEmpty()) return EvidenceGapsResponse(gaps = emptyList(), totalTrailsWithGaps = 0)

        // Bulk-load flows and attestations to avoid N+1 queries
        val flowIds = trails.map { it.flowId }.toSet()
        val flowsById = flowRepository.findAllByIds(flowIds).associateBy { it.id }

        val trailIdsWithRequirements = trails
            .filter { (flowsById[it.flowId]?.requiredAttestationTypes ?: emptyList()).isNotEmpty() }
            .map { it.id }

        val attestationsByTrailId = if (trailIdsWithRequirements.isEmpty()) emptyMap()
        else attestationRepository.findByTrailIdIn(trailIdsWithRequirements)
            .groupBy { it.trailId }

        val gaps = mutableListOf<EvidenceGapItem>()
        for (trail in trails) {
            val flow = flowsById[trail.flowId] ?: continue
            val requiredTypes = flow.requiredAttestationTypes
            if (requiredTypes.isEmpty()) continue

            val collectedTypes = (attestationsByTrailId[trail.id] ?: emptyList()).map { it.type }.distinct()
            val missingTypes = requiredTypes.filter { required -> required !in collectedTypes }

            if (missingTypes.isNotEmpty()) {
                gaps.add(
                    EvidenceGapItem(
                        trailId = trail.id,
                        gitCommitSha = trail.gitCommitSha,
                        gitBranch = trail.gitBranch,
                        flowId = trail.flowId,
                        missingTypes = missingTypes,
                        trailStatus = trail.status
                    )
                )
            }
        }

        return EvidenceGapsResponse(gaps = gaps, totalTrailsWithGaps = gaps.size)
    }

    private fun processBulkEvidenceItem(item: BulkEvidenceItem): BulkEvidenceResult {
        if (!trailRepository.existsById(item.trailId)) throw NotFoundException("Trail not found: ${item.trailId}")
        val status = if (item.passed) AttestationStatus.PASSED else AttestationStatus.FAILED
        val attestation = Attestation(
            trailId = item.trailId,
            type = item.evidenceType,
            status = status,
            details = item.details ?: "Collected via bulk evidence pipeline (tool: ${item.tool})"
        )
        val saved = attestationRepository.save(attestation)
        if (!item.passed) {
            markTrailNonCompliant(item.trailId)
        }
        return BulkEvidenceResult(
            trailId = item.trailId,
            evidenceType = item.evidenceType,
            attestationId = saved.id,
            passed = item.passed
        )
    }

    private fun markTrailNonCompliant(trailId: UUID) {
        val trail = trailRepository.findById(trailId) ?: return
        trail.status = com.factstore.core.domain.TrailStatus.NON_COMPLIANT
        trail.updatedAt = Instant.now()
        trailRepository.save(trail)
    }

    private fun determineCoveragePassed(request: ReportCoverageRequest): Boolean {
        val min = request.minCoverage ?: return true
        val line = request.lineCoverage
        val branch = request.branchCoverage
        // When a minimum threshold is set but no coverage metrics are provided, we
        // cannot verify compliance — treat as failed rather than silently passing.
        return when {
            line != null && branch != null -> line >= min && branch >= min
            line != null -> line >= min
            branch != null -> branch >= min
            else -> false
        }
    }

    private fun buildCoverageDetails(request: ReportCoverageRequest, passed: Boolean): String {
        val parts = mutableListOf<String>()
        request.lineCoverage?.let { parts.add("line=${it}%") }
        request.branchCoverage?.let { parts.add("branch=${it}%") }
        request.minCoverage?.let { parts.add("min=${it}%") }
        parts.add(if (passed) "PASSED" else "FAILED")
        return parts.joinToString(", ")
    }
}

fun CoverageReport.toResponse() = CoverageReportResponse(
    id = id,
    trailId = trailId,
    tool = tool,
    lineCoverage = lineCoverage,
    branchCoverage = branchCoverage,
    minCoverage = minCoverage,
    passed = passed,
    reportFileName = reportFileName,
    reportFileHash = reportFileHash,
    details = details,
    createdAt = createdAt
)
