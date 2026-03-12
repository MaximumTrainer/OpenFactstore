package com.factstore.core.port.inbound

import com.factstore.dto.*
import java.util.UUID

interface IJiraIntegrationService {
    fun saveConfig(request: JiraConfigRequest): JiraConfigResponse
    fun getConfig(): JiraConfigResponse
    fun testConnectivity(): ConnectionTestResponse
    fun syncTrailsToJira(): JiraSyncResponse
    fun listTickets(): List<JiraTicketResponse>
    fun createTicketForTrail(trailId: UUID, summary: String, issueType: String): JiraTicketResponse
}
