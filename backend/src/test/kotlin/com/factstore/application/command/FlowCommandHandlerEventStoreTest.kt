package com.factstore.application.command

import com.factstore.core.port.outbound.IEventStore
import com.factstore.dto.command.CreateFlowCommand
import com.factstore.dto.command.DeleteFlowCommand
import com.factstore.dto.command.UpdateFlowCommand
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class FlowCommandHandlerEventStoreTest {

    @Autowired
    lateinit var commandHandler: FlowCommandHandler

    @Autowired
    lateinit var eventStore: IEventStore

    @Test
    fun `createFlow emits FlowCreated event to event store`() {
        val result = commandHandler.createFlow(CreateFlowCommand(
            name = "es-flow",
            description = "event sourced",
            requiredAttestationTypes = listOf("junit"),
            tags = mapOf("env" to "test")
        ))
        val events = eventStore.findByAggregateId(result.id)
        assertEquals(1, events.size)
        assertEquals("FlowCreated", events[0].eventType)
        assertEquals("Flow", events[0].aggregateType)
        assertTrue(events[0].payload.contains("es-flow"))
    }

    @Test
    fun `updateFlow emits FlowUpdated event to event store`() {
        val created = commandHandler.createFlow(CreateFlowCommand(name = "es-upd", description = "original"))
        commandHandler.updateFlow(UpdateFlowCommand(id = created.id, description = "modified"))
        val events = eventStore.findByAggregateId(created.id)
        assertEquals(2, events.size)
        assertEquals("FlowCreated", events[0].eventType)
        assertEquals("FlowUpdated", events[1].eventType)
    }

    @Test
    fun `deleteFlow emits FlowDeleted event before removal`() {
        val created = commandHandler.createFlow(CreateFlowCommand(name = "es-del", description = "to remove"))
        commandHandler.deleteFlow(DeleteFlowCommand(created.id))
        val events = eventStore.findByAggregateId(created.id)
        assertEquals(2, events.size)
        assertEquals("FlowCreated", events[0].eventType)
        assertEquals("FlowDeleted", events[1].eventType)
    }

    @Test
    fun `event store records are ordered by sequenceNumber`() {
        val created = commandHandler.createFlow(CreateFlowCommand(name = "es-order", description = "d"))
        commandHandler.updateFlow(UpdateFlowCommand(id = created.id, name = "es-order-updated"))
        val events = eventStore.findByAggregateId(created.id)
        assertTrue(events.size >= 2)
        val sequenceNumbers = events.map { it.sequenceNumber }
        assertTrue(sequenceNumbers.zipWithNext().all { (a, b) -> a <= b })
    }
}
