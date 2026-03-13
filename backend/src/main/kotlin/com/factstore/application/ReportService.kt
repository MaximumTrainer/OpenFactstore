package com.factstore.application

import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.inbound.IReportService
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IEvidenceFileRepository
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.AuditTrailExportResponse
import com.factstore.dto.FlowComplianceReport
import com.factstore.dto.TrailComplianceSummary
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Computes compliance rate as a percentage rounded to two decimal places. */
internal fun complianceRateOf(compliant: Int, total: Int): Double =
    if (total == 0) 0.0 else Math.round((compliant.toDouble() / total) * 10000.0) / 100.0

@Service
@Transactional(readOnly = true)
class ReportService(
    private val flowRepository: IFlowRepository,
    private val trailRepository: ITrailRepository,
    private val artifactRepository: IArtifactRepository,
    private val attestationRepository: IAttestationRepository,
    private val evidenceFileRepository: IEvidenceFileRepository
) : IReportService {

    private val log = LoggerFactory.getLogger(ReportService::class.java)

    override fun getComplianceReport(flowId: UUID?, from: Instant?, to: Instant?): FlowComplianceReport {
        if (flowId != null && !flowRepository.existsById(flowId)) {
            throw NotFoundException("Flow not found: $flowId")
        }

        val trails = when {
            flowId != null && from != null && to != null ->
                trailRepository.findByFlowIdAndCreatedAtBetween(flowId, from, to)
            flowId != null ->
                trailRepository.findByFlowId(flowId).let { list ->
                    if (from != null) list.filter { it.createdAt >= from } else list
                }.let { list ->
                    if (to != null) list.filter { it.createdAt <= to } else list
                }
            else ->
                trailRepository.findAll().let { list ->
                    if (from != null) list.filter { it.createdAt >= from } else list
                }.let { list ->
                    if (to != null) list.filter { it.createdAt <= to } else list
                }
        }

        val compliant = trails.count { it.status == TrailStatus.COMPLIANT }
        val nonCompliant = trails.count { it.status == TrailStatus.NON_COMPLIANT }
        val pending = trails.count { it.status == TrailStatus.PENDING }
        val total = trails.size

        val complianceRate = complianceRateOf(compliant, total)

        val nonCompliantList = trails
            .filter { it.status == TrailStatus.NON_COMPLIANT }
            .map { trail ->
                TrailComplianceSummary(
                    id = trail.id,
                    gitCommitSha = trail.gitCommitSha,
                    gitBranch = trail.gitBranch,
                    gitAuthor = trail.gitAuthor,
                    status = trail.status.name,
                    createdAt = trail.createdAt
                )
            }

        val flowName = flowId?.let {
            flowRepository.findById(it)?.name ?: "Unknown"
        } ?: "All Flows"

        log.info("Compliance report: flowId=$flowId total=$total compliant=$compliant")
        return FlowComplianceReport(
            flowId = flowId,
            flowName = flowName,
            from = from,
            to = to,
            totalTrails = total,
            compliantTrails = compliant,
            nonCompliantTrails = nonCompliant,
            pendingTrails = pending,
            complianceRate = complianceRate,
            nonCompliantTrailList = nonCompliantList
        )
    }

    override fun getAuditTrailExport(trailId: UUID): AuditTrailExportResponse {
        val trail = trailRepository.findById(trailId) ?: throw NotFoundException("Trail not found: $trailId")
        val flow = flowRepository.findById(trail.flowId) ?: throw NotFoundException("Flow not found: ${trail.flowId}")
        val artifacts = artifactRepository.findByTrailId(trailId)
        val attestations = attestationRepository.findByTrailId(trailId)
        val evidenceFiles = attestations.flatMap { evidenceFileRepository.findByAttestationId(it.id) }

        log.info("Audit trail export for trailId=$trailId")
        return AuditTrailExportResponse(
            trailId = trailId,
            exportedAt = Instant.now(),
            trail = trail.toResponse(),
            flow = flow.toResponse(),
            artifacts = artifacts.map { it.toResponse() },
            attestations = attestations.map { it.toResponse() },
            evidenceFiles = evidenceFiles.map { it.toResponse() }
        )
    }
}
