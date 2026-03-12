package com.factstore.application

import com.factstore.core.domain.ConfluenceConfig
import com.factstore.core.port.inbound.IConfluenceIntegrationService
import com.factstore.core.port.outbound.IConfluenceConfigRepository
import com.factstore.dto.*
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.Base64

@Service
@Transactional
class ConfluenceIntegrationService(
    private val confluenceConfigRepository: IConfluenceConfigRepository,
    private val restTemplate: RestTemplate
) : IConfluenceIntegrationService {

    private val log = LoggerFactory.getLogger(ConfluenceIntegrationService::class.java)

    override fun saveConfig(request: ConfluenceConfigRequest): ConfluenceConfigResponse {
        val existing = confluenceConfigRepository.findFirst()
        val config = if (existing != null) {
            existing.confluenceBaseUrl = request.confluenceBaseUrl.trimEnd('/')
            existing.confluenceUsername = request.confluenceUsername
            existing.confluenceApiToken = request.confluenceApiToken
            existing.defaultSpaceKey = request.defaultSpaceKey
            existing.updatedAt = Instant.now()
            existing
        } else {
            ConfluenceConfig(
                confluenceBaseUrl = request.confluenceBaseUrl.trimEnd('/'),
                confluenceUsername = request.confluenceUsername,
                confluenceApiToken = request.confluenceApiToken,
                defaultSpaceKey = request.defaultSpaceKey
            )
        }
        val saved = confluenceConfigRepository.save(config)
        log.info("Saved Confluence configuration for space: ${saved.defaultSpaceKey}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun getConfig(): ConfluenceConfigResponse =
        (confluenceConfigRepository.findFirst()
            ?: throw NotFoundException("Confluence configuration not found")).toResponse()

    override fun testConnectivity(): ConnectionTestResponse {
        val config = confluenceConfigRepository.findFirst()
            ?: return ConnectionTestResponse(
                success = false,
                message = "Confluence configuration not found. Please configure Confluence first."
            )
        return try {
            val headers = buildAuthHeaders(config.confluenceUsername, config.confluenceApiToken)
            val entity = HttpEntity<Void>(headers)
            val url = "${config.confluenceBaseUrl}/wiki/rest/api/space/${config.defaultSpaceKey}"
            restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java)
            log.info("Confluence connectivity test succeeded for: ${config.confluenceBaseUrl}")
            ConnectionTestResponse(
                success = true,
                message = "Successfully connected to Confluence at ${config.confluenceBaseUrl}"
            )
        } catch (ex: Exception) {
            log.warn("Confluence connectivity test failed: ${ex.message}")
            ConnectionTestResponse(success = false, message = "Failed to connect to Confluence: ${ex.message}")
        }
    }

    private fun buildAuthHeaders(username: String, apiToken: String): HttpHeaders {
        val headers = HttpHeaders()
        val credentials = Base64.getEncoder().encodeToString("$username:$apiToken".toByteArray())
        headers.set("Authorization", "Basic $credentials")
        return headers
    }
}

fun ConfluenceConfig.toResponse() = ConfluenceConfigResponse(
    id = id,
    confluenceBaseUrl = confluenceBaseUrl,
    confluenceUsername = confluenceUsername,
    defaultSpaceKey = defaultSpaceKey,
    createdAt = createdAt,
    updatedAt = updatedAt
)
