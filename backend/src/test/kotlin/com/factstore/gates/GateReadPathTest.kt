package com.factstore.gates

import com.factstore.application.ArtifactService
import com.factstore.application.AttestationService
import com.factstore.application.FlowService
import com.factstore.application.observability.ProjectionLag
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.port.inbound.IAssertService
import com.factstore.core.port.inbound.ITrailService
import com.factstore.dto.AssertRequest
import com.factstore.dto.AssertResponse
import com.factstore.dto.CreateArtifactRequest
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.CreateFlowRequest
import com.factstore.dto.CreateTrailRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * #194 — the gate read path is separated from the write path, cached, and says how fresh it is.
 *
 * Deployment gates are read far more often than evidence is written, so a decision is cached and
 * served without re-evaluating, until new evidence arrives (any change to the read model) or a
 * TTL passes. Every decision states `data_as_of`. A caller that cannot accept data older than
 * `max_staleness_seconds` gets the risk-tier fallback (#193) instead of a stale answer.
 */
@SpringBootTest(properties = ["security.enforce-auth=true", "factstore.gates.cache-ttl-seconds=30"])
@AutoConfigureMockMvc
@Transactional
@Import(GateReadPathTest.Wiring::class)
class GateReadPathTest {

    class MovableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    /** The real gate logic, counting how often it actually evaluates. */
    class CountingAssert(private val real: IAssertService) : IAssertService by real {
        val evaluations = AtomicInteger()
        override fun assertCompliance(request: AssertRequest): AssertResponse {
            evaluations.incrementAndGet()
            return real.assertCompliance(request)
        }
        override fun assertTrail(trailId: UUID, flowId: UUID?, sha256Digest: String?): AssertResponse =
            real.assertTrail(trailId, flowId, sha256Digest)
    }

    @TestConfiguration
    class Wiring {
        @Bean @Primary fun countingAssert(real: com.factstore.application.AssertService) = CountingAssert(real)
        @Bean @Primary fun movableClock() = MovableClock(Instant.parse("2026-11-12T20:00:00Z"))
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var flows: FlowService
    @Autowired lateinit var trails: ITrailService
    @Autowired lateinit var artifacts: ArtifactService
    @Autowired lateinit var attestations: AttestationService
    @Autowired lateinit var gate: CountingAssert
    @Autowired lateinit var clock: MovableClock
    @Autowired lateinit var lag: ProjectionLag

    private val mapper = jacksonObjectMapper()
    private val deployer = user("deployer").authorities(SimpleGrantedAuthority("SCOPE_assert:execute"))
    private val admin = user("admin@example.com").authorities(SimpleGrantedAuthority("SCOPE_admin"))
    private val digest = "d4".repeat(32)
    private lateinit var flowId: UUID
    private lateinit var trailId: UUID
    private lateinit var environment: String

    private fun asAdmin(block: () -> Unit) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("admin", null, listOf(SimpleGrantedAuthority("SCOPE_admin")))
        try { block() } finally { SecurityContextHolder.clearContext() }
    }

    @BeforeEach
    fun setUp() {
        clock.now = Instant.parse("2026-11-12T20:00:00Z")
        // The read model's position is process-wide; start each test caught up.
        lag.applied(clock.now)
        asAdmin {
            flowId = flows.createFlow(CreateFlowRequest("read-${System.nanoTime()}", "d", listOf("junit"))).id
            trailId = trails.createTrail(CreateTrailRequest(flowId = flowId, gitCommitSha = "c", gitBranch = "main", gitAuthor = "a", gitAuthorEmail = "a@example.com")).id
            artifacts.reportArtifact(trailId, CreateArtifactRequest(imageName = "svc", imageTag = "1", sha256Digest = "sha256:$digest", reportedBy = "ci"))
        }
        environment = "env-${System.nanoTime()}"
        mockMvc.post("/api/v1/environments") {
            with(admin)
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"$environment","type":"K8S","tier":"STANDARD"}"""
        }
        gate.evaluations.set(0)
    }

    private fun evaluate(extra: String = "") = mockMvc.post("/api/v1/queries/gates/evaluate") {
        with(deployer)
        contentType = MediaType.APPLICATION_JSON
        content = """{"artifact_sha256":"sha256:$digest","target_environment":"$environment","flow_id":"$flowId"$extra}"""
    }

    @Test
    fun `#194 a repeated gate query is served from the cache`() {
        evaluate().andExpect { jsonPath("$.status") { value("REJECTED") } }
        evaluate().andExpect {
            jsonPath("$.status") { value("REJECTED") }
            jsonPath("$.data_as_of.cached") { value(true) }
        }
        assertThat(gate.evaluations.get()).isEqualTo(1)
    }

    @Test
    fun `#194 new evidence invalidates the cached decision`() {
        evaluate().andExpect { jsonPath("$.status") { value("REJECTED") } }

        asAdmin { attestations.recordAttestation(trailId, CreateAttestationRequest(type = "junit", status = AttestationStatus.PASSED)) }

        evaluate().andExpect {
            jsonPath("$.status") { value("APPROVED") }
            jsonPath("$.data_as_of.cached") { value(false) }
        }
        assertThat(gate.evaluations.get()).isEqualTo(2)
    }

    @Test
    fun `#194 a cached decision expires after its TTL`() {
        evaluate()
        clock.now = clock.now.plusSeconds(31)
        evaluate().andExpect { jsonPath("$.data_as_of.cached") { value(false) } }
        assertThat(gate.evaluations.get()).isEqualTo(2)
    }

    @Test
    fun `#194 every decision says how fresh its data is`() {
        evaluate().andExpect {
            jsonPath("$.data_as_of.evaluated_at") { value("2026-11-12T20:00:00Z") }
            jsonPath("$.data_as_of.staleness_seconds") { value(0.0) }
            jsonPath("$.data_as_of.cached") { value(false) }
        }
    }

    @Test
    fun `#194 data staler than the caller accepts falls back by risk tier`() {
        // This read model last caught up with the writer two minutes behind.
        lag.applied(clock.now.minusSeconds(120))

        val body = mapper.readTree(evaluate(""","max_staleness_seconds":60""").andExpect {
            status { isOk() }
            jsonPath("$.fallback_mode") { value("FAIL_OPEN") }
            jsonPath("$.decision_tree.outcome") { value("FALLBACK") }
            jsonPath("$.decision_tree.reason") { value(org.hamcrest.Matchers.containsString("stale")) }
            jsonPath("$.data_as_of.staleness_seconds") { value(120.0) }
        }.andReturn().response.contentAsString)
        assertThat(body["audit_alert_id"].asText()).isNotBlank()
        assertThat(gate.evaluations.get()).isZero()
    }

    @Test
    fun `#194 data within the caller's tolerance is used`() {
        lag.applied(clock.now.minusSeconds(5))
        evaluate(""","max_staleness_seconds":60""").andExpect {
            jsonPath("$.fallback_mode") { value("NONE") }
            jsonPath("$.data_as_of.staleness_seconds") { value(5.0) }
        }
    }

    @Test
    fun `#194 fallback decisions are not cached`() {
        lag.applied(clock.now.minusSeconds(120))
        evaluate(""","max_staleness_seconds":60""")
        lag.applied(clock.now)
        evaluate(""","max_staleness_seconds":60""").andExpect { jsonPath("$.fallback_mode") { value("NONE") } }
    }
}
