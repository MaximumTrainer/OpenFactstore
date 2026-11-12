package com.factstore.gates

import com.factstore.core.domain.AuditEventType
import com.factstore.core.port.inbound.IAuditService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * #193 — when Factstore could not be reached at all, the CLI decided the gate itself by the
 * environment's tier. Once Factstore is reachable again it reports each such decision, so the
 * fallback is audited and alerted on exactly like a server-side one.
 */
@SpringBootTest(properties = ["security.enforce-auth=true"])
@AutoConfigureMockMvc
@Transactional
class ClientFallbackReportTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var audit: IAuditService
    @Autowired lateinit var meters: MeterRegistry

    private val mapper = jacksonObjectMapper()
    private val deployer = user("deployer").authorities(SimpleGrantedAuthority("SCOPE_assert:execute"))

    private fun report(reportId: UUID = UUID.randomUUID(), mode: String = "FAIL_OPEN") = """
        {"report_id":"$reportId","artifact_sha256":"sha256:${"c3".repeat(32)}","target_environment":"staging",
         "flow_id":"${UUID.randomUUID()}","tier":"STANDARD","fallback_mode":"$mode",
         "reason":"connection refused","decided_at":"2026-11-12T16:00:00Z"}
    """.trimIndent()

    private fun post(body: String) = mockMvc.post("/api/v1/commands/gates/fallback-reports") {
        with(deployer)
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun clientFallbacks() = meters.find("factstore.gate.fallbacks").tag("source", "client").counters().sumOf { it.count() }

    @Test
    fun `#193 a client-side fallback is audited and counted`() {
        val before = clientFallbacks()

        val alert = mapper.readTree(post(report()).andExpect { status { isCreated() } }.andReturn().response.contentAsString)["audit_alert_id"].asText()

        assertThat(audit.queryEvents(eventType = AuditEventType.GATE_FALLBACK).events).anySatisfy {
            assertThat(it.id.toString()).isEqualTo(alert)
            assertThat(it.payload).contains("\"source\":\"client\"").contains("FAIL_OPEN").contains("connection refused")
        }
        assertThat(clientFallbacks()).isEqualTo(before + 1)
    }

    @Test
    fun `#193 a report sent twice is recorded once`() {
        val id = UUID.randomUUID()
        val first = mapper.readTree(post(report(id)).andReturn().response.contentAsString)["audit_alert_id"].asText()
        val second = post(report(id)).andExpect { status { isOk() } }.andReturn().response.contentAsString

        assertThat(mapper.readTree(second)["audit_alert_id"].asText()).isEqualTo(first)
        assertThat(audit.queryEvents(eventType = AuditEventType.GATE_FALLBACK).events.count { it.payload.contains(id.toString()) }).isEqualTo(1)
    }

    @Test
    fun `#193 a report must say which way the gate fell back`() {
        post(report(mode = "WHATEVER")).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `#193 reporting needs assert execute`() {
        mockMvc.post("/api/v1/commands/gates/fallback-reports") {
            with(user("reader").authorities(SimpleGrantedAuthority("SCOPE_trails:read")))
            contentType = MediaType.APPLICATION_JSON
            content = report()
        }.andExpect { status { isForbidden() } }
    }
}
