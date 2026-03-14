package com.factstore

import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.UpdateFlowRequest
import com.factstore.exception.BadRequestException
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import com.factstore.application.FlowService
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

    @Test
    fun `create flow with tags persists tags in response`() {
        val tags = mapOf("risk-level" to "high", "team" to "payments")
        val resp = flowService.createFlow(CreateFlowRequest("tagged-flow", "desc", tags = tags))
        assertEquals(tags, resp.tags)
    }

    @Test
    fun `create flow without tags returns empty tags map`() {
        val resp = flowService.createFlow(CreateFlowRequest("no-tags-flow", "desc"))
        assertEquals(emptyMap<String, String>(), resp.tags)
    }

    @Test
    fun `update flow replaces all tags`() {
        val created = flowService.createFlow(CreateFlowRequest("tag-upd-flow", "desc", tags = mapOf("old-key" to "old-val")))
        val updated = flowService.updateFlow(created.id, UpdateFlowRequest(tags = mapOf("new-key" to "new-val")))
        assertEquals(mapOf("new-key" to "new-val"), updated.tags)
    }

    @Test
    fun `update flow with empty tags map clears all tags`() {
        val created = flowService.createFlow(CreateFlowRequest("tag-clear-flow", "desc", tags = mapOf("key" to "val")))
        val updated = flowService.updateFlow(created.id, UpdateFlowRequest(tags = emptyMap()))
        assertEquals(emptyMap<String, String>(), updated.tags)
    }

    @Test
    fun `update flow without tags field leaves existing tags unchanged`() {
        val created = flowService.createFlow(CreateFlowRequest("tag-noop-flow", "desc", tags = mapOf("keep" to "this")))
        val updated = flowService.updateFlow(created.id, UpdateFlowRequest(description = "updated"))
        assertEquals(mapOf("keep" to "this"), updated.tags)
    }

    @Test
    fun `create flow with more than 50 tags throws BadRequestException`() {
        val tags = (1..51).associate { "key-$it" to "value-$it" }
        assertThrows<BadRequestException> {
            flowService.createFlow(CreateFlowRequest("too-many-tags-flow", "desc", tags = tags))
        }
    }

    @Test
    fun `create flow with key exceeding 64 chars throws BadRequestException`() {
        val longKey = "k".repeat(65)
        assertThrows<BadRequestException> {
            flowService.createFlow(CreateFlowRequest("long-key-flow", "desc", tags = mapOf(longKey to "value")))
        }
    }

    @Test
    fun `create flow with value exceeding 256 chars throws BadRequestException`() {
        val longValue = "v".repeat(257)
        assertThrows<BadRequestException> {
            flowService.createFlow(CreateFlowRequest("long-val-flow", "desc", tags = mapOf("key" to longValue)))
        }
    }

    @Test
    fun `create flow with blank tag key throws BadRequestException`() {
        assertThrows<BadRequestException> {
            flowService.createFlow(CreateFlowRequest("blank-key-flow", "desc", tags = mapOf("  " to "value")))
        }
    }
}
