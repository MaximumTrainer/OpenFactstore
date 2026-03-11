package com.factstore

import com.factstore.application.GenericWebhookParser
import com.factstore.application.GitHubWebhookParser
import com.factstore.core.domain.AttestationStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WebhookParserTest {

    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        objectMapper = jacksonObjectMapper()
    }

    @Test
    fun `generic parser maps build succeeded event`() {
        val payload = """{"eventType":"build.succeeded","gitCommitSha":"abc123","gitBranch":"main","gitAuthor":"dev"}"""
        val event = GenericWebhookParser(objectMapper).parse(payload)
        assertEquals("build.succeeded", event.eventType)
        assertEquals("abc123", event.commitSha)
        assertEquals("main", event.branch)
        assertEquals("dev", event.actor)
        assertEquals("BUILD", event.attestationType)
        assertEquals(AttestationStatus.PASSED, event.attestationStatus)
    }

    @Test
    fun `generic parser maps build failed event`() {
        val payload = """{"eventType":"build.failed","gitCommitSha":"def456"}"""
        val event = GenericWebhookParser(objectMapper).parse(payload)
        assertEquals("build.failed", event.eventType)
        assertEquals("BUILD", event.attestationType)
        assertEquals(AttestationStatus.FAILED, event.attestationStatus)
    }

    @Test
    fun `generic parser maps test passed event`() {
        val payload = """{"eventType":"test.passed","gitCommitSha":"abc123"}"""
        val event = GenericWebhookParser(objectMapper).parse(payload)
        assertEquals("test.passed", event.eventType)
        assertEquals("TEST", event.attestationType)
        assertEquals(AttestationStatus.PASSED, event.attestationStatus)
    }

    @Test
    fun `generic parser maps test failed event`() {
        val payload = """{"eventType":"test.failed","gitCommitSha":"abc123"}"""
        val event = GenericWebhookParser(objectMapper).parse(payload)
        assertEquals("test.failed", event.eventType)
        assertEquals("TEST", event.attestationType)
        assertEquals(AttestationStatus.FAILED, event.attestationStatus)
    }

    @Test
    fun `generic parser maps scan passed event`() {
        val payload = """{"eventType":"scan.passed","gitCommitSha":"abc123"}"""
        val event = GenericWebhookParser(objectMapper).parse(payload)
        assertEquals("scan.passed", event.eventType)
        assertEquals("SECURITY_SCAN", event.attestationType)
        assertEquals(AttestationStatus.PASSED, event.attestationStatus)
    }

    @Test
    fun `generic parser maps scan failed event`() {
        val payload = """{"eventType":"scan.failed","gitCommitSha":"abc123"}"""
        val event = GenericWebhookParser(objectMapper).parse(payload)
        assertEquals("scan.failed", event.eventType)
        assertEquals("SECURITY_SCAN", event.attestationType)
        assertEquals(AttestationStatus.FAILED, event.attestationStatus)
    }

    @Test
    fun `generic parser uses explicit attestation type and status when provided`() {
        val payload = """{"eventType":"build.succeeded","attestationType":"CUSTOM","attestationStatus":"FAILED"}"""
        val event = GenericWebhookParser(objectMapper).parse(payload)
        assertEquals("CUSTOM", event.attestationType)
        assertEquals(AttestationStatus.FAILED, event.attestationStatus)
    }

    @Test
    fun `generic parser handles deployment triggered event`() {
        val payload = """{"eventType":"deployment.triggered","gitCommitSha":"abc123","gitAuthor":"deployer"}"""
        val event = GenericWebhookParser(objectMapper).parse(payload)
        assertEquals("deployment.triggered", event.eventType)
        assertEquals("deployer", event.actor)
    }

    @Test
    fun `generic parser handles approval granted event`() {
        val payload = """{"eventType":"approval.granted","gitCommitSha":"abc123","gitAuthor":"reviewer"}"""
        val event = GenericWebhookParser(objectMapper).parse(payload)
        assertEquals("approval.granted", event.eventType)
        assertEquals("reviewer", event.actor)
    }

    @Test
    fun `github parser maps completed success workflow_run`() {
        val payload = """{
            "action": "completed",
            "sender": {"login": "octocat"},
            "workflow_run": {
                "head_sha": "abc123",
                "head_branch": "main",
                "conclusion": "success",
                "actor": {"login": "octocat"}
            }
        }"""
        val event = GitHubWebhookParser(objectMapper).parse(payload)
        assertEquals("build.succeeded", event.eventType)
        assertEquals("abc123", event.commitSha)
        assertEquals("main", event.branch)
        assertEquals("octocat", event.actor)
        assertEquals("BUILD", event.attestationType)
        assertEquals(AttestationStatus.PASSED, event.attestationStatus)
    }

    @Test
    fun `github parser maps completed failure workflow_run`() {
        val payload = """{
            "action": "completed",
            "sender": {"login": "octocat"},
            "workflow_run": {
                "head_sha": "def456",
                "head_branch": "feature-x",
                "conclusion": "failure",
                "actor": {"login": "octocat"}
            }
        }"""
        val event = GitHubWebhookParser(objectMapper).parse(payload)
        assertEquals("build.failed", event.eventType)
        assertEquals("def456", event.commitSha)
        assertEquals("feature-x", event.branch)
        assertEquals("BUILD", event.attestationType)
        assertEquals(AttestationStatus.FAILED, event.attestationStatus)
    }

    @Test
    fun `github parser maps in_progress workflow_run as build started`() {
        val payload = """{
            "action": "in_progress",
            "sender": {"login": "octocat"},
            "workflow_run": {
                "head_sha": "abc123",
                "head_branch": "main",
                "conclusion": "",
                "actor": {"login": "octocat"}
            }
        }"""
        val event = GitHubWebhookParser(objectMapper).parse(payload)
        assertEquals("build.started", event.eventType)
        assertNull(event.attestationType)
        assertNull(event.attestationStatus)
    }
}
