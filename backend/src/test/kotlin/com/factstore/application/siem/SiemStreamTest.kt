package com.factstore.application.siem

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.factstore.application.ApiKeyService
import com.factstore.core.domain.OwnerType
import com.factstore.core.domain.User
import com.factstore.core.domain.security.Permission
import com.factstore.core.port.outbound.IUserRepository
import com.factstore.dto.CreateApiKeyRequest
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * #199 — every authentication attempt, policy rejection and read or write reaches the SIEM
 * stream, in real time and in OCSF.
 *
 * The stream is the `factstore.audit` logger: one OCSF event per line, written as the request
 * completes, which a log collector (Fluent Bit, Vector, the Datadog Agent) forwards to Splunk HEC
 * or Datadog Logs. The test reads that logger directly, exactly what a collector would see.
 */
@SpringBootTest(properties = ["security.enforce-auth=true", "security.dev-login.enabled=true"])
@AutoConfigureMockMvc
@Transactional
class SiemStreamTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var apiKeys: ApiKeyService
    @Autowired lateinit var users: IUserRepository

    private val mapper = jacksonObjectMapper()
    private val stream = ListAppender<ILoggingEvent>()
    private val auditLogger get() = LoggerFactory.getLogger("factstore.audit") as Logger

    @BeforeEach
    fun attach() {
        SecurityContextHolder.clearContext()
        TestSecurityContextHolder.clearContext()
        stream.start()
        auditLogger.addAppender(stream)
    }

    @AfterEach
    fun detach() {
        auditLogger.detachAppender(stream)
    }

    private fun events(): List<JsonNode> = stream.list.map { mapper.readTree(it.formattedMessage) }

    private fun key(vararg scopes: String): String {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("admin", null, listOf(SimpleGrantedAuthority(Permission.ADMIN.authority)))
        try {
            val owner = users.save(User(email = "siem-${System.nanoTime()}@example.com", name = "SIEM"))
            return apiKeys.createApiKey(
                CreateApiKeyRequest(ownerId = owner.id, label = "siem", ownerType = OwnerType.USER, scopes = scopes.toList())
            ).plainTextKey
        } finally {
            SecurityContextHolder.clearContext()
            TestSecurityContextHolder.clearContext()
            stream.list.clear()
        }
    }

    private fun JsonNode.isClass(uid: Int) = this["class_uid"].asInt() == uid

    @Test
    fun `#199 a successful authentication and the read it makes are both streamed`() {
        val key = key("flows:read")

        mockMvc.get("/api/v1/flows") { header("Authorization", "Bearer $key") }.andExpect { status { isOk() } }

        val auth = events().single { it.isClass(3002) }
        assertThat(auth["status_id"].asInt()).isEqualTo(1)
        assertThat(auth["auth_protocol"].asText()).isEqualTo("api_key")
        assertThat(auth["user"]["name"].asText()).startsWith("api-key:")
        val read = events().single { it.isClass(6003) }
        assertThat(read["activity_id"].asInt()).isEqualTo(2)
        assertThat(read["status_id"].asInt()).isEqualTo(1)
        assertThat(read["http_request"]["url"]["path"].asText()).isEqualTo("/api/v1/flows")
        assertThat(read["actor"]["user"]["name"].asText()).isEqualTo(auth["user"]["name"].asText())
        assertThat(read["metadata"]["correlation_uid"].asText()).isEqualTo(auth["metadata"]["correlation_uid"].asText())
    }

    @Test
    fun `#199 a failed authentication is streamed with its reason`() {
        mockMvc.get("/api/v1/flows") { header("Authorization", "Bearer fsp_${"0".repeat(64)}") }
            .andExpect { status { isUnauthorized() } }

        val auth = events().single { it.isClass(3002) }
        assertThat(auth["status_id"].asInt()).isEqualTo(2)
        assertThat(auth["status_detail"].asText()).isNotBlank()
        assertThat(events().single { it.isClass(6003) }["http_response"]["code"].asInt()).isEqualTo(401)
    }

    @Test
    fun `#199 a policy rejection is streamed as a failed API activity`() {
        val key = key("flows:read")

        mockMvc.put("/api/v1/commands/schemas/siem-denied/v1") {
            header("Authorization", "Bearer $key")
            contentType = MediaType.APPLICATION_JSON
            content = """{"type":"object"}"""
        }.andExpect { status { isForbidden() } }

        val write = events().single { it.isClass(6003) }
        assertThat(write["activity_id"].asInt()).isEqualTo(3)
        assertThat(write["status_id"].asInt()).isEqualTo(2)
        assertThat(write["http_response"]["code"].asInt()).isEqualTo(403)
        assertThat(write["status_detail"].asText()).isNotBlank()
    }

    @Test
    fun `#199 an anonymous call to a protected endpoint is streamed`() {
        mockMvc.get("/api/v1/flows").andExpect { status { isUnauthorized() } }
        val call = events().single { it.isClass(6003) }
        assertThat(call["status_id"].asInt()).isEqualTo(2)
        assertThat(call["actor"]["user"]["name"].asText()).isEqualTo("anonymous")
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `#199 a successful write is streamed, with the audit record it produced`() {
        val key = key("admin")
        val name = "siem-${System.nanoTime()}"

        mockMvc.put("/api/v1/commands/schemas/$name/v1") {
            header("Authorization", "Bearer $key")
            contentType = MediaType.APPLICATION_JSON
            content = """{"type":"object"}"""
        }.andExpect { status { isCreated() } }

        val write = events().single { it.isClass(6003) && it["http_request"] != null }
        assertThat(write["activity_id"].asInt()).isEqualTo(3)
        assertThat(write["status_id"].asInt()).isEqualTo(1)
        val audit = events().single { it["unmapped"]?.get("factstore_event_type")?.asText() == "SCHEMA_PUBLISHED" }
        assertThat(audit["activity_id"].asInt()).isEqualTo(1)
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `#199 an interactive sign-in is streamed as an authentication`() {
        mockMvc.post("/api/v1/auth/dev-login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"siem-${System.nanoTime()}@localhost"}"""
        }.andExpect { status { isOk() } }

        assertThat(events()).anySatisfy {
            assertThat(it["class_uid"].asInt()).isEqualTo(3002)
            assertThat(it["status_id"].asInt()).isEqualTo(1)
            assertThat(it["unmapped"]["factstore_event_type"].asText()).isEqualTo("USER_SIGNED_IN")
        }
    }

    @Test
    fun `#199 calls outside the API are not streamed`() {
        mockMvc.get("/actuator/health")
        assertThat(events()).isEmpty()
    }
}
