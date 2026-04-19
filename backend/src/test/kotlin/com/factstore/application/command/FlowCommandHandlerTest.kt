package com.factstore.application.command

import com.factstore.dto.command.CreateFlowCommand
import com.factstore.dto.command.DeleteFlowCommand
import com.factstore.dto.command.UpdateFlowCommand
import com.factstore.exception.BadRequestException
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class FlowCommandHandlerTest {

    @Autowired
    lateinit var commandHandler: FlowCommandHandler

    @Test
    fun `create flow command returns CommandResult with id`() {
        val command = CreateFlowCommand(name = "cmd-flow", description = "test desc", requiredAttestationTypes = listOf("junit"))
        val result = commandHandler.createFlow(command)
        assertNotNull(result.id)
        assertEquals("created", result.status)
        assertNotNull(result.timestamp)
    }

    @Test
    fun `create flow with duplicate name throws ConflictException`() {
        commandHandler.createFlow(CreateFlowCommand(name = "dup-cmd-flow", description = "first"))
        assertThrows<ConflictException> {
            commandHandler.createFlow(CreateFlowCommand(name = "dup-cmd-flow", description = "second"))
        }
    }

    @Test
    fun `update flow command returns CommandResult with updated status`() {
        val created = commandHandler.createFlow(CreateFlowCommand(name = "upd-cmd-flow", description = "old"))
        val result = commandHandler.updateFlow(UpdateFlowCommand(id = created.id, description = "new desc"))
        assertEquals(created.id, result.id)
        assertEquals("updated", result.status)
    }

    @Test
    fun `update non-existent flow throws NotFoundException`() {
        assertThrows<NotFoundException> {
            commandHandler.updateFlow(UpdateFlowCommand(id = java.util.UUID.randomUUID(), name = "no-such"))
        }
    }

    @Test
    fun `delete flow command succeeds`() {
        val created = commandHandler.createFlow(CreateFlowCommand(name = "del-cmd-flow", description = "to delete"))
        commandHandler.deleteFlow(DeleteFlowCommand(created.id))
    }

    @Test
    fun `delete non-existent flow throws NotFoundException`() {
        assertThrows<NotFoundException> {
            commandHandler.deleteFlow(DeleteFlowCommand(java.util.UUID.randomUUID()))
        }
    }

    @Test
    fun `create flow with tags persists via command`() {
        val tags = mapOf("env" to "prod", "team" to "platform")
        val result = commandHandler.createFlow(CreateFlowCommand(name = "tagged-cmd-flow", description = "with tags", tags = tags))
        assertNotNull(result.id)
    }

    @Test
    fun `create flow with more than 50 tags throws BadRequestException`() {
        val tags = (1..51).associate { "key-$it" to "value-$it" }
        assertThrows<BadRequestException> {
            commandHandler.createFlow(CreateFlowCommand(name = "too-many-tags-cmd", description = "fail", tags = tags))
        }
    }
}
