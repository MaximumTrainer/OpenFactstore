package com.factstore.application

import com.factstore.core.domain.*
import com.factstore.core.port.inbound.IWebhookService
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.core.port.outbound.IWebhookConfigRepository
import com.factstore.core.port.outbound.IWebhookDeliveryRepository
import com.factstore.dto.WebhookResponse
import com.factstore.exception.BadRequestException
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
class WebhookService(
    private val webhookConfigRepository: IWebhookConfigRepository,
    private val webhookDeliveryRepository: IWebhookDeliveryRepository,
    private val trailRepository: ITrailRepository,
    private val attestationRepository: IAttestationRepository,
    private val objectMapper: ObjectMapper
) : IWebhookService {

    private val log = LoggerFactory.getLogger(WebhookService::class.java)

    override fun processWebhook(
        source: String,
        payload: String,
        signature: String?,
        deliveryId: String?
    ): WebhookResponse {
        val webhookSource = parseSource(source)
        val configs = webhookConfigRepository.findAll()
            .filter { it.source == webhookSource && it.isActive }

        if (configs.isEmpty()) {
            throw BadRequestException("No active webhook config found for source: $source")
        }

        val config = findMatchingConfig(configs, payload, signature)
            ?: throw BadRequestException("Signature verification failed for source: $source")

        val resolvedDeliveryId = deliveryId ?: UUID.randomUUID().toString()

        if (webhookDeliveryRepository.existsByDeliveryIdAndWebhookConfigId(resolvedDeliveryId, config.id)) {
            log.info("Duplicate webhook delivery: $resolvedDeliveryId for config: ${config.id}")
            return WebhookResponse(accepted = true, deliveryId = resolvedDeliveryId, message = "Duplicate delivery, already processed")
        }

        return try {
            val event = parseEvent(webhookSource, payload)
            routeEvent(event, config)

            val delivery = WebhookDelivery(
                webhookConfigId = config.id,
                deliveryId = resolvedDeliveryId,
                source = webhookSource,
                eventType = event.eventType,
                status = DeliveryStatus.SUCCESS,
                statusMessage = "Event processed successfully"
            )
            webhookDeliveryRepository.save(delivery)
            log.info("Processed webhook: source=$source deliveryId=$resolvedDeliveryId eventType=${event.eventType}")
            WebhookResponse(accepted = true, deliveryId = resolvedDeliveryId, message = "Event processed: ${event.eventType}")
        } catch (e: Exception) {
            val delivery = WebhookDelivery(
                webhookConfigId = config.id,
                deliveryId = resolvedDeliveryId,
                source = webhookSource,
                eventType = null,
                status = DeliveryStatus.FAILED,
                statusMessage = e.message ?: "Unknown error"
            )
            webhookDeliveryRepository.save(delivery)
            log.error("Failed to process webhook: source=$source deliveryId=$resolvedDeliveryId", e)
            throw e
        }
    }

    internal fun parseSource(source: String): WebhookSource {
        return try {
            WebhookSource.valueOf(source.uppercase())
        } catch (e: IllegalArgumentException) {
            throw BadRequestException("Unknown webhook source: $source. Valid sources: ${WebhookSource.entries.joinToString()}")
        }
    }

    internal fun findMatchingConfig(configs: List<WebhookConfig>, payload: String, signature: String?): WebhookConfig? {
        if (signature == null) {
            return configs.firstOrNull()
        }
        return configs.firstOrNull { config ->
            verifySignature(payload, signature, config.secretHash)
        }
    }

    internal fun verifySignature(payload: String, signature: String, secretHash: String): Boolean {
        return try {
            val rawSignature = signature.removePrefix("sha256=")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secretHash.toByteArray(), "HmacSHA256"))
            val computed = mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
            computed.equals(rawSignature, ignoreCase = true)
        } catch (e: Exception) {
            log.warn("Signature verification error: ${e.message}")
            false
        }
    }

    internal fun parseEvent(source: WebhookSource, payload: String): WebhookEvent {
        return when (source) {
            WebhookSource.GENERIC -> GenericWebhookParser(objectMapper).parse(payload)
            WebhookSource.GITHUB -> GitHubWebhookParser(objectMapper).parse(payload)
            else -> GenericWebhookParser(objectMapper).parse(payload)
        }
    }

    internal fun routeEvent(event: WebhookEvent, config: WebhookConfig) {
        when (event.eventType) {
            "build.started" -> handleBuildStarted(event, config)
            "build.succeeded", "build.failed" -> handleAttestation(event, config)
            "test.passed", "test.failed" -> handleAttestation(event, config)
            "scan.passed", "scan.failed" -> handleAttestation(event, config)
            "deployment.triggered" -> handleDeployment(event, config)
            "approval.granted" -> handleApproval(event, config)
            else -> log.warn("Unhandled event type: ${event.eventType}")
        }
    }

    private fun handleBuildStarted(event: WebhookEvent, config: WebhookConfig) {
        val trail = findOrCreateTrail(event, config)
        log.info("Build started for trail: ${trail.id}")
    }

    private fun handleAttestation(event: WebhookEvent, config: WebhookConfig) {
        val trail = findOrCreateTrail(event, config)
        val attestationType = event.attestationType ?: return
        val attestationStatus = event.attestationStatus ?: return

        val attestation = Attestation(
            trailId = trail.id,
            type = attestationType,
            status = attestationStatus,
            details = event.details
        )
        attestationRepository.save(attestation)

        if (attestationStatus == AttestationStatus.FAILED) {
            trail.status = TrailStatus.NON_COMPLIANT
            trail.updatedAt = Instant.now()
            trailRepository.save(trail)
        }

        log.info("Recorded attestation: type=$attestationType status=$attestationStatus for trail: ${trail.id}")
    }

    private fun handleDeployment(event: WebhookEvent, config: WebhookConfig) {
        val trail = findOrCreateTrail(event, config)
        trail.deploymentActor = event.actor
        trail.updatedAt = Instant.now()
        trailRepository.save(trail)
        log.info("Recorded deployment for trail: ${trail.id}")
    }

    private fun handleApproval(event: WebhookEvent, config: WebhookConfig) {
        val trail = findOrCreateTrail(event, config)
        trail.pullRequestReviewer = event.actor
        trail.updatedAt = Instant.now()
        trailRepository.save(trail)
        log.info("Recorded approval for trail: ${trail.id}")
    }

    private fun findOrCreateTrail(event: WebhookEvent, config: WebhookConfig): Trail {
        if (event.trailId != null) {
            val existing = trailRepository.findById(event.trailId)
            if (existing != null) return existing
        }

        if (event.commitSha != null) {
            val existing = trailRepository.findByFlowId(config.flowId)
                .firstOrNull { it.gitCommitSha == event.commitSha }
            if (existing != null) return existing
        }

        val trail = Trail(
            flowId = config.flowId,
            gitCommitSha = event.commitSha ?: "unknown",
            gitBranch = event.branch ?: "unknown",
            gitAuthor = event.actor ?: "webhook",
            gitAuthorEmail = event.actorEmail ?: "webhook@factstore"
        )
        return trailRepository.save(trail)
    }
}
