package com.factstore

import com.factstore.testsupport.recordWithEvidence
import com.factstore.application.ArtifactService
import com.factstore.application.AttestationService
import com.factstore.application.FlowService
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.port.inbound.ITrailService
import com.factstore.dto.CreateArtifactRequest
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.TrailResponse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * API surface for #163: a pipeline must be able to assert its own execution
 * without relying on a digest that other executions may share.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssertEndpointTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var flowService: FlowService
    @Autowired lateinit var trailService: ITrailService
    @Autowired lateinit var attestationService: AttestationService
    @Autowired lateinit var artifactService: ArtifactService

    private fun newTrail(flowId: UUID): TrailResponse = trailService.createTrail(
        CreateTrailRequest(
            flowId = flowId,
            gitCommitSha = "commit-sha",
            gitBranch = "main",
            gitAuthor = "a",
            gitAuthorEmail = "a@example.com"
        )
    )

    @Test
    fun `POST trails id assert judges that trail and reports its id`() {
        val flow = flowService.createFlow(CreateFlowRequest("flow-ep-${System.nanoTime()}", "d", listOf("junit")))
        val trail = newTrail(flow.id)
        attestationService.recordWithEvidence(
            trail.id,
            CreateAttestationRequest(type = "junit", status = AttestationStatus.PASSED)
        )

        mockMvc.post("/api/v1/trails/${trail.id}/assert") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("COMPLIANT") }
            jsonPath("$.trailId") { value(trail.id.toString()) }
            jsonPath("$.flowId") { value(flow.id.toString()) }
        }
    }

    @Test
    fun `POST trails id assert reports NON_COMPLIANT with the missing types`() {
        val flow = flowService.createFlow(
            CreateFlowRequest("flow-ep-nc-${System.nanoTime()}", "d", listOf("junit", "snyk"))
        )
        val trail = newTrail(flow.id)
        attestationService.recordWithEvidence(
            trail.id,
            CreateAttestationRequest(type = "junit", status = AttestationStatus.PASSED)
        )

        mockMvc.post("/api/v1/trails/${trail.id}/assert") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("NON_COMPLIANT") }
            jsonPath("$.missingAttestationTypes[0]") { value("snyk") }
        }
    }

    @Test
    fun `POST assert with a trailId scopes the verdict to that trail`() {
        val flow = flowService.createFlow(
            CreateFlowRequest("flow-ep-scope-${System.nanoTime()}", "d", listOf("junit"))
        )
        val digest = "sha256:ep${System.nanoTime()}"

        val older = newTrail(flow.id)
        attestationService.recordWithEvidence(
            older.id,
            CreateAttestationRequest(type = "junit", status = AttestationStatus.PASSED)
        )
        artifactService.reportArtifact(older.id, CreateArtifactRequest("img", "1", digest, reportedBy = "ci"))

        val newer = newTrail(flow.id)
        artifactService.reportArtifact(newer.id, CreateArtifactRequest("img", "1", digest, reportedBy = "ci"))

        mockMvc.post("/api/v1/assert") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"sha256Digest":"$digest","flowId":"${flow.id}","trailId":"${older.id}"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("COMPLIANT") }
            jsonPath("$.trailId") { value(older.id.toString()) }
        }

        mockMvc.post("/api/v1/assert") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"sha256Digest":"$digest","flowId":"${flow.id}","trailId":"${newer.id}"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("NON_COMPLIANT") }
            jsonPath("$.trailId") { value(newer.id.toString()) }
        }
    }

    @Test
    fun `POST trails id assert on an unknown trail returns 404`() {
        mockMvc.post("/api/v1/trails/${UUID.randomUUID()}/assert") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isNotFound() }
        }
    }
}
