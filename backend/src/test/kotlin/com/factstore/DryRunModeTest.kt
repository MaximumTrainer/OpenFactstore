package com.factstore

import com.factstore.core.port.inbound.IAttestationService
import com.factstore.core.port.inbound.IFlowService
import com.factstore.core.port.inbound.ITrailService
import com.factstore.dto.FlowResponse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class DryRunModeTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockBean
    lateinit var flowService: IFlowService

    @MockBean
    lateinit var trailService: ITrailService

    @MockBean
    lateinit var attestationService: IAttestationService

    private val trailId: UUID = UUID.randomUUID()

    private fun sampleFlowResponse() = FlowResponse(
        id = UUID.randomUUID(),
        name = "my-flow",
        description = "test",
        requiredAttestationTypes = emptyList(),
        tags = emptyMap(),
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun `POST flow with X-Factstore-Dry-Run header returns 200 with dryRun true and does not call service`() {
        mockMvc.post("/api/v1/flows") {
            header("X-Factstore-Dry-Run", "true")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"my-flow","description":"test"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.dryRun") { value(true) }
            jsonPath("$.wouldCreate.name") { value("my-flow") }
        }
        verify(flowService, never()).createFlow(any())
    }

    @Test
    fun `POST flow with dryRun query param returns 200 with dryRun true and does not call service`() {
        mockMvc.post("/api/v1/flows?dryRun=true") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"my-flow","description":"test"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.dryRun") { value(true) }
            jsonPath("$.wouldCreate.name") { value("my-flow") }
        }
        verify(flowService, never()).createFlow(any())
    }

    @Test
    fun `POST flow without dry-run flag calls service and returns 201`() {
        whenever(flowService.createFlow(any())).thenReturn(sampleFlowResponse())

        mockMvc.post("/api/v1/flows") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"my-flow","description":"test"}"""
        }.andExpect {
            status { isCreated() }
        }
        verify(flowService).createFlow(any())
    }

    @Test
    fun `POST trail with X-Factstore-Dry-Run header returns 200 with dryRun true and does not call service`() {
        val flowId = UUID.randomUUID()

        mockMvc.post("/api/v1/trails") {
            header("X-Factstore-Dry-Run", "true")
            contentType = MediaType.APPLICATION_JSON
            content = """{
                "flowId": "$flowId",
                "gitCommitSha": "abc123",
                "gitBranch": "main",
                "gitAuthor": "Alice",
                "gitAuthorEmail": "alice@example.com"
            }"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.dryRun") { value(true) }
            jsonPath("$.wouldCreate.gitBranch") { value("main") }
            jsonPath("$.wouldCreate.status") { value("PENDING") }
        }
        verify(trailService, never()).createTrail(any())
    }

    @Test
    fun `POST attestation with X-Factstore-Dry-Run header returns 200 with dryRun true and does not call service`() {
        mockMvc.post("/api/v1/trails/$trailId/attestations") {
            header("X-Factstore-Dry-Run", "true")
            contentType = MediaType.APPLICATION_JSON
            content = """{"type":"junit","status":"PASSED"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.dryRun") { value(true) }
            jsonPath("$.wouldCreate.type") { value("junit") }
            jsonPath("$.wouldCreate.compliant") { value(true) }
        }
        verify(attestationService, never()).recordAttestation(any(), any())
    }

    @Test
    fun `invalid JSON payload with dry-run header still returns 400`() {
        mockMvc.post("/api/v1/flows") {
            header("X-Factstore-Dry-Run", "true")
            contentType = MediaType.APPLICATION_JSON
            content = """not-valid-json"""
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
