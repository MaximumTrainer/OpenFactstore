package com.factstore.application.command

import com.factstore.core.domain.Trail
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
    private val flowRepository: IFlowRepository
) : ITrailCommandHandler {

    private val log = LoggerFactory.getLogger(TrailCommandHandler::class.java)

    override fun createTrail(command: CreateTrailCommand): CommandResult {
        if (!flowRepository.existsById(command.flowId)) {
            throw NotFoundException("Flow not found: ${command.flowId}")
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
            buildUrl = command.buildUrl
        )
        val saved = trailRepository.save(trail)
        log.info("Created trail: ${saved.id} for flow: ${saved.flowId}")
        return CommandResult(id = saved.id, status = "created")
    }
}
