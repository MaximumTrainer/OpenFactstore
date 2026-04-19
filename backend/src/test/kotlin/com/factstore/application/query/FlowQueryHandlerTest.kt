package com.factstore.application.query

import com.factstore.application.command.FlowCommandHandler
import com.factstore.dto.command.CreateFlowCommand
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
class FlowQueryHandlerTest {

    @Autowired
    lateinit var queryHandler: FlowQueryHandler

    @Autowired
    lateinit var commandHandler: FlowCommandHandler

    @Test
    fun `get flow returns FlowView after creation`() {
        val created = commandHandler.createFlow(CreateFlowCommand(name = "query-flow", description = "read test", requiredAttestationTypes = listOf("snyk")))
        val view = queryHandler.getFlow(created.id)
        assertEquals(created.id, view.id)
        assertEquals("query-flow", view.name)
        assertEquals("read test", view.description)
        assertEquals(listOf("snyk"), view.requiredAttestationTypes)
    }

    @Test
    fun `list flows returns FlowView list`() {
        commandHandler.createFlow(CreateFlowCommand(name = "qh-flow-a", description = "a"))
        commandHandler.createFlow(CreateFlowCommand(name = "qh-flow-b", description = "b"))
        val flows = queryHandler.listFlows()
        assertTrue(flows.size >= 2)
    }

    @Test
    fun `get flow by unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            queryHandler.getFlow(UUID.randomUUID())
        }
    }

    @Test
    fun `list flows by org returns correct views`() {
        commandHandler.createFlow(CreateFlowCommand(name = "org-flow-1", description = "a", orgSlug = "my-org"))
        commandHandler.createFlow(CreateFlowCommand(name = "org-flow-2", description = "b", orgSlug = "other-org"))
        val views = queryHandler.listFlowsByOrg("my-org")
        assertTrue(views.all { it.orgSlug == "my-org" })
        assertTrue(views.isNotEmpty())
    }

    @Test
    fun `get flow template returns FlowTemplateView`() {
        val created = commandHandler.createFlow(
            CreateFlowCommand(name = "template-flow", description = "test", templateYaml = "key: value")
        )
        val template = queryHandler.getFlowTemplate(created.id)
        assertEquals(created.id, template.flowId)
        assertEquals("key: value", template.templateYaml)
        assertNotNull(template.effectiveTemplate)
    }
}
