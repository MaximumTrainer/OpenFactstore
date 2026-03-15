package com.factstore.application

import com.factstore.core.domain.ScmIntegration
import com.factstore.core.domain.ScmProvider
import com.factstore.core.port.inbound.IScmIntegrationService
import com.factstore.core.port.outbound.IScmIntegrationRepository
import com.factstore.dto.CreateScmIntegrationRequest
import com.factstore.dto.ScmIntegrationResponse
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ScmIntegrationService(
    private val scmIntegrationRepository: IScmIntegrationRepository,
    private val encryptionService: ScmTokenEncryptionService
) : IScmIntegrationService {

    private val log = LoggerFactory.getLogger(ScmIntegrationService::class.java)

    override fun createIntegration(orgSlug: String, request: CreateScmIntegrationRequest): ScmIntegrationResponse {
        if (scmIntegrationRepository.existsByOrgSlugAndProvider(orgSlug, request.provider)) {
            throw ConflictException("SCM integration for provider '${request.provider}' already exists in org '$orgSlug'")
        }
        val integration = ScmIntegration(
            orgSlug = orgSlug,
            provider = request.provider,
            encryptedToken = encryptionService.encrypt(request.token),
            isTokenEncrypted = true
        )
        val saved = scmIntegrationRepository.save(integration)
        log.info("Created SCM integration for org=$orgSlug provider=${request.provider}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listIntegrations(orgSlug: String): List<ScmIntegrationResponse> =
        scmIntegrationRepository.findByOrgSlug(orgSlug).map { it.toResponse() }

    override fun deleteIntegration(orgSlug: String, provider: ScmProvider) {
        if (!scmIntegrationRepository.existsByOrgSlugAndProvider(orgSlug, provider)) {
            throw NotFoundException("No SCM integration found for org '$orgSlug' and provider '$provider'")
        }
        scmIntegrationRepository.deleteByOrgSlugAndProvider(orgSlug, provider)
        log.info("Deleted SCM integration for org=$orgSlug provider=$provider")
    }
}

fun ScmIntegration.toResponse() = ScmIntegrationResponse(
    id = id,
    orgSlug = orgSlug,
    provider = provider,
    isTokenEncrypted = isTokenEncrypted,
    createdAt = createdAt
)
