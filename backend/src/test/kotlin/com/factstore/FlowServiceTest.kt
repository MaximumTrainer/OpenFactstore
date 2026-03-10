package com.factstore

import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.UpdateFlowRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import com.factstore.repository.FlowRepository
import com.factstore.service.FlowService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@Transactional
class FlowServiceTest {

    @Autowired
    lateinit var flowService: FlowService

    @Autowired
    lateinit var flowRepository: FlowRepository

    @Test
    fun `create flow succeeds`() {
        val req = CreateFlowRequest("test-flow", "desc", listOf("junit", "snyk"))
        val resp = flowService.createFlow(req)
        assertEquals("test-flow", resp.name)
        assertEquals("desc", resp.description)
        assertEquals(listOf("junit", "snyk"), resp.requiredAttestationTypes)
        assertNotNull(resp.id)
    }

    @Test
    fun `create flow with duplicate name throws ConflictException`() {
        flowService.createFlow(CreateFlowRequest("dup-flow", "desc"))
        assertThrows<ConflictException> {
            flowService.createFlow(CreateFlowRequest("dup-flow", "other"))
        }
    }

    @Test
    fun `get flow by unknown id throws NotFoundException`() {
        assertThrows<NotFoundException> {
            flowService.getFlow(UUID.randomUUID())
        }
    }

    @Test
    fun `list flows returns all flows`() {
        flowService.createFlow(CreateFlowRequest("flow-a", "a"))
        flowService.createFlow(CreateFlowRequest("flow-b", "b"))
        val flows = flowService.listFlows()
        assertTrue(flows.size >= 2)
    }

    @Test
    fun `update flow updates fields`() {
        val created = flowService.createFlow(CreateFlowRequest("upd-flow", "old desc", listOf("junit")))
        val updated = flowService.updateFlow(created.id, UpdateFlowRequest(description = "new desc", requiredAttestationTypes = listOf("junit", "trivy")))
        assertEquals("new desc", updated.description)
        assertEquals(listOf("junit", "trivy"), updated.requiredAttestationTypes)
    }

    @Test
    fun `delete flow removes it`() {
        val created = flowService.createFlow(CreateFlowRequest("del-flow", "desc"))
        flowService.deleteFlow(created.id)
        assertThrows<NotFoundException> { flowService.getFlow(created.id) }
    }

    @Test
    fun `delete non-existent flow throws NotFoundException`() {
        assertThrows<NotFoundException> { flowService.deleteFlow(UUID.randomUUID()) }
    }
}
