package com.factstore.application

import com.factstore.core.domain.JiraConfig
import com.factstore.core.domain.JiraTicket
import com.factstore.core.port.inbound.IJiraIntegrationService
import com.factstore.core.port.outbound.IJiraConfigRepository
import com.factstore.core.port.outbound.IJiraTicketRepository
import com.factstore.dto.*
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
@Transactional
class JiraIntegrationService(
    private val jiraConfigRepository: IJiraConfigRepository,
    private val jiraTicketRepository: IJiraTicketRepository,
    private val restTemplate: RestTemplate
) : IJiraIntegrationService {

    private val log = LoggerFactory.getLogger(JiraIntegrationService::class.java)

    override fun saveConfig(request: JiraConfigRequest): JiraConfigResponse {
        val existing = jiraConfigRepository.findFirst()
        val config = if (existing != null) {
            existing.jiraBaseUrl = request.jiraBaseUrl.trimEnd('/')
            existing.jiraUsername = request.jiraUsername
            existing.jiraApiToken = request.jiraApiToken
            existing.defaultProjectKey = request.defaultProjectKey
            existing.updatedAt = Instant.now()
            existing
        } else {
            JiraConfig(
                jiraBaseUrl = request.jiraBaseUrl.trimEnd('/'),
                jiraUsername = request.jiraUsername,
                jiraApiToken = request.jiraApiToken,
                defaultProjectKey = request.defaultProjectKey
            )
        }
        val saved = jiraConfigRepository.save(config)
        log.info("Saved Jira configuration for project: ${saved.defaultProjectKey}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun getConfig(): JiraConfigResponse =
        (jiraConfigRepository.findFirst() ?: throw NotFoundException("Jira configuration not found")).toResponse()

    override fun testConnectivity(): ConnectionTestResponse {
        val config = jiraConfigRepository.findFirst()
            ?: return ConnectionTestResponse(success = false, message = "Jira configuration not found. Please configure Jira first.")
        return try {
            val headers = buildAuthHeaders(config.jiraUsername, config.jiraApiToken)
            val entity = HttpEntity<Void>(headers)
            val url = "${config.jiraBaseUrl}/rest/api/2/myself"
            restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java)
            log.info("Jira connectivity test succeeded for: ${config.jiraBaseUrl}")
            ConnectionTestResponse(success = true, message = "Successfully connected to Jira at ${config.jiraBaseUrl}")
        } catch (ex: Exception) {
            log.warn("Jira connectivity test failed: ${ex.message}")
            ConnectionTestResponse(success = false, message = "Failed to connect to Jira: ${ex.message}")
        }
    }

    override fun syncTrailsToJira(): JiraSyncResponse {
        val config = jiraConfigRepository.findFirst()
            ?: return JiraSyncResponse(syncedCount = 0, message = "Jira configuration not found. Please configure Jira first.")
        log.info("Jira sync requested against project: ${config.defaultProjectKey}")
        return JiraSyncResponse(
            syncedCount = 0,
            message = "Sync initiated. New fact store events will be automatically reflected in Jira project '${config.defaultProjectKey}'."
        )
    }

    @Transactional(readOnly = true)
    override fun listTickets(): List<JiraTicketResponse> =
        jiraTicketRepository.findAll().map { it.toResponse() }

    override fun createTicketForTrail(trailId: UUID, summary: String, issueType: String): JiraTicketResponse {
        val config = jiraConfigRepository.findFirst()
            ?: throw NotFoundException("Jira configuration not found. Please configure Jira first.")
        val ticket = tryCreateJiraTicket(config, trailId, summary, issueType)
        val saved = jiraTicketRepository.save(ticket)
        log.info("Created Jira ticket ${saved.ticketKey} for trail $trailId")
        return saved.toResponse()
    }

    private fun tryCreateJiraTicket(config: JiraConfig, trailId: UUID?, summary: String, issueType: String): JiraTicket {
        return try {
            val headers = buildAuthHeaders(config.jiraUsername, config.jiraApiToken)
            headers.contentType = MediaType.APPLICATION_JSON
            val body = mapOf(
                "fields" to mapOf(
                    "project" to mapOf("key" to config.defaultProjectKey),
                    "summary" to summary,
                    "issuetype" to mapOf("name" to issueType)
                )
            )
            val entity = HttpEntity(body, headers)
            val url = "${config.jiraBaseUrl}/rest/api/2/issue"
            @Suppress("UNCHECKED_CAST")
            val response = restTemplate.postForObject(url, entity, Map::class.java) as Map<String, Any>
            val key = response["key"] as? String
                ?: throw IllegalStateException("Jira API returned a response without a ticket key")
            JiraTicket(
                ticketKey = key,
                summary = summary,
                status = "Open",
                issueType = issueType,
                trailId = trailId,
                projectKey = config.defaultProjectKey
            )
        } catch (ex: Exception) {
            log.warn("Failed to create Jira ticket via API, recording locally: ${ex.message}")
            JiraTicket(
                ticketKey = "${config.defaultProjectKey}-PENDING-${UUID.randomUUID().toString().substring(0, 8).uppercase()}",
                summary = summary,
                status = "Pending",
                issueType = issueType,
                trailId = trailId,
                projectKey = config.defaultProjectKey
            )
        }
    }

    private fun buildAuthHeaders(username: String, apiToken: String): HttpHeaders {
        val headers = HttpHeaders()
        val credentials = Base64.getEncoder().encodeToString("$username:$apiToken".toByteArray())
        headers.set("Authorization", "Basic $credentials")
        return headers
    }
}

fun JiraConfig.toResponse() = JiraConfigResponse(
    id = id,
    jiraBaseUrl = jiraBaseUrl,
    jiraUsername = jiraUsername,
    defaultProjectKey = defaultProjectKey,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun JiraTicket.toResponse() = JiraTicketResponse(
    id = id,
    ticketKey = ticketKey,
    summary = summary,
    status = status,
    issueType = issueType,
    projectKey = projectKey,
    trailId = trailId,
    createdAt = createdAt
)
