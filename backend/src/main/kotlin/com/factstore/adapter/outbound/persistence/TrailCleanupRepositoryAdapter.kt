package com.factstore.adapter.outbound.persistence

import com.factstore.core.port.outbound.ITrailCleanupRepository
import com.factstore.dto.TrailCascadeCounts
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Implements the trail cascade (#161) over the existing JPA repositories.
 *
 * Rows are loaded and deleted as entities rather than with bulk JPQL so that element
 * collections — trail tags, attestation annotations, artifact tags — go with their owner.
 * A trail carries tens of rows, not thousands, so this is the right trade.
 *
 * Order matters: children before parents, because the schema's `ON DELETE` behaviour is
 * inconsistent — `approvals` and `coverage_reports` cascade, `artifacts`, `attestations` and
 * `jira_tickets` do not, and `security_scan_results` and `compliance_assessments` have no
 * foreign key at all and would simply be orphaned.
 */
@Component
class TrailCleanupRepositoryAdapter(
    private val trails: TrailRepositoryJpa,
    private val attestations: AttestationRepositoryJpa,
    private val artifacts: ArtifactRepositoryJpa,
    private val evidenceFiles: EvidenceFileRepositoryJpa,
    private val approvals: ApprovalRepositoryJpa,
    private val coverageReports: CoverageReportRepositoryJpa,
    private val securityScans: SecurityScanRepositoryJpa,
    private val complianceAssessments: ComplianceAssessmentRepositoryJpa,
    private val jiraTickets: JiraTicketRepositoryJpa
) : ITrailCleanupRepository {

    @Transactional(readOnly = true)
    override fun countCascade(trailId: UUID): TrailCascadeCounts {
        val trailAttestations = attestations.findByTrailId(trailId)
        return TrailCascadeCounts(
            attestations = trailAttestations.size,
            artifacts = artifacts.findByTrailId(trailId).size,
            evidenceFiles = trailAttestations.sumOf { evidenceFiles.findByAttestationId(it.id).size },
            approvals = approvals.findByTrailId(trailId).size,
            coverageReports = coverageReports.findByTrailId(trailId).size,
            securityScans = securityScans.findByTrailId(trailId).size,
            complianceAssessments = complianceAssessments.findByTrailId(trailId).size,
            jiraTickets = jiraTickets.findByTrailId(trailId).size
        )
    }
}
