package com.factstore.application

import com.factstore.core.domain.Trail
import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.inbound.ITrailService
import com.factstore.core.port.inbound.TrailCreationResult
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.PageResponse
import com.factstore.dto.TrailResponse
import com.factstore.exception.BadRequestException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class TrailService(
    private val trailRepository: ITrailRepository,
    private val flowRepository: IFlowRepository,
    private val tenantScope: TenantScope = TenantScope.UNSCOPED
) : ITrailService {

    private val log = LoggerFactory.getLogger(TrailService::class.java)

    /** A trail's organisation is its own, or its flow's when it was created without one. */
    private fun orgOf(trail: Trail): String? =
        trail.orgSlug ?: flowRepository.findById(trail.flowId)?.orgSlug

    private fun visible(trail: Trail): Boolean = tenantScope.visible(orgOf(trail))

    /** Another organisation's trail is "not found" (#155 FR-5.2). */
    private fun findVisibleTrail(id: UUID): Trail =
        tenantScope.require(trailRepository.findById(id), ::orgOf) { "Trail not found: $id" }

    private fun requireVisibleFlow(flowId: UUID) {
        tenantScope.require(flowRepository.findById(flowId), { it.orgSlug }) { "Flow not found: $flowId" }
    }

    override fun createTrail(request: CreateTrailRequest): TrailResponse = createOrGetTrail(request).trail

    /**
     * Get-or-create on `(flowId, externalId)`.
     *
     * A release identifier makes trail creation idempotent (#164): a re-run of the primary
     * pipeline, or a secondary pipeline that starts first, attaches to the same trail instead of
     * forking the evidence for one release across several records. Without an `externalId` every
     * call creates a new trail, as before.
     */
    override fun createOrGetTrail(request: CreateTrailRequest): TrailCreationResult {
        if (!flowRepository.existsById(request.flowId)) {
            throw NotFoundException("Flow not found: ${request.flowId}")
        }
        request.externalId?.takeIf { it.isNotBlank() }?.let { externalId ->
            trailRepository.findByFlowIdAndExternalId(request.flowId, externalId)?.let { existing ->
                log.info("Reusing trail ${existing.id} for flow ${request.flowId} externalId '$externalId'")
                return TrailCreationResult(existing.toResponse(), created = false)
            }
        }
        val trail = Trail(
            flowId = request.flowId,
            gitCommitSha = request.gitCommitSha
                ?: throw BadRequestException("gitCommitSha is required (or use X-Factstore-CI-Context header)"),
            gitBranch = request.gitBranch
                ?: throw BadRequestException("gitBranch is required (or use X-Factstore-CI-Context header)"),
            gitAuthor = request.gitAuthor,
            gitAuthorEmail = request.gitAuthorEmail,
            pullRequestId = request.pullRequestId,
            pullRequestReviewer = request.pullRequestReviewer,
            deploymentActor = request.deploymentActor,
            orgSlug = request.orgSlug,
            templateYaml = request.templateYaml,
            buildUrl = request.buildUrl
        )
        trail.name = request.name
        trail.externalId = request.externalId?.takeIf { it.isNotBlank() }
        trail.tags = request.tags.toMutableMap()
        val saved = trailRepository.save(trail)
        log.info("Created trail: ${saved.id} for flow: ${saved.flowId}")
        return TrailCreationResult(saved.toResponse(), created = true)
    }

    @Transactional(readOnly = true)
    override fun listTrails(flowId: UUID?, includeArchived: Boolean): List<TrailResponse> {
        val trails = if (flowId != null) trailRepository.findByFlowId(flowId) else trailRepository.findAll()
        return trails.filter { (includeArchived || it.archivedAt == null) && visible(it) }.map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    override fun getTrail(id: UUID): TrailResponse =
        (findVisibleTrail(id)).toResponse()

    @Transactional(readOnly = true)
    override fun listTrailsForFlow(flowId: UUID, includeArchived: Boolean): List<TrailResponse> {
        requireVisibleFlow(flowId)
        return trailRepository.findByFlowId(flowId)
            .filter { includeArchived || it.archivedAt == null }
            .map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    override fun listTrailsForFlow(
        flowId: UUID,
        page: Int,
        size: Int,
        includeArchived: Boolean
    ): PageResponse<TrailResponse> {
        requireVisibleFlow(flowId)
        if (includeArchived) {
            val pageResult = trailRepository.findByFlowId(flowId, PageRequest.of(page, size))
            return PageResponse(
                items = pageResult.content.map { it.toResponse() },
                page = pageResult.number,
                size = pageResult.size,
                totalItems = pageResult.totalElements,
                totalPages = pageResult.totalPages
            )
        }
        // Archived trails are filtered out before paging, so the page counts stay honest.
        val active = trailRepository.findByFlowId(flowId).filter { it.archivedAt == null }
        val from = (page * size).coerceAtMost(active.size)
        val to = (from + size).coerceAtMost(active.size)
        val totalPages = if (size == 0) 0 else ((active.size + size - 1) / size)
        return PageResponse(
            items = active.subList(from, to).map { it.toResponse() },
            page = page,
            size = size,
            totalItems = active.size.toLong(),
            totalPages = totalPages
        )
    }

    override fun updateTrailStatus(id: UUID, status: TrailStatus): Trail {
        val trail = findVisibleTrail(id)
        trail.status = status
        trail.updatedAt = Instant.now()
        return trailRepository.save(trail)
    }

    override fun getTrailEntity(id: UUID): Trail =
        findVisibleTrail(id)

    /**
     * Resolves the trail a downstream pipeline was told to attest against. Exactly one selector
     * is expected; `gitCommitSha` returns the most recent trail for that commit, since a commit
     * may have many runs.
     */
    @Transactional(readOnly = true)
    override fun lookupTrail(
        flowId: UUID,
        externalId: String?,
        name: String?,
        gitCommitSha: String?
    ): TrailResponse {
        requireVisibleFlow(flowId)

        val trail = when {
            !externalId.isNullOrBlank() -> trailRepository.findByFlowIdAndExternalId(flowId, externalId)
            !name.isNullOrBlank() -> trailRepository.findByFlowIdAndName(flowId, name)
            !gitCommitSha.isNullOrBlank() ->
                trailRepository.findByFlowIdAndGitCommitSha(flowId, gitCommitSha).maxByOrNull { it.createdAt }
            else -> throw BadRequestException("One of externalId, name or gitCommitSha is required")
        }
        return (trail ?: throw NotFoundException(
            "No trail in flow $flowId matching externalId=$externalId name=$name gitCommitSha=$gitCommitSha"
        )).toResponse()
    }

    @Transactional(readOnly = true)
    override fun findByName(flowId: UUID, name: String): TrailResponse =
        (trailRepository.findByFlowIdAndName(flowId, name)
            ?: throw NotFoundException("Trail with name '$name' not found in flow $flowId")).toResponse()
}

fun Trail.toResponse() = TrailResponse(
    id = id,
    flowId = flowId,
    gitCommitSha = gitCommitSha,
    gitBranch = gitBranch,
    gitAuthor = gitAuthor,
    gitAuthorEmail = gitAuthorEmail,
    pullRequestId = pullRequestId,
    pullRequestReviewer = pullRequestReviewer,
    deploymentActor = deploymentActor,
    status = status,
    orgSlug = orgSlug,
    templateYaml = templateYaml,
    buildUrl = buildUrl,
    name = name,
    externalId = externalId,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    tags = tags.toMap()
)
