package com.factstore.application.command

import com.factstore.core.domain.Trail
import com.factstore.core.domain.event.DomainEvent
import com.factstore.core.port.inbound.command.ITrailCommandHandler
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.command.CommandResult
import com.factstore.dto.command.CreateTrailCommand
import com.factstore.exception.BadRequestException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class TrailCommandHandler(
    private val trailRepository: ITrailRepository,
    private val flowRepository: IFlowRepository,
    private val eventAppender: EventAppender,
    /** Strictly increasing creation times, so the latest run for a commit is well defined (#220). */
    private val time: com.factstore.application.MonotonicTime = com.factstore.application.MonotonicTime(java.time.Clock.systemUTC())
) : ITrailCommandHandler {

    private val log = LoggerFactory.getLogger(TrailCommandHandler::class.java)

    override fun createTrail(command: CreateTrailCommand): CommandResult {
        if (!flowRepository.existsById(command.flowId)) {
            throw NotFoundException("Flow not found: ${command.flowId}")
        }
        // Get-or-create on (flowId, externalId): a re-run of the pipeline that owns the release
        // must not fork the evidence, and no second TrailCreated event is appended (#164).
        command.externalId?.takeIf { it.isNotBlank() }?.let { externalId ->
            trailRepository.findByFlowIdAndExternalId(command.flowId, externalId)?.let { existing ->
                log.info("Reusing trail ${existing.id} for flow ${command.flowId} externalId '$externalId'")
                return CommandResult(id = existing.id, status = "exists")
            }
        }
        val trail = Trail(
            flowId = command.flowId,
            gitCommitSha = command.gitCommitSha
                ?: throw BadRequestException("gitCommitSha is required (or use X-Factstore-CI-Context header)"),
            gitBranch = command.gitBranch
                ?: throw BadRequestException("gitBranch is required (or use X-Factstore-CI-Context header)"),
            gitAuthor = command.gitAuthor,
            gitAuthorEmail = command.gitAuthorEmail,
            pullRequestId = command.pullRequestId,
            pullRequestReviewer = command.pullRequestReviewer,
            deploymentActor = command.deploymentActor,
            orgSlug = command.orgSlug,
            templateYaml = command.templateYaml,
            buildUrl = command.buildUrl,
            name = command.name,
            externalId = command.externalId?.takeIf { it.isNotBlank() },
            createdAt = time.now()
        )
        val saved = trailRepository.save(trail)
        eventAppender.append(DomainEvent.TrailCreated(
            aggregateId = saved.id,
            flowId = saved.flowId,
            gitCommitSha = saved.gitCommitSha,
            gitBranch = saved.gitBranch,
            gitAuthor = saved.gitAuthor,
            gitAuthorEmail = saved.gitAuthorEmail,
            pullRequestId = saved.pullRequestId,
            pullRequestReviewer = saved.pullRequestReviewer,
            deploymentActor = saved.deploymentActor,
            orgSlug = saved.orgSlug,
            templateYaml = saved.templateYaml,
            buildUrl = saved.buildUrl,
            name = saved.name,
            externalId = saved.externalId
        ))
        log.info("Created trail: ${saved.id} for flow: ${saved.flowId}")
        return CommandResult(id = saved.id, status = "created")
    }
}
