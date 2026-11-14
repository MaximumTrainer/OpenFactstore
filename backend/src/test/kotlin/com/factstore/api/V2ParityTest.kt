package com.factstore.api

import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * #250 — the v1 twins are deprecated in favour of v2, so v2 must do everything they did before
 * they go: the same request fields, the same response fields, hiding archived records by default
 * and paging long lists.
 */
@SpringBootTest(properties = ["security.enforce-auth=false"])
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = ["SCOPE_admin"])
class V2ParityTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var flows: IFlowRepository
    @Autowired lateinit var trails: ITrailRepository

    private val mapper = jacksonObjectMapper()
    private val sha = "a".repeat(40)

    private fun json(body: String): JsonNode = mapper.readTree(body)

    private fun post(path: String, body: String): JsonNode = json(
        mockMvc.post(path) { contentType = MediaType.APPLICATION_JSON; content = body }
            .andExpect { status { is2xxSuccessful() } }.andReturn().response.contentAsString
    )

    private fun get(path: String): JsonNode =
        json(mockMvc.get(path).andExpect { status { isOk() } }.andReturn().response.contentAsString)

    private fun flow(extra: String = ""): UUID =
        UUID.fromString(post("/api/v2/flows", """{"name":"parity-${UUID.randomUUID()}"$extra}""")["id"].asText())

    private fun trail(flowId: UUID, extra: String = ""): UUID = UUID.fromString(post(
        "/api/v2/trails",
        """{"flowId":"$flowId","gitCommitSha":"$sha","gitBranch":"main","gitAuthor":"a","gitAuthorEmail":"a@example.com"$extra}"""
    )["id"].asText())

    @Test
    fun `#250 a v2 flow can be created from hub templates and shows where its template came from`() {
        val id = flow(""","templateIds":["service-batch-job"]""")
        val view = get("/api/v2/flows/$id")
        assertThat(view["templateId"].asText()).isEqualTo("service-batch-job")
        assertThat(view["templateVersion"].asText()).isNotBlank()
        assertThat(view["templateYaml"].asText()).isNotBlank()
        assertThat(view.has("archivedAt")).isTrue()
    }

    @Test
    fun `#250 v2 lists hide archived flows and trails unless asked`() {
        val archivedFlow = flow()
        flows.findById(archivedFlow)!!.also { it.archivedAt = Instant.now(); flows.save(it) }
        val live = flow()
        val archivedTrail = trail(live)
        trails.findById(archivedTrail)!!.also { it.archivedAt = Instant.now(); trails.save(it) }
        val liveTrail = trail(live)

        fun ids(node: JsonNode) = node.map { it["id"].asText() }
        assertThat(ids(get("/api/v2/flows"))).contains(live.toString()).doesNotContain(archivedFlow.toString())
        assertThat(ids(get("/api/v2/flows?includeArchived=true"))).contains(archivedFlow.toString())
        assertThat(ids(get("/api/v2/trails?flowId=$live"))).containsExactly(liveTrail.toString())
        assertThat(ids(get("/api/v2/trails?flowId=$live&includeArchived=true"))).contains(archivedTrail.toString())
        assertThat(ids(get("/api/v2/flows/$live/trails"))).containsExactly(liveTrail.toString())
        assertThat(ids(get("/api/v2/flows/$live/trails?includeArchived=true"))).hasSize(2)
        assertThat(get("/api/v2/trails/$archivedTrail")["archivedAt"].isNull).isFalse()
    }

    @Test
    fun `#250 v2 trail lists and attestation lists can be paged`() {
        val flowId = flow()
        repeat(3) { trail(flowId) }
        mockMvc.get("/api/v2/flows/$flowId/trails?page=1&size=2").andExpect {
            status { isOk() }
            header { string("X-Total-Count", "3") }
            jsonPath("$.length()") { value(1) }
        }
        val trailId = trail(flowId)
        repeat(3) { post("/api/v2/trails/$trailId/attestations", """{"type":"junit","status":"PASSED"}""") }
        mockMvc.get("/api/v2/trails/$trailId/attestations?page=0&size=2").andExpect {
            header { string("X-Total-Count", "3") }
            jsonPath("$.length()") { value(2) }
        }
        mockMvc.get("/api/v2/trails/$trailId/attestations").andExpect { jsonPath("$.length()") { value(3) } }
    }

    @Test
    fun `#250 v2 trails and artifacts keep their tags`() {
        val trailId = trail(flow(), ""","tags":{"team":"payments"}""")
        assertThat(get("/api/v2/trails/$trailId")["tags"]["team"].asText()).isEqualTo("payments")

        val digest = "sha256:" + "b".repeat(64)
        post("/api/v2/trails/$trailId/artifacts",
            """{"imageName":"app","imageTag":"1.0","sha256Digest":"$digest","reportedBy":"ci","tags":{"tier":"gold"}}""")
        assertThat(get("/api/v2/trails/$trailId/artifacts").single()["tags"]["tier"].asText()).isEqualTo("gold")
    }

    @Test
    fun `#250 a v2 attestation keeps its data, links, annotations and git context`() {
        val trailId = trail(flow())
        post("/api/v2/trails/$trailId/attestations", """{
            "type":"junit","status":"PASSED",
            "attestationData":"{\"tests\":10}",
            "externalUrls":["https://ci.example.com/run/1"],
            "annotations":{"suite":"unit"},
            "gitCommitSha":"$sha","gitBranch":"main","gitRepoUrl":"https://example.com/repo.git"
        }""")
        val view = get("/api/v2/trails/$trailId/attestations").single()
        assertThat(view["attestationData"].asText()).isEqualTo("""{"tests":10}""")
        assertThat(view["externalUrls"].map { it.asText() }).containsExactly("https://ci.example.com/run/1")
        assertThat(view["annotations"]["suite"].asText()).isEqualTo("unit")
        assertThat(view["gitCommitSha"].asText()).isEqualTo(sha)
        assertThat(view["gitBranch"].asText()).isEqualTo("main")
        assertThat(view["gitRepoUrl"].asText()).isEqualTo("https://example.com/repo.git")
        assertThat(view.has("artifactId")).isTrue()
        assertThat(view.has("justification")).isTrue()
        assertThat(view.has("overridesAttestationId")).isTrue()
    }

    @Test
    fun `#250 a v2 attestation is checked against its custom type like v1`() {
        val type = "parity-type-${System.nanoTime()}"
        post("/api/v1/attestation-types", """{"name":"$type",
            "schemaJson":"{\"type\":\"object\",\"required\":[\"ok\"]}",
            "jqExpression":".ok == true"}""")
        val trailId = trail(flow())

        mockMvc.post("/api/v2/trails/$trailId/attestations") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"type":"$type","status":"PASSED","attestationData":"{}"}"""
        }.andExpect { status { isBadRequest() } }

        post("/api/v2/trails/$trailId/attestations", """{"type":"$type","status":"PASSED","attestationData":"{\"ok\":false}"}""")
        assertThat(get("/api/v2/trails/$trailId/attestations").single()["status"].asText()).isEqualTo("FAILED")
    }

    @Test
    fun `#250 v2 refuses the same bad input v1 refuses`() {
        fun refused(path: String, body: String) = mockMvc.post(path) {
            contentType = MediaType.APPLICATION_JSON; content = body
        }.andExpect { status { isBadRequest() } }

        refused("/api/v2/flows", """{"name":"  "}""")
        refused("/api/v2/flows", """{"name":"${"x".repeat(256)}"}""")
        val flowId = flow()
        refused("/api/v2/trails", """{"flowId":"$flowId","gitCommitSha":"$sha","gitBranch":"main","gitAuthor":" ","gitAuthorEmail":"a@example.com"}""")
        refused("/api/v2/trails", """{"flowId":"$flowId","gitCommitSha":"$sha","gitBranch":"main","gitAuthor":"a","gitAuthorEmail":"not-an-address"}""")
        val trailId = trail(flowId)
        refused("/api/v2/trails/$trailId/artifacts", """{"imageName":"app","imageTag":"1.0","sha256Digest":"abc","reportedBy":"ci"}""")
        refused("/api/v2/trails/$trailId/artifacts", """{"imageName":"","imageTag":"1.0","sha256Digest":"sha256:${"c".repeat(64)}","reportedBy":"ci"}""")
    }

    @Test
    fun `#250 v2 evidence is processed by the attestation type like v1`() {
        val trailId = trail(flow())
        val attestationId = post("/api/v2/trails/$trailId/attestations", """{"type":"junit","status":"PENDING"}""")["id"].asText()
        val report = org.springframework.mock.web.MockMultipartFile(
            "file", "junit.xml", "application/xml",
            """<testsuite name="unit" tests="3" failures="1" errors="0" skipped="0"/>""".toByteArray()
        )
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/v2/trails/$trailId/attestations/$attestationId/evidence").file(report)
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated)

        val view = get("/api/v2/trails/$trailId/attestations").single()
        assertThat(view["status"].asText()).isEqualTo("FAILED")
        assertThat(view["details"].asText()).contains("\"failures\":1")
        assertThat(view["evidenceFileName"].asText()).isEqualTo("junit.xml")
        assertThat(get("/api/v2/trails/$trailId")["status"].asText()).isEqualTo("NON_COMPLIANT")
    }
}
