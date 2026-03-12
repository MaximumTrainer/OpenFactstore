package com.factstore.application

import com.factstore.core.domain.SlackConfig
import com.factstore.core.port.inbound.ISlackService
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.ISlackConfigRepository
import com.factstore.core.port.outbound.ISlackNotificationSender
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.ConfigureSlackRequest
import com.factstore.dto.SlackCommandResponse
import com.factstore.dto.SlackConfigResponse
import com.factstore.dto.SlackNotification
import com.factstore.exception.BadRequestException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
@Transactional
class SlackService(
    private val slackConfigRepository: ISlackConfigRepository,
    private val artifactRepository: IArtifactRepository,
    private val trailRepository: ITrailRepository,
    private val attestationRepository: IAttestationRepository,
    private val flowRepository: IFlowRepository,
    private val slackNotificationSender: ISlackNotificationSender,
    private val objectMapper: ObjectMapper
) : ISlackService {

    private val log = LoggerFactory.getLogger(SlackService::class.java)
    private val commandParser = SlackCommandParser()
    private val messageFormatter = SlackMessageFormatter()

    override fun configureSlack(orgSlug: String, request: ConfigureSlackRequest): SlackConfigResponse {
        val existing = slackConfigRepository.findByOrgSlug(orgSlug)
        if (existing != null) {
            existing.botToken = request.botToken
            existing.signingSecret = request.signingSecret
            existing.channel = request.channel
            existing.updatedAt = Instant.now()
            return slackConfigRepository.save(existing).toResponse()
        }
        val config = SlackConfig(
            orgSlug = orgSlug,
            botToken = request.botToken,
            signingSecret = request.signingSecret,
            channel = request.channel
        )
        return slackConfigRepository.save(config).toResponse()
    }

    override fun removeSlack(orgSlug: String) {
        if (!slackConfigRepository.existsByOrgSlug(orgSlug)) {
            throw NotFoundException("No Slack configuration found for organisation: $orgSlug")
        }
        slackConfigRepository.deleteByOrgSlug(orgSlug)
        log.info("Removed Slack config for org: $orgSlug")
    }

    @Transactional(readOnly = true)
    override fun getConfig(orgSlug: String): SlackConfigResponse =
        (slackConfigRepository.findByOrgSlug(orgSlug)
            ?: throw NotFoundException("No Slack configuration found for organisation: $orgSlug"))
            .toResponse()

    @Transactional(readOnly = true)
    override fun verifySlackRequest(orgSlug: String, timestamp: String?, signature: String?, rawBody: String) {
        val config = slackConfigRepository.findByOrgSlug(orgSlug)
            ?: throw NotFoundException("No Slack configuration found for organisation: $orgSlug")

        if (timestamp.isNullOrBlank() || signature.isNullOrBlank()) {
            throw BadRequestException("Missing Slack request signature headers")
        }

        val requestTimestamp = timestamp.toLongOrNull()
            ?: throw BadRequestException("Invalid X-Slack-Request-Timestamp: $timestamp")

        // Replay attack prevention: reject requests older than 5 minutes
        if (kotlin.math.abs(Instant.now().epochSecond - requestTimestamp) > 300) {
            throw BadRequestException("Slack request timestamp is too old")
        }

        // Compute expected signature: v0=HMAC-SHA256("v0:{timestamp}:{body}", signingSecret)
        val sigBaseString = "v0:$timestamp:$rawBody"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(config.signingSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val computed = "v0=" + mac.doFinal(sigBaseString.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        if (!computed.equals(signature, ignoreCase = true)) {
            throw BadRequestException("Invalid Slack request signature")
        }
    }

    @Transactional(readOnly = true)
    override fun handleSlashCommand(
        orgSlug: String,
        text: String,
        userId: String,
        userName: String
    ): SlackCommandResponse {
        val command = commandParser.parse(text)
        val responseText = when (command) {
            is SlackCommand.Help -> messageFormatter.formatHelp()
            is SlackCommand.Search -> handleSearch(command.shaPrefix)
            is SlackCommand.Env -> "Environment snapshots are not yet supported. Coming soon!"
            is SlackCommand.TrailDetails -> handleTrailDetails(command.trailId)
            is SlackCommand.Approve -> handleApprove(command.approvalId, command.comment, userName)
            is SlackCommand.Reject -> handleReject(command.approvalId, command.comment, userName)
            is SlackCommand.Unknown -> messageFormatter.formatUnknownCommand(command.input)
        }
        return SlackCommandResponse(text = responseText)
    }

    override fun handleInteractiveAction(orgSlug: String, payloadJson: String): SlackCommandResponse {
        val payload = try {
            objectMapper.readTree(payloadJson)
        } catch (e: Exception) {
            log.warn("Failed to parse Slack interactive action payload: ${e.message}")
            return SlackCommandResponse(text = "Invalid payload: unable to parse JSON")
        }
        val actions = payload.get("actions")
            ?: return SlackCommandResponse(text = "No actions found in payload")
        val firstAction = actions.firstOrNull()
            ?: return SlackCommandResponse(text = "No actions found")
        val actionId = firstAction.get("action_id")?.asText() ?: ""
        val value = firstAction.get("value")?.asText() ?: ""
        val userName = payload.get("user")?.get("username")?.asText() ?: "unknown"

        return when (actionId) {
            "approve_release" -> SlackCommandResponse(text = handleApprove(value, null, userName))
            "reject_release" -> SlackCommandResponse(text = handleReject(value, null, userName))
            else -> SlackCommandResponse(text = "Unknown action: $actionId")
        }
    }

    override fun sendNotification(orgSlug: String, notification: SlackNotification): Boolean {
        val config = slackConfigRepository.findByOrgSlug(orgSlug)
            ?: throw NotFoundException("No Slack configuration found for organisation: $orgSlug")
        val message = when (notification) {
            is SlackNotification.TrailNonCompliant -> messageFormatter.formatTrailNonCompliant(
                notification.trailId, notification.flowName,
                notification.missingAttestationTypes, notification.failedAttestationTypes,
                notification.trailUrl
            )
            is SlackNotification.ApprovalRequested -> messageFormatter.formatApprovalRequested(
                notification.approvalId, notification.artifactSha,
                notification.targetEnvironment, notification.requiredApprovers
            )
            is SlackNotification.ApprovalDecision -> messageFormatter.formatApprovalDecision(
                notification.approvalId, notification.decision,
                notification.decidedBy, notification.comment
            )
        }
        return slackNotificationSender.send(config.botToken, config.channel, message)
    }

    private fun handleSearch(shaPrefix: String): String {
        // Limit the number of results processed in-memory to avoid loading an unbounded set
        val maxResultsToProcess = 11
        val artifacts = artifactRepository.findBySha256DigestStartingWith(shaPrefix).take(maxResultsToProcess)
        return messageFormatter.formatArtifactSearch(shaPrefix, artifacts.map { it.toResponse() })
    }

    private fun handleTrailDetails(trailId: UUID): String {
        val trail = trailRepository.findById(trailId)
            ?: return "Trail not found: `$trailId`"
        val flow = flowRepository.findById(trail.flowId)
            ?: return "Flow not found for trail: `$trailId`"
        val attestations = attestationRepository.findByTrailId(trailId)
        return messageFormatter.formatTrailDetails(
            trail.toResponse(),
            flow.toResponse(),
            attestations.map { it.toResponse() }
        )
    }

    private fun handleApprove(approvalId: String, comment: String?, userName: String): String {
        log.info("Approval granted: approvalId=$approvalId by=$userName comment=$comment")
        return messageFormatter.formatApprovalDecision(approvalId, "approved", userName, comment)
    }

    private fun handleReject(approvalId: String, comment: String?, userName: String): String {
        log.info("Approval rejected: approvalId=$approvalId by=$userName comment=$comment")
        return messageFormatter.formatApprovalDecision(approvalId, "rejected", userName, comment)
    }
}

fun SlackConfig.toResponse() = SlackConfigResponse(
    id = id,
    orgSlug = orgSlug,
    channel = channel,
    createdAt = createdAt,
    updatedAt = updatedAt
)
