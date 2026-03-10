package com.factstore.service

import com.factstore.dto.ChainOfCustodyResponse
import com.factstore.dto.ComplianceStatus
import com.factstore.exception.NotFoundException
import com.factstore.repository.ArtifactRepository
import com.factstore.repository.AttestationRepository
import com.factstore.repository.EvidenceFileRepository
import com.factstore.repository.FlowRepository
import com.factstore.repository.TrailRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ComplianceService(
    private val artifactRepository: ArtifactRepository,
    private val trailRepository: TrailRepository,
    private val flowRepository: FlowRepository,
    private val attestationRepository: AttestationRepository,
    private val evidenceFileRepository: EvidenceFileRepository
) {

    fun getChainOfCustody(sha256Digest: String): ChainOfCustodyResponse {
        val artifacts = artifactRepository.findBySha256Digest(sha256Digest)
        if (artifacts.isEmpty()) throw NotFoundException("No artifact found with digest: $sha256Digest")

        val artifact = artifacts.first()
        val trail = trailRepository.findById(artifact.trailId)
            .orElseThrow { NotFoundException("Trail not found: ${artifact.trailId}") }
        val flow = flowRepository.findById(trail.flowId)
            .orElseThrow { NotFoundException("Flow not found: ${trail.flowId}") }
        val attestations = attestationRepository.findByTrailId(trail.id)
        val evidenceFiles = attestations.flatMap { evidenceFileRepository.findByAttestationId(it.id) }

        val required = flow.requiredAttestationTypes
        val passedTypes = attestations.filter { it.status == com.factstore.domain.AttestationStatus.PASSED }.map { it.type }.toSet()
        val complianceStatus = if (required.isEmpty() || required.all { it in passedTypes })
            ComplianceStatus.COMPLIANT else ComplianceStatus.NON_COMPLIANT

        return ChainOfCustodyResponse(
            artifact = artifact.toResponse(),
            trail = trail.toResponse(),
            flow = flow.toResponse(),
            attestations = attestations.map { it.toResponse() },
            evidenceFiles = evidenceFiles.map { it.toResponse() },
            complianceStatus = complianceStatus
        )
    }
}
