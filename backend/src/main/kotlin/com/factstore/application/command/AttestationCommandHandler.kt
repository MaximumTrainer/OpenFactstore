package com.factstore.application.command

import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.TrailStatus
import com.factstore.core.domain.event.DomainEvent
import com.factstore.core.port.inbound.command.IAttestationCommandHandler
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.inbound.IEvidenceVaultService
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IEventPublisher
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.IOrganisationRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.core.port.outbound.SupplyChainEvent
import com.factstore.dto.command.CommandResult
import com.factstore.dto.command.RecordAttestationCommand
import com.factstore.dto.command.UploadEvidenceCommand
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class AttestationCommandHandler(
    private val attestationRepository: IAttestationRepository,
    private val trailRepository: ITrailRepository,
    private val evidenceVaultService: IEvidenceVaultService,
    private val auditService: IAuditService,
    private val organisationRepository: IOrganisationRepository,
    private val flowRepository: IFlowRepository,
    private val eventPublisher: IEventPublisher,
    private val eventAppender: EventAppender
) : IAttestationCommandHandler {

    private val log = LoggerFactory.getLogger(AttestationCommandHandler::class.java)

    override fun recordAttestation(command: RecordAttestationCommand): CommandResult {
        if (command.orgSlug != null && !organisationRepository.existsBySlug(command.orgSlug)) {
            throw NotFoundException("Organisation not found: ${command.orgSlug}")
        }
        if (command.orgSlug != null && command.flowName != null) {
            val orgFlows = flowRepository.findAllByOrgSlug(command.orgSlug)
            if (orgFlows.none { it.name == command.flowName }) {
                throw NotFoundException("Flow '${command.flowName}' not found for organisation '${command.orgSlug}'")
            }
        }
        if (!trailRepository.existsById(command.trailId)) throw NotFoundException("Trail not found: ${command.trailId}")
        val attestation = Attestation(
            trailId = command.trailId,
            type = command.type,
            status = command.status,
            details = command.details,
            name = command.name,
            evidenceUrl = command.evidenceUrl,
            orgSlug = command.orgSlug,
            artifactFingerprint = command.artifactFingerprint
        )
        val saved = attestationRepository.save(attestation)
        eventAppender.append(DomainEvent.AttestationRecorded(
            aggregateId = saved.id,
            trailId = saved.trailId,
            type = saved.type,
            status = saved.status.name,
            details = saved.details,
            name = saved.name,
            evidenceUrl = saved.evidenceUrl,
            orgSlug = saved.orgSlug,
            artifactFingerprint = saved.artifactFingerprint,
            flowName = command.flowName
        ))
        eventPublisher.publish(
            SupplyChainEvent.AttestationRecorded(
                trailId = command.trailId,
                attestationType = command.type,
                orgSlug = command.orgSlug,
                artifactFingerprint = command.artifactFingerprint
            )
        )
        if (command.status == AttestationStatus.FAILED) {
            markTrailNonCompliant(command.trailId)
        }
        auditService.record(
            eventType = AuditEventType.ATTESTATION_RECORDED,
            actor = "system",
            payload = mapOf(
                "attestationId" to saved.id.toString(),
                "trailId" to command.trailId.toString(),
                "type" to saved.type,
                "status" to saved.status.name
            ),
            trailId = command.trailId
        )
        log.info("Recorded attestation: ${saved.id} type=${saved.type} status=${saved.status}")
        return CommandResult(id = saved.id, status = "created")
    }

    override fun uploadEvidence(command: UploadEvidenceCommand): CommandResult {
        val attestation = attestationRepository.findById(command.attestationId)
            ?: throw NotFoundException("Attestation not found: ${command.attestationId}")
        if (attestation.trailId != command.trailId) {
            throw NotFoundException("Attestation ${command.attestationId} does not belong to trail ${command.trailId}")
        }
        val evidenceFile = evidenceVaultService.store(
            command.attestationId, command.fileName, command.contentType, command.content
        )
        attestation.evidenceFileHash = evidenceFile.sha256Hash
        attestation.evidenceFileName = evidenceFile.fileName
        attestation.evidenceFileSizeBytes = evidenceFile.fileSizeBytes
        attestationRepository.save(attestation)
        eventAppender.append(DomainEvent.EvidenceUploaded(
            aggregateId = attestation.id,
            trailId = attestation.trailId,
            fileName = evidenceFile.fileName,
            contentType = command.contentType,
            sha256Hash = evidenceFile.sha256Hash,
            fileSizeBytes = evidenceFile.fileSizeBytes
        ))
        log.info("Uploaded evidence for attestation: ${command.attestationId} hash=${evidenceFile.sha256Hash}")
        return CommandResult(id = attestation.id, status = "created")
    }

    private fun markTrailNonCompliant(trailId: java.util.UUID) {
        val trail = trailRepository.findById(trailId) ?: return
        trail.status = TrailStatus.NON_COMPLIANT
        trail.updatedAt = Instant.now()
        trailRepository.save(trail)
    }
}
