package com.factstore.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.ScmProvider
import com.factstore.core.domain.TrailStatus
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.inbound.IPullRequestAttestationService
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IScmClient
import com.factstore.core.port.outbound.IScmIntegrationRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.AttestationResponse
import com.factstore.dto.CreatePrAttestationRequest
import com.factstore.exception.NotFoundException
import com.factstore.exception.PullRequestNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
@Transactional
class PullRequestAttestationService(
    private val scmClients: List<IScmClient>,
    private val attestationRepository: IAttestationRepository,
    private val trailRepository: ITrailRepository,
    private val scmIntegrationRepository: IScmIntegrationRepository,
    private val auditService: IAuditService,
    private val objectMapper: ObjectMapper
) : IPullRequestAttestationService {

    private val log = LoggerFactory.getLogger(PullRequestAttestationService::class.java)

    override fun attestPullRequest(trailId: UUID, request: CreatePrAttestationRequest): AttestationResponse {
        if (!trailRepository.existsById(trailId)) throw NotFoundException("Trail not found: $trailId")

        val orgSlug = request.orgSlug
            ?: throw NotFoundException("orgSlug is required for pull request attestation")

        val integration = scmIntegrationRepository.findByOrgSlugAndProvider(orgSlug, request.provider)
            ?: throw NotFoundException("No SCM integration found for org '$orgSlug' and provider '${request.provider}'")

        // NOTE: In production, replace Base64 decode with KMS decryption
        val token = String(Base64.getDecoder().decode(integration.tokenEncrypted))

        val client = scmClients.find { it.provider == request.provider }
            ?: throw NotFoundException("No SCM client registered for provider '${request.provider}'")

        val prInfo = client.findPullRequestForCommit(request.repository, request.commitSha, token)

        val status: AttestationStatus
        val details: String?

        if (prInfo != null) {
            status = AttestationStatus.PASSED
            details = objectMapper.writeValueAsString(prInfo)
            log.info("PR found for commit ${request.commitSha}: PR#${prInfo.id} in ${request.repository}")
        } else {
            status = AttestationStatus.FAILED
            details = objectMapper.writeValueAsString(
                mapOf(
                    "message" to "No pull request found for commit ${request.commitSha} in ${request.repository}",
                    "provider" to request.provider.name,
                    "repository" to request.repository,
                    "commitSha" to request.commitSha
                )
            )
            log.info("No PR found for commit ${request.commitSha} in ${request.repository}")

            if (request.assertOnMissing) {
                throw PullRequestNotFoundException(
                    "No pull request found for commit '${request.commitSha}' in repository '${request.repository}' (provider: ${request.provider})"
                )
            }
        }

        val attestation = Attestation(
            trailId = trailId,
            type = "pull-request",
            status = status,
            details = details,
            name = "Pull Request Attestation (${request.provider})",
            orgSlug = orgSlug
        )
        val saved = attestationRepository.save(attestation)

        if (status == AttestationStatus.FAILED) {
            markTrailNonCompliant(trailId)
        }

        auditService.record(
            eventType = AuditEventType.ATTESTATION_RECORDED,
            actor = "system",
            payload = mapOf(
                "attestationId" to saved.id.toString(),
                "trailId" to trailId.toString(),
                "type" to "pull-request",
                "status" to status.name,
                "provider" to request.provider.name,
                "repository" to request.repository,
                "commitSha" to request.commitSha
            ),
            trailId = trailId
        )

        return saved.toResponse()
    }

    private fun markTrailNonCompliant(trailId: UUID) {
        val trail = trailRepository.findById(trailId) ?: return
        trail.status = TrailStatus.NON_COMPLIANT
        trail.updatedAt = Instant.now()
        trailRepository.save(trail)
    }
}
