package com.factstore.application

import com.factstore.application.attestation.AttestationTypeProcessor
import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.inbound.IAttestationService
import com.factstore.core.port.inbound.IEvidenceVaultService
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IEventPublisher
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.IOrganisationRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.core.port.outbound.SupplyChainEvent
import com.factstore.dto.AttestationResponse
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.EvidenceFileResponse
import com.factstore.dto.PageResponse
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class AttestationService(
    private val attestationRepository: IAttestationRepository,
    private val trailRepository: ITrailRepository,
    private val evidenceVaultService: IEvidenceVaultService,
    private val auditService: IAuditService,
    private val organisationRepository: IOrganisationRepository,
    private val flowRepository: IFlowRepository,
    private val eventPublisher: IEventPublisher,
    private val processors: List<AttestationTypeProcessor> = emptyList()
) : IAttestationService {

    private val log = LoggerFactory.getLogger(AttestationService::class.java)

    override fun recordAttestation(
        trailId: UUID,
        request: CreateAttestationRequest,
        artifactFingerprint: String?,
        orgSlug: String?,
        flowName: String?
    ): AttestationResponse {
        if (orgSlug != null && !organisationRepository.existsBySlug(orgSlug)) {
            throw NotFoundException("Organisation not found: $orgSlug")
        }
        if (orgSlug != null && flowName != null) {
            val orgFlows = flowRepository.findAllByOrgSlug(orgSlug)
            if (orgFlows.none { it.name == flowName }) {
                throw NotFoundException("Flow '$flowName' not found for organisation '$orgSlug'")
            }
        }
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")
        val attestation = Attestation(
            trailId = trailId,
            type = request.type,
            status = request.status,
            details = request.details,
            name = request.name,
            evidenceUrl = request.evidenceUrl,
            orgSlug = orgSlug ?: request.orgSlug,
            artifactFingerprint = artifactFingerprint
        )
        val saved = attestationRepository.save(attestation)
        eventPublisher.publish(
            SupplyChainEvent.AttestationRecorded(
                trailId = trailId,
                attestationType = request.type,
                orgSlug = orgSlug,
                artifactFingerprint = artifactFingerprint
            )
        )
        if (request.status == AttestationStatus.FAILED) {
            markTrailNonCompliant(trailId)
        }
        auditService.record(
            eventType = AuditEventType.ATTESTATION_RECORDED,
            actor = "system",
            payload = mapOf(
                "attestationId" to saved.id.toString(),
                "trailId" to trailId.toString(),
                "type" to saved.type,
                "status" to saved.status.name
            ),
            trailId = trailId
        )
        log.info("Recorded attestation: ${saved.id} type=${saved.type} status=${saved.status}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listAttestations(trailId: UUID): List<AttestationResponse> {
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")
        return attestationRepository.findByTrailId(trailId).map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    override fun listAttestations(trailId: UUID, page: Int, size: Int): PageResponse<AttestationResponse> {
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")
        val pageResult = attestationRepository.findByTrailId(trailId, PageRequest.of(page, size))
        return PageResponse(
            items = pageResult.content.map { it.toResponse() },
            page = pageResult.number,
            size = pageResult.size,
            totalItems = pageResult.totalElements,
            totalPages = pageResult.totalPages
        )
    }

    override fun uploadEvidence(
        trailId: UUID,
        attestationId: UUID,
        fileName: String,
        contentType: String,
        content: ByteArray
    ): EvidenceFileResponse {
        val attestation = attestationRepository.findById(attestationId)
            ?: throw NotFoundException("Attestation not found: $attestationId")
        if (attestation.trailId != trailId) throw NotFoundException("Attestation $attestationId does not belong to trail $trailId")

        val evidenceFile = evidenceVaultService.store(attestationId, fileName, contentType, content)

        attestation.evidenceFileHash = evidenceFile.sha256Hash
        attestation.evidenceFileName = evidenceFile.fileName
        attestation.evidenceFileSizeBytes = evidenceFile.fileSizeBytes
        applyTypeProcessor(attestation, content)
        attestationRepository.save(attestation)

        log.info("Uploaded evidence for attestation: $attestationId hash=${evidenceFile.sha256Hash}")
        return evidenceFile.toResponse()
    }

    private fun applyTypeProcessor(attestation: Attestation, evidenceContent: ByteArray) {
        val processor = processors.firstOrNull { it.typeName.equals(attestation.type, ignoreCase = true) }
        if (processor != null) {
            processor.process(evidenceContent, attestation)
            if (attestation.status == AttestationStatus.FAILED) {
                markTrailNonCompliant(attestation.trailId)
            }
            eventPublisher.publish(SupplyChainEvent.AttestationProcessedEvent(
                attestationId = attestation.id.toString(),
                type = attestation.type,
                status = attestation.status.name,
                details = attestation.details
            ))
        }
    }

    private fun markTrailNonCompliant(trailId: UUID) {
        val trail = trailRepository.findById(trailId) ?: return
        trail.status = TrailStatus.NON_COMPLIANT
        trail.updatedAt = Instant.now()
        trailRepository.save(trail)
    }
}

fun Attestation.toResponse() = AttestationResponse(
    id = id,
    trailId = trailId,
    type = type,
    status = status,
    evidenceFileHash = evidenceFileHash,
    evidenceFileName = evidenceFileName,
    evidenceFileSizeBytes = evidenceFileSizeBytes,
    details = details,
    name = name,
    evidenceUrl = evidenceUrl,
    compliant = status == AttestationStatus.PASSED,
    orgSlug = orgSlug,
    artifactFingerprint = artifactFingerprint,
    createdAt = createdAt
)
