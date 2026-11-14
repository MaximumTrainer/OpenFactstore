package com.factstore.retention

import com.factstore.core.domain.AuditEventType
import com.factstore.core.port.inbound.IAuditService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** #251: retention policies per organisation and data class, and legal holds. */
@SpringBootTest(properties = ["security.enforce-auth=true"])
@AutoConfigureMockMvc
@Transactional
class RetentionPolicyTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var audit: IAuditService

    private val admin = user("alice@example.com").authorities(SimpleGrantedAuthority("SCOPE_admin"))
    private val reader = user("reader").authorities(SimpleGrantedAuthority("SCOPE_flows:read"))

    private fun put(tenant: String, dataClass: String, days: Int) =
        mockMvc.put("/api/v1/admin/retention/policies/$tenant/$dataClass") {
            with(admin)
            contentType = MediaType.APPLICATION_JSON
            content = """{"retainDays":$days}"""
        }

    @Test
    fun `the data classes state their action and minimum period`() {
        mockMvc.get("/api/v1/admin/retention/data-classes") { with(admin) }.andExpect {
            status { isOk() }
            jsonPath("$[?(@.dataClass == 'FACT_CONTENT')].action") { value("SHRED") }
            jsonPath("$[?(@.dataClass == 'FACT_CONTENT')].minimumDays") { value(365) }
            jsonPath("$[?(@.dataClass == 'ENVIRONMENT_SNAPSHOTS')].action") { value("ARCHIVE") }
            jsonPath("$[?(@.dataClass == 'ENVIRONMENT_SNAPSHOTS')].minimumDays") { value(90) }
            jsonPath("$[?(@.dataClass == 'NOTIFICATIONS')].action") { value("PRUNE") }
            jsonPath("$[?(@.dataClass == 'NOTIFICATIONS')].minimumDays") { value(30) }
        }
    }

    @Test
    fun `a policy can be set, read back, changed and removed, and every change is audited`() {
        put("default", "ENVIRONMENT_SNAPSHOTS", 400).andExpect {
            status { isOk() }
            jsonPath("$.tenant") { value("default") }
            jsonPath("$.dataClass") { value("ENVIRONMENT_SNAPSHOTS") }
            jsonPath("$.retainDays") { value(400) }
            jsonPath("$.action") { value("ARCHIVE") }
            jsonPath("$.updatedBy") { value("alice@example.com") }
        }
        put("default", "ENVIRONMENT_SNAPSHOTS", 500).andExpect { jsonPath("$.retainDays") { value(500) } }

        mockMvc.get("/api/v1/admin/retention/policies") { with(admin) }.andExpect {
            jsonPath("$[?(@.dataClass == 'ENVIRONMENT_SNAPSHOTS' && @.tenant == 'default')].retainDays") { value(500) }
        }

        mockMvc.delete("/api/v1/admin/retention/policies/default/ENVIRONMENT_SNAPSHOTS") { with(admin) }
            .andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/admin/retention/policies") { with(admin) }.andExpect {
            jsonPath("$[?(@.dataClass == 'ENVIRONMENT_SNAPSHOTS' && @.tenant == 'default')]") { isEmpty() }
        }

        assertThat(audit.queryEvents(eventType = AuditEventType.RETENTION_POLICY_SET, size = 100).events).hasSizeGreaterThanOrEqualTo(2)
        assertThat(audit.queryEvents(eventType = AuditEventType.RETENTION_POLICY_REMOVED, size = 100).events).isNotEmpty()
    }

    @Test
    fun `a period shorter than the data class minimum is refused`() {
        put("default", "FACT_CONTENT", 364).andExpect { status { isBadRequest() } }
        put("default", "NOTIFICATIONS", 29).andExpect { status { isBadRequest() } }
        put("default", "NOTIFICATIONS", 30).andExpect { status { isOk() } }
    }

    @Test
    fun `an unknown data class is refused and an unknown organisation is not found`() {
        put("default", "EVERYTHING", 400).andExpect { status { isBadRequest() } }
        put("no-such-org-${System.nanoTime()}", "NOTIFICATIONS", 400).andExpect { status { isNotFound() } }
    }

    @Test
    fun `removing a policy that does not exist is not found`() {
        mockMvc.delete("/api/v1/admin/retention/policies/default/FACT_CONTENT") { with(admin) }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `retention is for administrators only`() {
        mockMvc.get("/api/v1/admin/retention/policies") { with(reader) }.andExpect { status { isForbidden() } }
        mockMvc.put("/api/v1/admin/retention/policies/default/NOTIFICATIONS") {
            with(reader)
            contentType = MediaType.APPLICATION_JSON
            content = """{"retainDays":60}"""
        }.andExpect { status { isForbidden() } }
        mockMvc.post("/api/v1/admin/retention/runs?dryRun=true") { with(reader) }.andExpect { status { isForbidden() } }
        mockMvc.post("/api/v1/legal-holds") {
            with(reader)
            contentType = MediaType.APPLICATION_JSON
            content = """{"scopeType":"TRAIL","scopeId":"${UUID.randomUUID()}","reason":"x"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `a legal hold is placed, listed and released, never deleted`() {
        val scopeId = UUID.randomUUID()
        val body = mockMvc.post("/api/v1/legal-holds") {
            with(admin)
            contentType = MediaType.APPLICATION_JSON
            content = """{"scopeType":"FLOW","scopeId":"$scopeId","reason":"Litigation 2026-17"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.active") { value(true) }
            jsonPath("$.placedBy") { value("alice@example.com") }
        }.andReturn().response.contentAsString
        val id = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(body)!!.groupValues[1]

        mockMvc.get("/api/v1/legal-holds") { with(admin) }.andExpect {
            jsonPath("$[?(@.id == '$id')].reason") { value("Litigation 2026-17") }
        }
        mockMvc.post("/api/v1/legal-holds/$id/release") { with(admin) }.andExpect {
            status { isOk() }
            jsonPath("$.active") { value(false) }
            jsonPath("$.releasedBy") { value("alice@example.com") }
        }
        mockMvc.get("/api/v1/legal-holds") { with(admin) }.andExpect { jsonPath("$[?(@.id == '$id')]") { isEmpty() } }
        mockMvc.get("/api/v1/legal-holds?includeReleased=true") { with(admin) }.andExpect {
            jsonPath("$[?(@.id == '$id')]") { isNotEmpty() }
        }
        assertThat(audit.queryEvents(eventType = AuditEventType.LEGAL_HOLD_PLACED, size = 100).events).isNotEmpty()
        assertThat(audit.queryEvents(eventType = AuditEventType.LEGAL_HOLD_RELEASED, size = 100).events).isNotEmpty()
    }
}
