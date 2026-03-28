package com.factstore.application.command

import com.factstore.adapter.mock.InMemoryEventStore
import com.factstore.core.domain.EventLogEntry
import com.factstore.core.domain.event.DomainEvent
import com.factstore.core.port.outbound.IDomainEventBus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class EventAppenderTest {

    private lateinit var eventStore: InMemoryEventStore
    private lateinit var objectMapper: ObjectMapper
    private lateinit var eventAppender: EventAppender
    private val noopBus = object : IDomainEventBus {
        override fun publish(entry: EventLogEntry) { /* no-op for test */ }
    }

    @BeforeEach
    fun setUp() {
        eventStore = InMemoryEventStore()
        objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        eventAppender = EventAppender(eventStore, objectMapper, noopBus)
    }

    @Test
    fun `append stores FlowCreated event in event store`() {
        val flowId = UUID.randomUUID()
        val event = DomainEvent.FlowCreated(
            aggregateId = flowId,
            name = "my-flow",
            description = "A test flow"
        )
        eventAppender.append(event)

        val stored = eventStore.events()
        assertEquals(1, stored.size)
        assertEquals(flowId, stored[0].aggregateId)
        assertEquals("Flow", stored[0].aggregateType)
        assertEquals("FlowCreated", stored[0].eventType)
        assertTrue(stored[0].payload.contains("my-flow"))
    }

    @Test
    fun `append stores FlowUpdated event with correct type`() {
        val flowId = UUID.randomUUID()
        val event = DomainEvent.FlowUpdated(
            aggregateId = flowId,
            name = "updated-name"
        )
        eventAppender.append(event)

        val stored = eventStore.events()
        assertEquals(1, stored.size)
        assertEquals("FlowUpdated", stored[0].eventType)
        assertEquals("Flow", stored[0].aggregateType)
    }

    @Test
    fun `append stores FlowDeleted event`() {
        val flowId = UUID.randomUUID()
        eventAppender.append(DomainEvent.FlowDeleted(aggregateId = flowId))

        val stored = eventStore.events()
        assertEquals(1, stored.size)
        assertEquals("FlowDeleted", stored[0].eventType)
    }

    @Test
    fun `append stores TrailCreated event`() {
        val trailId = UUID.randomUUID()
        val flowId = UUID.randomUUID()
        eventAppender.append(DomainEvent.TrailCreated(
            aggregateId = trailId,
            flowId = flowId,
            gitCommitSha = "abc123",
            gitBranch = "main",
            gitAuthor = "user",
            gitAuthorEmail = "user@example.com"
        ))

        val stored = eventStore.events()
        assertEquals(1, stored.size)
        assertEquals("TrailCreated", stored[0].eventType)
        assertEquals("Trail", stored[0].aggregateType)
    }

    @Test
    fun `append stores ArtifactReported event`() {
        val artifactId = UUID.randomUUID()
        val trailId = UUID.randomUUID()
        eventAppender.append(DomainEvent.ArtifactReported(
            aggregateId = artifactId,
            trailId = trailId,
            imageName = "myapp",
            imageTag = "v1.0",
            sha256Digest = "sha256:abc",
            reportedBy = "ci-bot"
        ))

        val stored = eventStore.events()
        assertEquals(1, stored.size)
        assertEquals("ArtifactReported", stored[0].eventType)
        assertEquals("Artifact", stored[0].aggregateType)
    }

    @Test
    fun `append stores AttestationRecorded event`() {
        val attestationId = UUID.randomUUID()
        val trailId = UUID.randomUUID()
        eventAppender.append(DomainEvent.AttestationRecorded(
            aggregateId = attestationId,
            trailId = trailId,
            type = "snyk-scan",
            status = "PASSED"
        ))

        val stored = eventStore.events()
        assertEquals(1, stored.size)
        assertEquals("AttestationRecorded", stored[0].eventType)
        assertEquals("Attestation", stored[0].aggregateType)
    }

    @Test
    fun `append stores EvidenceUploaded event`() {
        val attestationId = UUID.randomUUID()
        val trailId = UUID.randomUUID()
        eventAppender.append(DomainEvent.EvidenceUploaded(
            aggregateId = attestationId,
            trailId = trailId,
            fileName = "report.pdf",
            contentType = "application/pdf",
            sha256Hash = "sha256:def",
            fileSizeBytes = 1024
        ))

        val stored = eventStore.events()
        assertEquals(1, stored.size)
        assertEquals("EvidenceUploaded", stored[0].eventType)
    }

    @Test
    fun `multiple events get sequential numbers`() {
        val id = UUID.randomUUID()
        eventAppender.append(DomainEvent.FlowCreated(aggregateId = id, name = "f1", description = "d1"))
        eventAppender.append(DomainEvent.FlowUpdated(aggregateId = id, name = "f1-updated"))
        eventAppender.append(DomainEvent.FlowDeleted(aggregateId = id))

        val stored = eventStore.events()
        assertEquals(3, stored.size)
        assertEquals(1L, stored[0].sequenceNumber)
        assertEquals(2L, stored[1].sequenceNumber)
        assertEquals(3L, stored[2].sequenceNumber)
    }

    @Test
    fun `payload is valid JSON`() {
        val flowId = UUID.randomUUID()
        eventAppender.append(DomainEvent.FlowCreated(
            aggregateId = flowId,
            name = "json-test",
            description = "Testing JSON serialization",
            tags = mapOf("env" to "prod")
        ))

        val stored = eventStore.events()[0]
        val parsed = objectMapper.readTree(stored.payload)
        assertEquals("json-test", parsed["name"].asText())
        assertEquals("prod", parsed["tags"]["env"].asText())
    }
}
