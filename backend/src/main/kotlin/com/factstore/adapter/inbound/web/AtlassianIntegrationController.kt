package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IConfluenceIntegrationService
import com.factstore.core.port.inbound.IJiraIntegrationService
import com.factstore.dto.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Atlassian Integrations", description = "Jira and Confluence integration management")
class AtlassianIntegrationController(
    private val jiraIntegrationService: IJiraIntegrationService,
    private val confluenceIntegrationService: IConfluenceIntegrationService
) {

    // --- Jira endpoints ---

    @PostMapping("/jira/config")
    @Operation(summary = "Configure Jira integration")
    fun saveJiraConfig(@RequestBody request: JiraConfigRequest): ResponseEntity<JiraConfigResponse> =
        ResponseEntity.ok(jiraIntegrationService.saveConfig(request))

    @GetMapping("/jira/config")
    @Operation(summary = "Get Jira configuration")
    fun getJiraConfig(): ResponseEntity<JiraConfigResponse> =
        ResponseEntity.ok(jiraIntegrationService.getConfig())

    @PostMapping("/jira/test")
    @Operation(summary = "Test Jira connectivity")
    fun testJiraConnectivity(): ResponseEntity<ConnectionTestResponse> =
        ResponseEntity.ok(jiraIntegrationService.testConnectivity())

    @PostMapping("/jira/sync")
    @Operation(summary = "Manual sync of fact store events to Jira")
    fun syncToJira(): ResponseEntity<JiraSyncResponse> =
        ResponseEntity.ok(jiraIntegrationService.syncTrailsToJira())

    @GetMapping("/jira/tickets")
    @Operation(summary = "List Jira tickets created by fact store")
    fun listJiraTickets(): ResponseEntity<List<JiraTicketResponse>> =
        ResponseEntity.ok(jiraIntegrationService.listTickets())

    @PostMapping("/jira/tickets")
    @Operation(summary = "Create a Jira ticket for a trail")
    fun createJiraTicket(@RequestBody request: CreateJiraTicketRequest): ResponseEntity<JiraTicketResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            jiraIntegrationService.createTicketForTrail(request.trailId, request.summary, request.issueType)
        )

    // --- Confluence endpoints ---

    @PostMapping("/confluence/config")
    @Operation(summary = "Configure Confluence integration")
    fun saveConfluenceConfig(@RequestBody request: ConfluenceConfigRequest): ResponseEntity<ConfluenceConfigResponse> =
        ResponseEntity.ok(confluenceIntegrationService.saveConfig(request))

    @GetMapping("/confluence/config")
    @Operation(summary = "Get Confluence configuration")
    fun getConfluenceConfig(): ResponseEntity<ConfluenceConfigResponse> =
        ResponseEntity.ok(confluenceIntegrationService.getConfig())

    @PostMapping("/confluence/test")
    @Operation(summary = "Test Confluence connectivity")
    fun testConfluenceConnectivity(): ResponseEntity<ConnectionTestResponse> =
        ResponseEntity.ok(confluenceIntegrationService.testConnectivity())
}
