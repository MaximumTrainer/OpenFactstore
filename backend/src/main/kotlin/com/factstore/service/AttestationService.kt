package com.factstore.service

import com.factstore.domain.Attestation
import com.factstore.domain.AttestationStatus
import com.factstore.domain.TrailStatus
import com.factstore.dto.AttestationResponse
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.EvidenceFileResponse
import com.factstore.exception.NotFoundException
import com.factstore.repository.AttestationRepository
import com.factstore.repository.TrailRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class AttestationService(
    private val attestationRepository: AttestationRepository,
    private val trailRepository: TrailRepository,
    private val evidenceVaultService: EvidenceVaultService
) {
    private val log = LoggerFactory.getLogger(AttestationService::class.java)

    fun recordAttestation(trailId: UUID, request: CreateAttestationRequest): AttestationResponse {
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")
        val attestation = Attestation(
            trailId = trailId,
            type = request.type,
            status = request.status,
            details = request.details
        )
        val saved = attestationRepository.save(attestation)
        if (request.status == AttestationStatus.FAILED) {
            markTrailNonCompliant(trailId)
        }
        log.info("Recorded attestation: ${saved.id} type=${saved.type} status=${saved.status}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun listAttestations(trailId: UUID): List<AttestationResponse> {
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")
        return attestationRepository.findByTrailId(trailId).map { it.toResponse() }
    }

    fun uploadEvidence(
        trailId: UUID,
        attestationId: UUID,
        fileName: String,
        contentType: String,
        content: ByteArray
    ): EvidenceFileResponse {
        val attestation = attestationRepository.findById(attestationId)
            .orElseThrow { NotFoundException("Attestation not found: $attestationId") }
        if (attestation.trailId != trailId) throw NotFoundException("Attestation $attestationId does not belong to trail $trailId")

        val evidenceFile = evidenceVaultService.store(attestationId, fileName, contentType, content)

        attestation.evidenceFileHash = evidenceFile.sha256Hash
        attestation.evidenceFileName = evidenceFile.fileName
        attestation.evidenceFileSizeBytes = evidenceFile.fileSizeBytes
        attestationRepository.save(attestation)

        log.info("Uploaded evidence for attestation: $attestationId hash=${evidenceFile.sha256Hash}")
        return evidenceFile.toResponse()
    }

    private fun markTrailNonCompliant(trailId: UUID) {
        val trail = trailRepository.findById(trailId).orElse(null) ?: return
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
    createdAt = createdAt
)
