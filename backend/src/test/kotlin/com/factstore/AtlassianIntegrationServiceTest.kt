package com.factstore

import com.factstore.application.JiraIntegrationService
import com.factstore.application.ConfluenceIntegrationService
import com.factstore.dto.*
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class AtlassianIntegrationServiceTest {

    @Autowired
    lateinit var jiraIntegrationService: JiraIntegrationService

    @Autowired
    lateinit var confluenceIntegrationService: ConfluenceIntegrationService

    // --- Jira config tests ---

    @Test
    fun `save jira config creates new configuration`() {
        val req = JiraConfigRequest(
            jiraBaseUrl = "https://company.atlassian.net",
            jiraUsername = "user@company.com",
            jiraApiToken = "secret-token",
            defaultProjectKey = "COMP"
        )
        val resp = jiraIntegrationService.saveConfig(req)
        assertNotNull(resp.id)
        assertEquals("https://company.atlassian.net", resp.jiraBaseUrl)
        assertEquals("user@company.com", resp.jiraUsername)
        assertEquals("COMP", resp.defaultProjectKey)
    }

    @Test
    fun `save jira config updates existing configuration`() {
        jiraIntegrationService.saveConfig(
            JiraConfigRequest("https://first.atlassian.net", "first@test.com", "token1", "PROJ1")
        )
        val updated = jiraIntegrationService.saveConfig(
            JiraConfigRequest("https://second.atlassian.net", "second@test.com", "token2", "PROJ2")
        )
        assertEquals("https://second.atlassian.net", updated.jiraBaseUrl)
        assertEquals("PROJ2", updated.defaultProjectKey)
        // Only one config should exist
        assertEquals(updated.id, jiraIntegrationService.getConfig().id)
    }

    @Test
    fun `get jira config throws NotFoundException when not configured`() {
        assertThrows<NotFoundException> {
            jiraIntegrationService.getConfig()
        }
    }

    @Test
    fun `get jira config returns saved configuration`() {
        val saved = jiraIntegrationService.saveConfig(
            JiraConfigRequest("https://company.atlassian.net", "user@test.com", "token", "COMP")
        )
        val fetched = jiraIntegrationService.getConfig()
        assertEquals(saved.id, fetched.id)
        assertEquals("COMP", fetched.defaultProjectKey)
    }

    @Test
    fun `test jira connectivity returns failure when not configured`() {
        val result = jiraIntegrationService.testConnectivity()
        assertFalse(result.success)
        assertTrue(result.message.contains("not found", ignoreCase = true))
    }

    @Test
    fun `test jira connectivity returns failure for invalid url`() {
        jiraIntegrationService.saveConfig(
            JiraConfigRequest("https://invalid-jira-url-that-does-not-exist.example.com", "user", "token", "COMP")
        )
        val result = jiraIntegrationService.testConnectivity()
        assertFalse(result.success)
        assertTrue(result.message.contains("Failed to connect", ignoreCase = true))
    }

    @Test
    fun `sync to jira returns message when not configured`() {
        val result = jiraIntegrationService.syncTrailsToJira()
        assertEquals(0, result.syncedCount)
        assertTrue(result.message.contains("not found", ignoreCase = true))
    }

    @Test
    fun `sync to jira returns message when configured`() {
        jiraIntegrationService.saveConfig(
            JiraConfigRequest("https://company.atlassian.net", "user@test.com", "token", "COMP")
        )
        val result = jiraIntegrationService.syncTrailsToJira()
        assertEquals(0, result.syncedCount)
        assertTrue(result.message.contains("COMP"))
    }

    @Test
    fun `list jira tickets returns empty list initially`() {
        val tickets = jiraIntegrationService.listTickets()
        assertTrue(tickets.isEmpty())
    }

    @Test
    fun `create jira ticket for trail records ticket locally when api unavailable`() {
        jiraIntegrationService.saveConfig(
            JiraConfigRequest("https://invalid-jira.example.com", "user@test.com", "token", "COMP")
        )
        val trailId = UUID.randomUUID()
        val ticket = jiraIntegrationService.createTicketForTrail(trailId, "Compliance Review - v1.2.3", "Task")
        assertNotNull(ticket.id)
        assertEquals("Compliance Review - v1.2.3", ticket.summary)
        assertEquals("COMP", ticket.projectKey)
        assertEquals(trailId, ticket.trailId)
        assertTrue(ticket.ticketKey.startsWith("COMP-PENDING-"))
    }

    @Test
    fun `pending ticket keys are unique across multiple local tickets`() {
        jiraIntegrationService.saveConfig(
            JiraConfigRequest("https://invalid-jira.example.com", "user@test.com", "token", "COMP")
        )
        val trailId = UUID.randomUUID()
        val ticket1 = jiraIntegrationService.createTicketForTrail(trailId, "First ticket", "Task")
        val ticket2 = jiraIntegrationService.createTicketForTrail(trailId, "Second ticket", "Task")
        assertNotEquals(ticket1.ticketKey, ticket2.ticketKey)
    }

    @Test
    fun `create jira ticket throws NotFoundException when not configured`() {
        assertThrows<NotFoundException> {
            jiraIntegrationService.createTicketForTrail(UUID.randomUUID(), "Test Summary", "Task")
        }
    }

    @Test
    fun `list jira tickets returns created tickets`() {
        jiraIntegrationService.saveConfig(
            JiraConfigRequest("https://invalid-jira.example.com", "user@test.com", "token", "COMP")
        )
        val trailId = UUID.randomUUID()
        jiraIntegrationService.createTicketForTrail(trailId, "Release Approval", "Task")
        jiraIntegrationService.createTicketForTrail(trailId, "Security Scan Failed", "Bug")
        val tickets = jiraIntegrationService.listTickets()
        assertEquals(2, tickets.size)
    }

    @Test
    fun `jira base url trailing slash is stripped`() {
        val resp = jiraIntegrationService.saveConfig(
            JiraConfigRequest("https://company.atlassian.net/", "user", "token", "COMP")
        )
        assertEquals("https://company.atlassian.net", resp.jiraBaseUrl)
    }

    // --- Confluence config tests ---

    @Test
    fun `save confluence config creates new configuration`() {
        val req = ConfluenceConfigRequest(
            confluenceBaseUrl = "https://company.atlassian.net",
            confluenceUsername = "user@company.com",
            confluenceApiToken = "secret-token",
            defaultSpaceKey = "AUDIT"
        )
        val resp = confluenceIntegrationService.saveConfig(req)
        assertNotNull(resp.id)
        assertEquals("https://company.atlassian.net", resp.confluenceBaseUrl)
        assertEquals("user@company.com", resp.confluenceUsername)
        assertEquals("AUDIT", resp.defaultSpaceKey)
    }

    @Test
    fun `save confluence config updates existing configuration`() {
        confluenceIntegrationService.saveConfig(
            ConfluenceConfigRequest("https://first.atlassian.net", "first@test.com", "token1", "SPACE1")
        )
        val updated = confluenceIntegrationService.saveConfig(
            ConfluenceConfigRequest("https://second.atlassian.net", "second@test.com", "token2", "SPACE2")
        )
        assertEquals("https://second.atlassian.net", updated.confluenceBaseUrl)
        assertEquals("SPACE2", updated.defaultSpaceKey)
        assertEquals(updated.id, confluenceIntegrationService.getConfig().id)
    }

    @Test
    fun `get confluence config throws NotFoundException when not configured`() {
        assertThrows<NotFoundException> {
            confluenceIntegrationService.getConfig()
        }
    }

    @Test
    fun `get confluence config returns saved configuration`() {
        val saved = confluenceIntegrationService.saveConfig(
            ConfluenceConfigRequest("https://company.atlassian.net", "user@test.com", "token", "AUDIT")
        )
        val fetched = confluenceIntegrationService.getConfig()
        assertEquals(saved.id, fetched.id)
        assertEquals("AUDIT", fetched.defaultSpaceKey)
    }

    @Test
    fun `test confluence connectivity returns failure when not configured`() {
        val result = confluenceIntegrationService.testConnectivity()
        assertFalse(result.success)
        assertTrue(result.message.contains("not found", ignoreCase = true))
    }

    @Test
    fun `test confluence connectivity returns failure for invalid url`() {
        confluenceIntegrationService.saveConfig(
            ConfluenceConfigRequest("https://invalid-confluence.example.com", "user", "token", "AUDIT")
        )
        val result = confluenceIntegrationService.testConnectivity()
        assertFalse(result.success)
        assertTrue(result.message.contains("Failed to connect", ignoreCase = true))
    }

    @Test
    fun `confluence base url trailing slash is stripped`() {
        val resp = confluenceIntegrationService.saveConfig(
            ConfluenceConfigRequest("https://company.atlassian.net/", "user", "token", "AUDIT")
        )
        assertEquals("https://company.atlassian.net", resp.confluenceBaseUrl)
    }
}
