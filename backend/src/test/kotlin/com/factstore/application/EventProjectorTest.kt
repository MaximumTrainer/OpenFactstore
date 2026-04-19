package com.factstore.application

import com.factstore.adapter.mock.InMemoryEventStore
import com.factstore.core.domain.EventLogEntry
import com.factstore.core.domain.event.DomainEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class EventProjectorTest {

    private lateinit var eventStore: InMemoryEventStore
    private lateinit var objectMapper: ObjectMapper
    private lateinit var projector: EventProjector

    @BeforeEach
    fun setUp() {
        eventStore = InMemoryEventStore()
        objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        projector = EventProjector(eventStore, objectMapper)
    }

    @Test
    fun `replayAll processes all events`() {
        seedEvents()
        val replayed = mutableListOf<DomainEvent>()
        val count = projector.replayAll { replayed.add(it) }
        assertEquals(3L, count)
        assertEquals(3, replayed.size)
        assertTrue(replayed[0] is DomainEvent.FlowCreated)
        assertTrue(replayed[1] is DomainEvent.FlowUpdated)
        assertTrue(replayed[2] is DomainEvent.FlowDeleted)
    }

    @Test
    fun `replayAll returns zero for empty store`() {
        val count = projector.replayAll { }
        assertEquals(0L, count)
    }

    @Test
    fun `replayAfter returns only newer events`() {
        seedEvents()
        val replayed = mutableListOf<DomainEvent>()
        val count = projector.replayAfter(1L) { replayed.add(it) }
        assertEquals(2L, count)
        assertTrue(replayed[0] is DomainEvent.FlowUpdated)
        assertTrue(replayed[1] is DomainEvent.FlowDeleted)
    }

    @Test
    fun `replayAggregate replays only events for that aggregate`() {
        val flowA = UUID.randomUUID()
        val flowB = UUID.randomUUID()
        appendEvent(DomainEvent.FlowCreated(aggregateId = flowA, name = "A", description = ""))
        appendEvent(DomainEvent.FlowCreated(aggregateId = flowB, name = "B", description = ""))
        appendEvent(DomainEvent.FlowUpdated(aggregateId = flowA, name = "A-updated"))

        val replayed = mutableListOf<DomainEvent>()
        val count = projector.replayAggregate(flowA) { replayed.add(it) }
        assertEquals(2L, count)
        assertTrue(replayed.all { it.aggregateId == flowA })
    }

    @Test
    fun `unknown event type is skipped`() {
        eventStore.append(EventLogEntry(
            aggregateId = UUID.randomUUID(),
            aggregateType = "Unknown",
            eventType = "SomeFutureEvent",
            payload = "{}"
        ))
        val replayed = mutableListOf<DomainEvent>()
        val count = projector.replayAll { replayed.add(it) }
        assertEquals(0L, count)
        assertEquals(0, replayed.size)
    }

    @Test
    fun `malformed payload is skipped`() {
        eventStore.append(EventLogEntry(
            aggregateId = UUID.randomUUID(),
            aggregateType = "Flow",
            eventType = "FlowCreated",
            payload = "NOT VALID JSON"
        ))
        val replayed = mutableListOf<DomainEvent>()
        val count = projector.replayAll { replayed.add(it) }
        assertEquals(0L, count)
        assertEquals(0, replayed.size)
    }

    @Test
    fun `replayed FlowCreated event has correct data`() {
        val flowId = UUID.randomUUID()
        appendEvent(DomainEvent.FlowCreated(
            aggregateId = flowId,
            name = "my-flow",
            description = "desc",
            tags = mapOf("env" to "prod"),
            requiresApproval = true
        ))
        val replayed = mutableListOf<DomainEvent>()
        projector.replayAll { replayed.add(it) }
        val event = replayed[0] as DomainEvent.FlowCreated
        assertEquals(flowId, event.aggregateId)
        assertEquals("my-flow", event.name)
        assertEquals("desc", event.description)
        assertEquals(mapOf("env" to "prod"), event.tags)
        assertTrue(event.requiresApproval)
    }

    private fun seedEvents() {
        val flowId = UUID.randomUUID()
        appendEvent(DomainEvent.FlowCreated(aggregateId = flowId, name = "test", description = "d"))
        appendEvent(DomainEvent.FlowUpdated(aggregateId = flowId, name = "test-updated"))
        appendEvent(DomainEvent.FlowDeleted(aggregateId = flowId))
    }

    private fun appendEvent(event: DomainEvent) {
        eventStore.append(EventLogEntry(
            eventId = event.eventId,
            aggregateId = event.aggregateId,
            aggregateType = event.aggregateType,
            eventType = event::class.simpleName ?: "Unknown",
            payload = objectMapper.writeValueAsString(event),
            occurredAt = event.occurredAt
        ))
    }
}
