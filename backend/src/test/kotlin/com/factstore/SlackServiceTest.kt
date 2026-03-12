package com.factstore

import com.factstore.application.SlackService
import com.factstore.dto.ConfigureSlackRequest
import com.factstore.dto.CreateArtifactRequest
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.SlackNotification
import com.factstore.exception.BadRequestException
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
class SlackServiceTest {

    @Autowired lateinit var slackService: SlackService
    @Autowired lateinit var flowService: com.factstore.application.FlowService
    @Autowired lateinit var trailService: com.factstore.application.TrailService
    @Autowired lateinit var artifactService: com.factstore.application.ArtifactService
    @Autowired lateinit var attestationService: com.factstore.application.AttestationService

    private val testRequest = ConfigureSlackRequest(
        botToken = "xoxb-test-token",
        signingSecret = "test-secret",
        channel = "#deployments"
    )

    @Test
    fun `configureSlack creates new configuration`() {
        val result = slackService.configureSlack("my-org", testRequest)
        assertNotNull(result.id)
        assertEquals("my-org", result.orgSlug)
        assertEquals("#deployments", result.channel)
    }

    @Test
    fun `configureSlack updates existing configuration`() {
        slackService.configureSlack("my-org", testRequest)
        val updated = slackService.configureSlack(
            "my-org",
            ConfigureSlackRequest(
                botToken = "xoxb-new-token",
                signingSecret = "new-secret",
                channel = "#alerts"
            )
        )
        assertEquals("#alerts", updated.channel)
        assertEquals("my-org", updated.orgSlug)
    }

    @Test
    fun `removeSlack removes existing configuration`() {
        slackService.configureSlack("my-org", testRequest)
        slackService.removeSlack("my-org")
        assertThrows<NotFoundException> { slackService.getConfig("my-org") }
    }

    @Test
    fun `removeSlack throws NotFoundException for unknown org`() {
        assertThrows<NotFoundException> { slackService.removeSlack("no-such-org") }
    }

    @Test
    fun `getConfig throws NotFoundException for unknown org`() {
        assertThrows<NotFoundException> { slackService.getConfig("no-such-org") }
    }

    @Test
    fun `handleSlashCommand help returns help text`() {
        slackService.configureSlack("my-org", testRequest)
        val response = slackService.handleSlashCommand("my-org", "help", "U123", "alice")
        assertTrue(response.text.contains("search"))
        assertTrue(response.text.contains("trail"))
    }

    @Test
    fun `handleSlashCommand empty text returns help text`() {
        slackService.configureSlack("my-org", testRequest)
        val response = slackService.handleSlashCommand("my-org", "", "U123", "alice")
        assertTrue(response.text.contains("help"))
    }

    @Test
    fun `handleSlashCommand search with no matching artifacts returns no-match message`() {
        slackService.configureSlack("my-org", testRequest)
        val response = slackService.handleSlashCommand("my-org", "search deadbeef", "U123", "alice")
        assertTrue(response.text.contains("deadbeef"))
        assertTrue(response.text.contains("No artifacts"))
    }

    @Test
    fun `handleSlashCommand search with matching artifact returns artifact details`() {
        val flow = flowService.createFlow(CreateFlowRequest("slack-flow-${System.nanoTime()}", "test"))
        val trail = trailService.createTrail(
            CreateTrailRequest(
                flowId = flow.id,
                gitCommitSha = "abc123xyz",
                gitBranch = "main",
                gitAuthor = "dev",
                gitAuthorEmail = "dev@test.com"
            )
        )
        artifactService.reportArtifact(
            trail.id,
            CreateArtifactRequest(
                imageName = "my-service",
                imageTag = "1.2.3",
                sha256Digest = "abc123xyz789",
                reportedBy = "ci-bot"
            )
        )

        slackService.configureSlack("my-org", testRequest)
        val response = slackService.handleSlashCommand("my-org", "search abc123", "U123", "alice")
        assertTrue(response.text.contains("my-service"))
    }

    @Test
    fun `handleSlashCommand trail with unknown id returns not-found message`() {
        slackService.configureSlack("my-org", testRequest)
        val unknownId = UUID.randomUUID()
        val response = slackService.handleSlashCommand("my-org", "trail $unknownId", "U123", "alice")
        assertTrue(response.text.contains("not found"))
    }

    @Test
    fun `handleSlashCommand trail with known id returns trail details`() {
        val flow = flowService.createFlow(CreateFlowRequest("slack-trail-flow-${System.nanoTime()}", "test"))
        val trail = trailService.createTrail(
            CreateTrailRequest(
                flowId = flow.id,
                gitCommitSha = "trailsha123",
                gitBranch = "main",
                gitAuthor = "dev",
                gitAuthorEmail = "dev@test.com"
            )
        )
        attestationService.recordAttestation(
            trail.id,
            CreateAttestationRequest(type = "BUILD", status = com.factstore.core.domain.AttestationStatus.PASSED)
        )

        slackService.configureSlack("my-org", testRequest)
        val response = slackService.handleSlashCommand("my-org", "trail ${trail.id}", "U123", "alice")
        assertTrue(response.text.contains("trailsha123"))
        assertTrue(response.text.contains("BUILD"))
    }

    @Test
    fun `handleSlashCommand approve returns approval decision message`() {
        slackService.configureSlack("my-org", testRequest)
        val response = slackService.handleSlashCommand("my-org", "approve APR-42 looks good", "U123", "alice")
        assertTrue(response.text.contains("APR-42"))
        assertTrue(response.text.contains("alice"))
        assertTrue(response.text.contains("looks good"))
    }

    @Test
    fun `handleSlashCommand reject returns rejection decision message`() {
        slackService.configureSlack("my-org", testRequest)
        val response = slackService.handleSlashCommand("my-org", "reject APR-99 security issue", "U123", "bob")
        assertTrue(response.text.contains("APR-99"))
        assertTrue(response.text.contains("bob"))
        assertTrue(response.text.contains("security issue"))
    }

    @Test
    fun `handleSlashCommand env returns not-yet-supported message`() {
        slackService.configureSlack("my-org", testRequest)
        val response = slackService.handleSlashCommand("my-org", "env production", "U123", "alice")
        assertTrue(response.text.contains("not yet supported"))
    }

    @Test
    fun `handleSlashCommand unknown sub-command returns unknown-command message`() {
        slackService.configureSlack("my-org", testRequest)
        val response = slackService.handleSlashCommand("my-org", "deploy production", "U123", "alice")
        assertTrue(response.text.contains("help"))
    }

    @Test
    fun `handleInteractiveAction approve_release action returns approval message`() {
        slackService.configureSlack("my-org", testRequest)
        val payload = """
            {
              "type": "block_actions",
              "user": {"id": "U999", "username": "charlie"},
              "actions": [
                {"action_id": "approve_release", "value": "APR-77"}
              ]
            }
        """.trimIndent()
        val response = slackService.handleInteractiveAction("my-org", payload)
        assertTrue(response.text.contains("APR-77"))
        assertTrue(response.text.contains("charlie"))
    }

    @Test
    fun `handleInteractiveAction reject_release action returns rejection message`() {
        slackService.configureSlack("my-org", testRequest)
        val payload = """
            {
              "type": "block_actions",
              "user": {"id": "U888", "username": "diana"},
              "actions": [
                {"action_id": "reject_release", "value": "APR-55"}
              ]
            }
        """.trimIndent()
        val response = slackService.handleInteractiveAction("my-org", payload)
        assertTrue(response.text.contains("APR-55"))
        assertTrue(response.text.contains("diana"))
    }

    @Test
    fun `sendNotification throws NotFoundException when no slack config exists`() {
        assertThrows<NotFoundException> {
            slackService.sendNotification(
                "no-such-org",
                SlackNotification.TrailNonCompliant(
                    trailId = UUID.randomUUID(),
                    flowName = "test-flow",
                    missingAttestationTypes = listOf("BUILD"),
                    failedAttestationTypes = emptyList()
                )
            )
        }
    }

    @Test
    fun `verifySlackRequest passes with valid signature`() {
        slackService.configureSlack("my-org", testRequest)
        val timestamp = java.time.Instant.now().epochSecond.toString()
        val body = "command=%2Ffactstore&text=help"
        val sigBaseString = "v0:$timestamp:$body"
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec("test-secret".toByteArray(), "HmacSHA256"))
        val signature = "v0=" + mac.doFinal(sigBaseString.toByteArray()).joinToString("") { "%02x".format(it) }
        // should not throw
        slackService.verifySlackRequest("my-org", timestamp, signature, body)
    }

    @Test
    fun `verifySlackRequest throws BadRequestException with invalid signature`() {
        slackService.configureSlack("my-org", testRequest)
        val timestamp = java.time.Instant.now().epochSecond.toString()
        assertThrows<BadRequestException> {
            slackService.verifySlackRequest("my-org", timestamp, "v0=invalidsignature", "body")
        }
    }

    @Test
    fun `verifySlackRequest throws BadRequestException with missing signature headers`() {
        slackService.configureSlack("my-org", testRequest)
        assertThrows<BadRequestException> {
            slackService.verifySlackRequest("my-org", null, null, "body")
        }
    }

    @Test
    fun `verifySlackRequest throws BadRequestException with stale timestamp`() {
        slackService.configureSlack("my-org", testRequest)
        val staleTimestamp = (java.time.Instant.now().epochSecond - 400).toString()
        assertThrows<BadRequestException> {
            slackService.verifySlackRequest("my-org", staleTimestamp, "v0=sig", "body")
        }
    }

    @Test
    fun `verifySlackRequest throws NotFoundException for unknown org`() {
        assertThrows<NotFoundException> {
            slackService.verifySlackRequest("no-such-org", "12345", "v0=sig", "body")
        }
    }

    @Test
    fun `handleInteractiveAction returns error for malformed JSON payload`() {
        slackService.configureSlack("my-org", testRequest)
        val response = slackService.handleInteractiveAction("my-org", "not-valid-json{{{")
        assertTrue(response.text.contains("Invalid payload"))
    }
}
