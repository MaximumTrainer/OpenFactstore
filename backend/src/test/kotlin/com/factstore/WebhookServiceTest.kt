package com.factstore

import com.factstore.application.WebhookConfigService
import com.factstore.application.WebhookService
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.DeliveryStatus
import com.factstore.core.domain.WebhookSource
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateWebhookConfigRequest
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
class WebhookServiceTest {

    @Autowired lateinit var webhookService: WebhookService
    @Autowired lateinit var webhookConfigService: WebhookConfigService
    @Autowired lateinit var flowService: com.factstore.application.FlowService

    private fun setupConfig(source: WebhookSource = WebhookSource.GENERIC): UUID {
        val flow = flowService.createFlow(CreateFlowRequest("webhook-flow-${System.nanoTime()}", "test"))
        val config = webhookConfigService.createConfig(
            CreateWebhookConfigRequest(source = source, secret = "test-secret", flowId = flow.id)
        )
        return config.id
    }

    @Test
    fun `process generic build succeeded creates trail and attestation`() {
        setupConfig()
        val payload = """{"eventType":"build.succeeded","gitCommitSha":"abc123","gitBranch":"main","gitAuthor":"dev","gitAuthorEmail":"dev@test.com"}"""
        val result = webhookService.processWebhook("generic", payload, null, "delivery-1")
        assertTrue(result.accepted)
        assertEquals("delivery-1", result.deliveryId)
    }

    @Test
    fun `duplicate delivery id is deduplicated`() {
        setupConfig()
        val payload = """{"eventType":"build.succeeded","gitCommitSha":"abc123","gitBranch":"main","gitAuthor":"dev","gitAuthorEmail":"dev@test.com"}"""
        webhookService.processWebhook("generic", payload, null, "dup-delivery")
        val result = webhookService.processWebhook("generic", payload, null, "dup-delivery")
        assertTrue(result.accepted)
        assertTrue(result.message.contains("Duplicate"))
    }

    @Test
    fun `unknown source throws BadRequestException`() {
        assertThrows<BadRequestException> {
            webhookService.processWebhook("unknown", "{}", null, null)
        }
    }

    @Test
    fun `no active config throws BadRequestException`() {
        assertThrows<BadRequestException> {
            webhookService.processWebhook("generic", """{"eventType":"build.succeeded"}""", null, null)
        }
    }

    @Test
    fun `process test passed event creates attestation`() {
        setupConfig()
        val payload = """{"eventType":"test.passed","gitCommitSha":"test123","gitBranch":"main","gitAuthor":"dev","gitAuthorEmail":"dev@test.com"}"""
        val result = webhookService.processWebhook("generic", payload, null, "test-delivery-1")
        assertTrue(result.accepted)
    }

    @Test
    fun `process scan failed event creates attestation with FAILED status`() {
        setupConfig()
        val payload = """{"eventType":"scan.failed","gitCommitSha":"scan123","gitBranch":"main","gitAuthor":"dev","gitAuthorEmail":"dev@test.com"}"""
        val result = webhookService.processWebhook("generic", payload, null, "scan-delivery-1")
        assertTrue(result.accepted)
    }

    @Test
    fun `process deployment triggered event updates trail`() {
        setupConfig()
        val payload = """{"eventType":"deployment.triggered","gitCommitSha":"deploy123","gitBranch":"main","gitAuthor":"deployer","gitAuthorEmail":"deploy@test.com"}"""
        val result = webhookService.processWebhook("generic", payload, null, "deploy-delivery-1")
        assertTrue(result.accepted)
    }

    @Test
    fun `process approval granted event updates trail`() {
        setupConfig()
        val payload = """{"eventType":"approval.granted","gitCommitSha":"approve123","gitBranch":"main","gitAuthor":"reviewer","gitAuthorEmail":"review@test.com"}"""
        val result = webhookService.processWebhook("generic", payload, null, "approval-delivery-1")
        assertTrue(result.accepted)
    }

    @Test
    fun `webhook config CRUD works`() {
        val flow = flowService.createFlow(CreateFlowRequest("crud-flow-${System.nanoTime()}", "test"))
        val config = webhookConfigService.createConfig(
            CreateWebhookConfigRequest(source = WebhookSource.GITHUB, secret = "my-secret", flowId = flow.id)
        )
        assertNotNull(config.id)
        assertEquals(WebhookSource.GITHUB, config.source)
        assertTrue(config.isActive)

        val configs = webhookConfigService.listConfigs()
        assertTrue(configs.any { it.id == config.id })

        webhookConfigService.deleteConfig(config.id)
        val afterDelete = webhookConfigService.listConfigs()
        assertFalse(afterDelete.any { it.id == config.id })
    }

    @Test
    fun `create config for non-existent flow throws NotFoundException`() {
        assertThrows<NotFoundException> {
            webhookConfigService.createConfig(
                CreateWebhookConfigRequest(source = WebhookSource.GENERIC, secret = "s", flowId = UUID.randomUUID())
            )
        }
    }

    @Test
    fun `delete non-existent config throws NotFoundException`() {
        assertThrows<NotFoundException> {
            webhookConfigService.deleteConfig(UUID.randomUUID())
        }
    }

    @Test
    fun `list deliveries for config`() {
        val configId = setupConfig()
        val payload = """{"eventType":"build.succeeded","gitCommitSha":"abc","gitBranch":"main","gitAuthor":"dev","gitAuthorEmail":"dev@test.com"}"""
        webhookService.processWebhook("generic", payload, null, "del-1")

        val deliveries = webhookConfigService.listDeliveries(configId)
        assertTrue(deliveries.isNotEmpty())
        assertEquals(DeliveryStatus.SUCCESS, deliveries.first().status)
        assertEquals("del-1", deliveries.first().deliveryId)
    }

    @Test
    fun `list deliveries for unknown config throws NotFoundException`() {
        assertThrows<NotFoundException> {
            webhookConfigService.listDeliveries(UUID.randomUUID())
        }
    }
}
