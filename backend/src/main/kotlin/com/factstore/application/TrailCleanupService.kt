package com.factstore.application

import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.Trail
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.inbound.ITrailCleanupService
import com.factstore.core.port.outbound.ITrailCleanupRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.CleanupMode
import com.factstore.dto.TrailCascadeCounts
import com.factstore.dto.TrailCleanupRequest
import com.factstore.dto.TrailCleanupResponse
import com.factstore.dto.TrailResponse
import com.factstore.exception.BadRequestException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class TrailCleanupService(
    private val trailRepository: ITrailRepository,
    private val trailCleanupRepository: ITrailCleanupRepository,
    private val auditService: IAuditService,
    private val actorResolver: ActorResolver
) : ITrailCleanupService {

    private val log = LoggerFactory.getLogger(TrailCleanupService::class.java)

    override fun archiveTrail(id: UUID): TrailResponse {
        val trail = require(id)
        if (trail.archivedAt == null) {
            trail.archivedAt = Instant.now()
            trail.updatedAt = Instant.now()
            trailRepository.save(trail)
            audit(AuditEventType.TRAIL_ARCHIVED, trail, mapOf("archivedAt" to trail.archivedAt.toString()))
            log.info("Archived trail $id")
        }
        return trail.toResponse()
    }

    override fun unarchiveTrail(id: UUID): TrailResponse {
        val trail = require(id)
        if (trail.archivedAt != null) {
            trail.archivedAt = null
            trail.updatedAt = Instant.now()
            trailRepository.save(trail)
            audit(AuditEventType.TRAIL_UNARCHIVED, trail, emptyMap())
            log.info("Unarchived trail $id")
        }
        return trail.toResponse()
    }

    @Transactional(readOnly = true)
    override fun cascadeFor(id: UUID): TrailCascadeCounts {
        require(id)
        return trailCleanupRepository.countCascade(id)
    }

    override fun cleanup(request: TrailCleanupRequest): TrailCleanupResponse {
        if (request.mode == CleanupMode.DELETE) {
            throw BadRequestException(
                "Trails are never deleted (#245); use mode ARCHIVE. Personal data is erased by shredding its data key (see docs/data-lifecycle.md)."
            )
        }
        if (request.flowId == null && request.tagKey == null && request.olderThan == null) {
            throw BadRequestException(
                "At least one of flowId, tagKey or olderThan is required so a cleanup cannot select every trail"
            )
        }

        val selected = select(request)
        val cascade = selected.fold(TrailCascadeCounts()) { total, trail ->
            total + trailCleanupRepository.countCascade(trail.id)
        }

        if (request.dryRun) {
            return TrailCleanupResponse(
                dryRun = true,
                mode = request.mode,
                trailCount = selected.size,
                trailIds = selected.map { it.id },
                cascade = cascade
            )
        }

        selected.forEach { trail -> archiveTrail(trail.id) }
        log.info("Cleanup ${request.mode} applied to ${selected.size} trails")

        return TrailCleanupResponse(
            dryRun = false,
            mode = request.mode,
            trailCount = selected.size,
            trailIds = selected.map { it.id },
            cascade = cascade
        )
    }

    private fun select(request: TrailCleanupRequest): List<Trail> {
        val candidates = request.flowId?.let { trailRepository.findByFlowId(it) } ?: trailRepository.findAll()
        return candidates.filter { trail ->
            val tagMatches = request.tagKey?.let { key ->
                val value = trail.tags[key]
                value != null && (request.tagValue == null || value == request.tagValue)
            } ?: true
            val ageMatches = request.olderThan?.let { trail.createdAt.isBefore(it) } ?: true
            // Archiving an already-archived trail is a no-op; leave it out of the count.
            val notAlreadyArchived = trail.archivedAt == null
            tagMatches && ageMatches && notAlreadyArchived
        }
    }

    private fun require(id: UUID): Trail =
        trailRepository.findById(id) ?: throw NotFoundException("Trail not found: $id")

    private fun audit(eventType: AuditEventType, trail: Trail, extra: Map<String, Any?>) {
        auditService.record(
            eventType = eventType,
            actor = actorResolver.current(),
            payload = mapOf(
                "trailId" to trail.id.toString(),
                "flowId" to trail.flowId.toString(),
                "gitCommitSha" to trail.gitCommitSha
            ) + extra,
            trailId = trail.id
        )
    }
}

private operator fun TrailCascadeCounts.plus(other: TrailCascadeCounts) = TrailCascadeCounts(
    attestations = attestations + other.attestations,
    artifacts = artifacts + other.artifacts,
    evidenceFiles = evidenceFiles + other.evidenceFiles,
    approvals = approvals + other.approvals,
    coverageReports = coverageReports + other.coverageReports,
    securityScans = securityScans + other.securityScans,
    complianceAssessments = complianceAssessments + other.complianceAssessments,
    jiraTickets = jiraTickets + other.jiraTickets
)

private fun TrailCascadeCounts.asMap(): Map<String, Int> = mapOf(
    "attestations" to attestations,
    "artifacts" to artifacts,
    "evidenceFiles" to evidenceFiles,
    "approvals" to approvals,
    "coverageReports" to coverageReports,
    "securityScans" to securityScans,
    "complianceAssessments" to complianceAssessments,
    "jiraTickets" to jiraTickets,
    "total" to total
)
