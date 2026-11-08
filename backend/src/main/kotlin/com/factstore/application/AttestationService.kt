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
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.ICustomAttestationTypeRepository
import com.factstore.core.port.outbound.IEventPublisher
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.IOrganisationRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.core.port.outbound.SupplyChainEvent
import com.factstore.dto.AttestationResponse
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreateArtifactAttestationRequest
import com.factstore.dto.EvidenceFileResponse
import com.factstore.dto.OverrideAttestationRequest
import com.factstore.dto.PageResponse
import com.factstore.exception.BadRequestException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
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
    private val customAttestationTypeRepository: ICustomAttestationTypeRepository,
    private val artifactRepository: IArtifactRepository,
    private val objectMapper: ObjectMapper,
    private val processors: List<AttestationTypeProcessor> = emptyList(),
    private val actorResolver: ActorResolver,
    private val tenantScope: TenantScope = TenantScope.UNSCOPED
) : IAttestationService {

    /**
     * The trail exists and belongs to the caller's organisation. Another organisation's trail is
     * "not found", for writes as for reads (#155 FR-5.2).
     */
    private fun requireVisibleTrail(trailId: UUID) {
        tenantScope.require(trailRepository.findById(trailId), { it.orgSlug ?: flowRepository.findById(it.flowId)?.orgSlug }) {
            "Trail not found: $trailId"
        }
    }

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
        requireVisibleTrail(trailId)

        val customType = customAttestationTypeRepository.findByName(request.type)
        if (customType?.schemaJson != null && request.attestationData != null) {
            validateDataAgainstSchema(request.attestationData, customType.schemaJson!!, request.type)
        }

        val attestation = Attestation(
            trailId = trailId,
            type = request.type,
            status = request.status,
            details = request.details,
            name = request.name,
            evidenceUrl = request.evidenceUrl,
            orgSlug = orgSlug ?: request.orgSlug,
            artifactFingerprint = artifactFingerprint,
            attestationData = request.attestationData,
            gitCommitSha = request.gitCommitSha,
            gitBranch = request.gitBranch,
            gitRepoUrl = request.gitRepoUrl
        )
        val effectiveExternalUrls = if (request.externalUrls.isEmpty() && request.evidenceUrl != null) {
            listOf(request.evidenceUrl)
        } else {
            request.externalUrls
        }
        attestation.externalUrls = effectiveExternalUrls
        attestation.annotations.putAll(request.annotations)

        if (customType?.jqExpression != null && request.attestationData != null) {
            applyJqExpression(attestation, customType.jqExpression!!, request.attestationData)
        }

        val saved = attestationRepository.save(attestation)
        eventPublisher.publish(
            SupplyChainEvent.AttestationRecorded(
                trailId = trailId,
                attestationType = request.type,
                orgSlug = orgSlug,
                artifactFingerprint = artifactFingerprint
            )
        )
        if (saved.status == AttestationStatus.FAILED) {
            markTrailNonCompliant(trailId)
        }
        auditService.record(
            eventType = AuditEventType.ATTESTATION_RECORDED,
            // Recording evidence is an act with an owner; "system" cannot answer who
            // vouched for it (#156 FR-7.2).
            actor = actorResolver.current(),
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
        requireVisibleTrail(trailId)
        return attestationRepository.findByTrailId(trailId).map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    override fun listAttestations(trailId: UUID, page: Int, size: Int): PageResponse<AttestationResponse> {
        requireVisibleTrail(trailId)
        val pageResult = attestationRepository.findByTrailId(trailId, PageRequest.of(page, size))
        return PageResponse(
            items = pageResult.content.map { it.toResponse() },
            page = pageResult.number,
            size = pageResult.size,
            totalItems = pageResult.totalElements,
            totalPages = pageResult.totalPages
        )
    }

    override fun recordArtifactAttestation(artifactId: UUID, request: CreateArtifactAttestationRequest): AttestationResponse {
        val artifact = artifactRepository.findById(artifactId)
            ?: throw NotFoundException("Artifact not found: $artifactId")
        val attestation = Attestation(
            trailId = artifact.trailId,
            type = request.type,
            status = request.status,
            details = request.details,
            name = request.name,
            evidenceUrl = request.evidenceUrl,
            orgSlug = request.orgSlug ?: artifact.orgSlug,
            artifactFingerprint = artifact.sha256Digest,
            artifactId = artifactId,
            attestationData = request.attestationData,
            gitCommitSha = request.gitCommitSha,
            gitBranch = request.gitBranch,
            gitRepoUrl = request.gitRepoUrl
        )
        val effectiveExternalUrls = if (request.externalUrls.isEmpty() && request.evidenceUrl != null) {
            listOf(request.evidenceUrl)
        } else {
            request.externalUrls
        }
        attestation.externalUrls = effectiveExternalUrls
        attestation.annotations.putAll(request.annotations)
        val saved = attestationRepository.save(attestation)
        eventPublisher.publish(
            SupplyChainEvent.AttestationRecorded(
                trailId = artifact.trailId,
                attestationType = request.type,
                orgSlug = request.orgSlug,
                artifactFingerprint = artifact.sha256Digest
            )
        )
        if (saved.status == AttestationStatus.FAILED) {
            markTrailNonCompliant(artifact.trailId)
        }
        log.info("Recorded artifact-level attestation: ${saved.id} artifactId=$artifactId type=${saved.type}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listArtifactAttestations(artifactId: UUID): List<AttestationResponse> {
        if (artifactRepository.findById(artifactId) == null) throw NotFoundException("Artifact not found: $artifactId")
        return attestationRepository.findByArtifactId(artifactId).map { it.toResponse() }
    }

    override fun overrideAttestation(id: UUID, request: OverrideAttestationRequest): AttestationResponse {
        val original = attestationRepository.findById(id)
            ?: throw NotFoundException("Attestation not found: $id")
        val override = Attestation(
            trailId = original.trailId,
            type = original.type,
            status = AttestationStatus.PASSED,
            orgSlug = original.orgSlug,
            artifactFingerprint = original.artifactFingerprint,
            artifactId = original.artifactId,
            overridesAttestationId = id,
            justification = request.justification
        )
        val saved = attestationRepository.save(override)
        log.info("Overrode attestation: $id with new attestation: ${saved.id}")
        return saved.toResponse()
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

    private fun validateDataAgainstSchema(data: String, schemaJson: String, typeName: String) {
        try {
            val dataNode = objectMapper.readTree(data)
            val schemaNode = objectMapper.readTree(schemaJson)
            val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
            val schema = factory.getSchema(schemaNode)
            val errors = schema.validate(dataNode)
            if (errors.isNotEmpty()) {
                val message = errors.take(3).joinToString("; ") { it.message }
                throw BadRequestException("attestationData does not conform to schema for type '$typeName': $message")
            }
        } catch (e: BadRequestException) {
            throw e
        } catch (e: Exception) {
            throw BadRequestException("attestationData is not valid JSON for type '$typeName': ${e.message}")
        }
    }

    private fun applyJqExpression(attestation: Attestation, jqExpression: String, attestationData: String) {
        try {
            val node = objectMapper.readTree(attestationData)
            val result = evaluateJqExpression(jqExpression, node)
            if (result != null) {
                attestation.status = if (result) AttestationStatus.PASSED else AttestationStatus.FAILED
            }
        } catch (e: Exception) {
            log.warn("Failed to evaluate jq expression '{}' for attestation type '{}': {}", jqExpression, attestation.type, e.message)
        }
    }

    /**
     * Evaluates a simple jq-like expression against a JSON node.
     * Supports: `.field`, `.field == value`, `.field.nested == value`
     * Returns null if the expression is not supported or cannot be evaluated.
     */
    private fun evaluateJqExpression(expression: String, node: JsonNode): Boolean? {
        val trimmed = expression.trim()
        return if (trimmed.contains("==")) {
            val parts = trimmed.split("==", limit = 2)
            val fieldPath = parts[0].trim()
            val expectedRaw = parts[1].trim()
            val fieldNode = resolveFieldPath(fieldPath, node) ?: return null
            val expected = expectedRaw.removeSurrounding("\"")
            when {
                expectedRaw == "true" -> fieldNode.asBoolean() == true
                expectedRaw == "false" -> fieldNode.asBoolean() == false
                expectedRaw.startsWith("\"") -> fieldNode.asText() == expected
                else -> fieldNode.asText() == expected
            }
        } else {
            val fieldNode = resolveFieldPath(trimmed, node) ?: return null
            when {
                fieldNode.isBoolean -> fieldNode.asBoolean()
                fieldNode.isNull -> false
                fieldNode.isMissingNode -> false
                else -> true
            }
        }
    }

    private fun resolveFieldPath(path: String, node: JsonNode): JsonNode? {
        val parts = path.trimStart('.').split(".")
        var current: JsonNode = node
        for (part in parts) {
            if (part.isBlank()) continue
            current = current.get(part) ?: return null
        }
        return current
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
    createdAt = createdAt,
    attestationData = attestationData,
    externalUrls = externalUrls,
    annotations = annotations.toMap(),
    gitCommitSha = gitCommitSha,
    gitBranch = gitBranch,
    gitRepoUrl = gitRepoUrl,
    artifactId = artifactId,
    overridesAttestationId = overridesAttestationId,
    justification = justification
)
