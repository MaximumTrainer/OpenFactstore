package com.factstore.service

import com.factstore.domain.Trail
import com.factstore.domain.TrailStatus
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.TrailResponse
import com.factstore.exception.NotFoundException
import com.factstore.repository.FlowRepository
import com.factstore.repository.TrailRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class TrailService(
    private val trailRepository: TrailRepository,
    private val flowRepository: FlowRepository
) {
    private val log = LoggerFactory.getLogger(TrailService::class.java)

    fun createTrail(request: CreateTrailRequest): TrailResponse {
        if (!flowRepository.existsById(request.flowId)) {
            throw NotFoundException("Flow not found: ${request.flowId}")
        }
        val trail = Trail(
            flowId = request.flowId,
            gitCommitSha = request.gitCommitSha,
            gitBranch = request.gitBranch,
            gitAuthor = request.gitAuthor,
            gitAuthorEmail = request.gitAuthorEmail,
            pullRequestId = request.pullRequestId,
            pullRequestReviewer = request.pullRequestReviewer,
            deploymentActor = request.deploymentActor
        )
        val saved = trailRepository.save(trail)
        log.info("Created trail: ${saved.id} for flow: ${saved.flowId}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun listTrails(flowId: UUID?): List<TrailResponse> =
        if (flowId != null) trailRepository.findByFlowId(flowId).map { it.toResponse() }
        else trailRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    fun getTrail(id: UUID): TrailResponse =
        trailRepository.findById(id).orElseThrow { NotFoundException("Trail not found: $id") }.toResponse()

    @Transactional(readOnly = true)
    fun listTrailsForFlow(flowId: UUID): List<TrailResponse> {
        if (!flowRepository.existsById(flowId)) throw NotFoundException("Flow not found: $flowId")
        return trailRepository.findByFlowId(flowId).map { it.toResponse() }
    }

    fun updateTrailStatus(id: UUID, status: TrailStatus): Trail {
        val trail = trailRepository.findById(id).orElseThrow { NotFoundException("Trail not found: $id") }
        trail.status = status
        trail.updatedAt = Instant.now()
        return trailRepository.save(trail)
    }

    fun getTrailEntity(id: UUID): Trail =
        trailRepository.findById(id).orElseThrow { NotFoundException("Trail not found: $id") }
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
    createdAt = createdAt,
    updatedAt = updatedAt
)
