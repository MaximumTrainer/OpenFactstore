package com.factstore.application

import com.factstore.core.domain.*
import com.factstore.core.domain.event.DomainEvent
import com.factstore.core.domain.event.DomainEventRegistry
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Applies [DomainEvent]s to JPA entities in the (read) database.
 *
 * In a fully decoupled CQRS deployment the query service runs this
 * projector against events received from the domain event bus (RabbitMQ or
 * in-memory).  Each event type maps to a create / update / delete
 * operation on the relevant aggregate table.
 *
 * The projector is idempotent with respect to create operations — if an
 * entity with the given aggregate ID already exists the event is skipped.
 */
@Component
class ReadModelProjector(
    private val flowRepository: IFlowRepository,
    private val trailRepository: ITrailRepository,
    private val artifactRepository: IArtifactRepository,
    private val attestationRepository: IAttestationRepository,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(ReadModelProjector::class.java)

    private val eventTypeMap = DomainEventRegistry.eventTypeMap

    /**
     * Deserialise a JSON payload into a [DomainEvent] and apply it.
     * Returns `true` when the event was successfully projected.
     */
    @Transactional
    fun project(eventType: String, payload: String): Boolean {
        val clazz = eventTypeMap[eventType]
        if (clazz == null) {
            log.warn("Unknown event type '{}' — skipping projection", eventType)
            return false
        }
        return try {
            val event = objectMapper.readValue(payload, clazz)
            apply(event)
            true
        } catch (e: Exception) {
            log.error("Failed to project event type={}", eventType, e)
            false
        }
    }

    @Transactional
    fun apply(event: DomainEvent) {
        when (event) {
            is DomainEvent.FlowCreated -> applyFlowCreated(event)
            is DomainEvent.FlowUpdated -> applyFlowUpdated(event)
            is DomainEvent.FlowDeleted -> applyFlowDeleted(event)
            is DomainEvent.TrailCreated -> applyTrailCreated(event)
            is DomainEvent.ArtifactReported -> applyArtifactReported(event)
            is DomainEvent.AttestationRecorded -> applyAttestationRecorded(event)
            is DomainEvent.EvidenceUploaded -> applyEvidenceUploaded(event)
        }
    }

    private fun applyFlowCreated(event: DomainEvent.FlowCreated) {
        if (flowRepository.existsById(event.aggregateId)) {
            log.debug("Flow {} already exists — skipping projection", event.aggregateId)
            return
        }
        val flow = Flow(
            id = event.aggregateId,
            name = event.name,
            description = event.description,
            orgSlug = event.orgSlug,
            createdAt = event.occurredAt,
            updatedAt = event.occurredAt
        ).also {
            it.requiredAttestationTypes = event.requiredAttestationTypes
            it.tags = event.tags.toMutableMap()
            it.templateYaml = event.templateYaml
            it.requiresApproval = event.requiresApproval
            it.requiredApproverRoles = event.requiredApproverRoles
        }
        flowRepository.save(flow)
        log.debug("Projected FlowCreated: {}", event.aggregateId)
    }

    private fun applyFlowUpdated(event: DomainEvent.FlowUpdated) {
        val flow = flowRepository.findById(event.aggregateId)
        if (flow == null) {
            log.warn("FlowUpdated for unknown flow {} — skipping", event.aggregateId)
            return
        }
        event.name?.let { flow.name = it }
        event.description?.let { flow.description = it }
        event.requiredAttestationTypes?.let { flow.requiredAttestationTypes = it }
        event.tags?.let { flow.tags = it.toMutableMap() }
        event.templateYaml?.let { flow.templateYaml = it }
        event.requiresApproval?.let { flow.requiresApproval = it }
        event.requiredApproverRoles?.let { flow.requiredApproverRoles = it }
        flow.updatedAt = event.occurredAt
        flowRepository.save(flow)
        log.debug("Projected FlowUpdated: {}", event.aggregateId)
    }

    private fun applyFlowDeleted(event: DomainEvent.FlowDeleted) {
        if (!flowRepository.existsById(event.aggregateId)) {
            log.debug("Flow {} already deleted — skipping", event.aggregateId)
            return
        }
        flowRepository.deleteById(event.aggregateId)
        log.debug("Projected FlowDeleted: {}", event.aggregateId)
    }

    private fun applyTrailCreated(event: DomainEvent.TrailCreated) {
        if (trailRepository.existsById(event.aggregateId)) {
            log.debug("Trail {} already exists — skipping projection", event.aggregateId)
            return
        }
        val trail = Trail(
            id = event.aggregateId,
            flowId = event.flowId,
            gitCommitSha = event.gitCommitSha,
            gitBranch = event.gitBranch,
            gitAuthor = event.gitAuthor,
            gitAuthorEmail = event.gitAuthorEmail,
            pullRequestId = event.pullRequestId,
            pullRequestReviewer = event.pullRequestReviewer,
            deploymentActor = event.deploymentActor,
            orgSlug = event.orgSlug,
            templateYaml = event.templateYaml,
            buildUrl = event.buildUrl,
            createdAt = event.occurredAt,
            updatedAt = event.occurredAt
        )
        trailRepository.save(trail)
        log.debug("Projected TrailCreated: {}", event.aggregateId)
    }

    private fun applyArtifactReported(event: DomainEvent.ArtifactReported) {
        val artifact = Artifact(
            id = event.aggregateId,
            trailId = event.trailId,
            imageName = event.imageName,
            imageTag = event.imageTag,
            sha256Digest = event.sha256Digest,
            registry = event.registry,
            reportedBy = event.reportedBy,
            orgSlug = event.orgSlug,
            reportedAt = event.occurredAt
        )
        artifactRepository.save(artifact)
        log.debug("Projected ArtifactReported: {}", event.aggregateId)
    }

    private fun applyAttestationRecorded(event: DomainEvent.AttestationRecorded) {
        val attestation = Attestation(
            id = event.aggregateId,
            trailId = event.trailId,
            type = event.type,
            status = AttestationStatus.valueOf(event.status),
            details = event.details,
            name = event.name,
            evidenceUrl = event.evidenceUrl,
            orgSlug = event.orgSlug,
            artifactFingerprint = event.artifactFingerprint,
            createdAt = event.occurredAt
        )
        attestationRepository.save(attestation)
        log.debug("Projected AttestationRecorded: {}", event.aggregateId)
    }

    private fun applyEvidenceUploaded(event: DomainEvent.EvidenceUploaded) {
        val attestation = attestationRepository.findById(event.aggregateId)
        if (attestation == null) {
            log.warn("EvidenceUploaded for unknown attestation {} — skipping", event.aggregateId)
            return
        }
        attestation.evidenceFileHash = event.sha256Hash
        attestation.evidenceFileName = event.fileName
        attestation.evidenceFileSizeBytes = event.fileSizeBytes
        attestationRepository.save(attestation)
        log.debug("Projected EvidenceUploaded: {}", event.aggregateId)
    }
}
