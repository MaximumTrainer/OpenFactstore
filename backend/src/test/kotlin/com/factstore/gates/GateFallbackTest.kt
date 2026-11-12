package com.factstore.gates

import com.factstore.application.ArtifactService
import com.factstore.application.AttestationService
import com.factstore.application.FlowService
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.AuditEventType
import com.factstore.core.port.inbound.IAssertService
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.inbound.ITrailService
import com.factstore.dto.AssertRequest
import com.factstore.dto.AssertResponse
import com.factstore.dto.CreateArtifactRequest
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import com.factstore.dto.TrailAssertRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.dao.TransientDataAccessResourceException
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * #193 — risk-based fallback for deployment gates.
 *
 * `POST /api/v1/queries/gates/evaluate` decides whether an artifact may ship to an environment. The
 * environment's stored risk tier decides what happens when the decision cannot be computed because
 * a dependency is down: a `STANDARD` environment fails open and a `TIER1` environment fails closed.
 * Either way the response says so (`fallback_mode`), an audit alert is raised (`audit_alert_id`),
 * and the decision tree records why. The same inputs always give the same decision.
 */
@SpringBootTest(properties = ["security.enforce-auth=true"])
@AutoConfigureMockMvc
@Transactional
@Import(GateFallbackTest.Faults::class)
class GateFallbackTest {

    /** The real gate logic, which the test can make fail as an unreachable database would. */
    class FlakyAssert(private val real: IAssertService) : IAssertService by real {
        @Volatile var down = false
        override fun assertCompliance(request: AssertRequest): AssertResponse {
            if (down) throw TransientDataAccessResourceException("evidence store unreachable")
            return real.assertCompliance(request)
        }
        override fun assertTrail(trailId: UUID, flowId: UUID?, sha256Digest: String?): AssertResponse = real.assertTrail(trailId, flowId, sha256Digest)
    }

    @TestConfiguration
    class Faults {
        @Bean
        @Primary
        fun flakyAssert(real: com.factstore.application.AssertService) = FlakyAssert(real)
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var flows: FlowService
    @Autowired lateinit var trails: ITrailService
    @Autowired lateinit var artifacts: ArtifactService
    @Autowired lateinit var attestations: AttestationService
    @Autowired lateinit var audit: IAuditService
    @Autowired lateinit var gate: FlakyAssert

    private val mapper = jacksonObjectMapper()
    private val deployer = user("deployer").authorities(SimpleGrantedAuthority("SCOPE_assert:execute"))
    private val admin = user("admin@example.com").authorities(SimpleGrantedAuthority("SCOPE_admin"))
    private val digest = "a1".repeat(32)
    private lateinit var flowId: UUID

    @BeforeEach
    fun setUp() {
        gate.down = false
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("admin", null, listOf(SimpleGrantedAuthority("SCOPE_admin")))
        flowId = flows.createFlow(CreateFlowRequest("gate-${System.nanoTime()}", "d", listOf("junit"))).id
        val trail = trails.createTrail(CreateTrailRequest(flowId = flowId, gitCommitSha = "c", gitBranch = "main", gitAuthor = "a", gitAuthorEmail = "a@example.com"))
        artifacts.reportArtifact(trail.id, CreateArtifactRequest(imageName = "svc", imageTag = "1", sha256Digest = "sha256:$digest", reportedBy = "ci"))
        attestations.recordAttestation(trail.id, CreateAttestationRequest(type = "junit", status = AttestationStatus.PASSED))
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun up() {
        gate.down = false
    }

    private fun environment(name: String, tier: String?) {
        mockMvc.post("/api/v1/environments") {
            with(admin)
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(mapOf("name" to name, "type" to "K8S") + (tier?.let { mapOf("tier" to it) } ?: emptyMap()))
        }.andExpect { status { isCreated() } }
    }

    private fun evaluate(environment: String) = mockMvc.post("/api/v1/queries/gates/evaluate") {
        with(deployer)
        contentType = MediaType.APPLICATION_JSON
        content = """{"artifact_sha256":"sha256:$digest","target_environment":"$environment","flow_id":"$flowId"}"""
    }

    private fun env(tier: String?): String = "env-${System.nanoTime()}".also { environment(it, tier) }

    @Test
    fun `#193 a healthy evaluation decides on the evidence, without fallback`() {
        evaluate(env("TIER1")).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("APPROVED") }
            jsonPath("$.fallback_mode") { value("NONE") }
            jsonPath("$.audit_alert_id") { doesNotExist() }
            jsonPath("$.decision_tree.tier") { value("TIER1") }
            jsonPath("$.decision_tree.outcome") { value("EVALUATED") }
            jsonPath("$.missing_attestations") { isEmpty() }
        }
    }

    @Test
    fun `#193 missing evidence is rejected and listed`() {
        val target = env("STANDARD")
        mockMvc.post("/api/v1/queries/gates/evaluate") {
            with(deployer)
            contentType = MediaType.APPLICATION_JSON
            content = """{"artifact_sha256":"sha256:${"b2".repeat(32)}","target_environment":"$target","flow_id":"$flowId"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("REJECTED") }
            jsonPath("$.fallback_mode") { value("NONE") }
            jsonPath("$.missing_attestations[0]") { value("junit") }
        }
    }

    @Test
    fun `#193 a standard environment fails open when the evidence store is down, with an alerted audit`() {
        val target = env("STANDARD")
        gate.down = true

        val body = mapper.readTree(evaluate(target).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("APPROVED") }
            jsonPath("$.fallback_mode") { value("FAIL_OPEN") }
            jsonPath("$.decision_tree.outcome") { value("FALLBACK") }
            jsonPath("$.decision_tree.tier") { value("STANDARD") }
            jsonPath("$.decision_tree.reason") { value(org.hamcrest.Matchers.containsString("unreachable")) }
        }.andReturn().response.contentAsString)

        val alertId = body["audit_alert_id"].asText()
        assertThat(audit.queryEvents(eventType = AuditEventType.GATE_FALLBACK).events)
            .anySatisfy { assertThat(it.id.toString()).isEqualTo(alertId); assertThat(it.payload).contains("FAIL_OPEN").contains(target) }
    }

    @Test
    fun `#193 a tier 1 environment fails closed when the evidence store is down`() {
        val target = env("TIER1")
        gate.down = true

        evaluate(target).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("REJECTED") }
            jsonPath("$.fallback_mode") { value("FAIL_CLOSED") }
            jsonPath("$.audit_alert_id") { exists() }
        }
    }

    @Test
    fun `#193 an environment with no tier is treated as tier 1`() {
        val target = env(null)
        gate.down = true
        evaluate(target).andExpect { jsonPath("$.fallback_mode") { value("FAIL_CLOSED") } }
    }

    @Test
    fun `#193 the fallback is deterministic`() {
        val target = env("STANDARD")
        gate.down = true
        val first = mapper.readTree(evaluate(target).andReturn().response.contentAsString)
        val second = mapper.readTree(evaluate(target).andReturn().response.contentAsString)
        assertThat(second["status"]).isEqualTo(first["status"])
        assertThat(second["fallback_mode"]).isEqualTo(first["fallback_mode"])
        assertThat(second["decision_tree"]).isEqualTo(first["decision_tree"])
    }

    @Test
    fun `#193 the tier cannot be chosen by the caller`() {
        val target = env("TIER1")
        gate.down = true
        mockMvc.post("/api/v1/queries/gates/evaluate") {
            with(deployer)
            contentType = MediaType.APPLICATION_JSON
            content = """{"artifact_sha256":"sha256:$digest","target_environment":"$target","flow_id":"$flowId","tier":"STANDARD"}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `#193 an unknown environment is not found`() {
        evaluate("no-such-env").andExpect { status { isNotFound() } }
    }

    @Test
    fun `#193 evaluating a gate needs assert execute`() {
        mockMvc.post("/api/v1/queries/gates/evaluate") {
            with(user("reader").authorities(SimpleGrantedAuthority("SCOPE_trails:read")))
            contentType = MediaType.APPLICATION_JSON
            content = """{"artifact_sha256":"sha256:$digest","target_environment":"x","flow_id":"$flowId"}"""
        }.andExpect { status { isForbidden() } }
    }
}
