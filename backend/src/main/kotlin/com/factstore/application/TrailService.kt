package com.factstore.application

import com.factstore.core.domain.Trail
import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.inbound.ITrailService
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
    private val flowRepository: IFlowRepository
) : ITrailService {

    private val log = LoggerFactory.getLogger(TrailService::class.java)

    override fun createTrail(request: CreateTrailRequest): TrailResponse {
        if (!flowRepository.existsById(request.flowId)) {
            throw NotFoundException("Flow not found: ${request.flowId}")
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
        val saved = trailRepository.save(trail)
        log.info("Created trail: ${saved.id} for flow: ${saved.flowId}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listTrails(flowId: UUID?): List<TrailResponse> =
        if (flowId != null) trailRepository.findByFlowId(flowId).map { it.toResponse() }
        else trailRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getTrail(id: UUID): TrailResponse =
        (trailRepository.findById(id) ?: throw NotFoundException("Trail not found: $id")).toResponse()

    @Transactional(readOnly = true)
    override fun listTrailsForFlow(flowId: UUID): List<TrailResponse> {
        if (!flowRepository.existsById(flowId)) throw NotFoundException("Flow not found: $flowId")
        return trailRepository.findByFlowId(flowId).map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    override fun listTrailsForFlow(flowId: UUID, page: Int, size: Int): PageResponse<TrailResponse> {
        if (!flowRepository.existsById(flowId)) throw NotFoundException("Flow not found: $flowId")
        val pageResult = trailRepository.findByFlowId(flowId, PageRequest.of(page, size))
        return PageResponse(
            items = pageResult.content.map { it.toResponse() },
            page = pageResult.number,
            size = pageResult.size,
            totalItems = pageResult.totalElements,
            totalPages = pageResult.totalPages
        )
    }

    override fun updateTrailStatus(id: UUID, status: TrailStatus): Trail {
        val trail = trailRepository.findById(id) ?: throw NotFoundException("Trail not found: $id")
        trail.status = status
        trail.updatedAt = Instant.now()
        return trailRepository.save(trail)
    }

    override fun getTrailEntity(id: UUID): Trail =
        trailRepository.findById(id) ?: throw NotFoundException("Trail not found: $id")

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
    createdAt = createdAt,
    updatedAt = updatedAt
)
