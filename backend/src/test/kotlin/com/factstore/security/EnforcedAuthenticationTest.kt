package com.factstore.security

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/**
 * #155 acceptance criterion 1, FR-2.4 — with enforcement on, an unauthenticated request to a
 * protected endpoint is a 401 carrying `WWW-Authenticate: Bearer`, so a client can tell "sign in"
 * from "you may not". It used to be a bare 403.
 *
 * The command and query services run the same JAR, so this covers both.
 */
@SpringBootTest(properties = ["security.enforce-auth=true"])
@AutoConfigureMockMvc
@Transactional
class EnforcedAuthenticationTest {

    @Autowired lateinit var mockMvc: MockMvc

    @BeforeEach
    fun clear() {
        SecurityContextHolder.clearContext()
        TestSecurityContextHolder.clearContext()
    }

    @Test
    fun `an unauthenticated request to a protected endpoint is 401 with a Bearer challenge`() {
        mockMvc.get("/api/v1/flows").andExpect {
            status { isUnauthorized() }
            header { string("WWW-Authenticate", "Bearer realm=\"factstore\"") }
            jsonPath("$.reason") { value("MISSING") }
        }
    }

    @Test
    fun `an unauthenticated mutation is 401 too, not 403`() {
        mockMvc.post("/api/v1/flows") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"x","description":"y"}"""
        }.andExpect {
            status { isUnauthorized() }
            header { string("WWW-Authenticate", "Bearer realm=\"factstore\"") }
        }
    }

    @Test
    fun `actuator metrics, env and loggers need a credential, health and info do not`() {
        listOf("/actuator/metrics", "/actuator/env", "/actuator/loggers").forEach { path ->
            mockMvc.get(path).andExpect { status { isUnauthorized() } }
        }
        mockMvc.get("/actuator/health").andExpect { status { isOk() } }
    }

    @Test
    fun `signed inbound hooks reach their own signature check without an API credential`() {
        // GitHub and Slack cannot present a Factstore credential; they sign the payload instead.
        val webhook = mockMvc.post("/api/v1/webhooks/github") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andReturn()
        val slack = mockMvc.post("/api/v1/organisations/acme/slack/commands") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            content = "command=%2Ffactstore"
        }.andReturn()

        // Refused, if at all, by the handler's own signature check, not by the credential gate.
        org.assertj.core.api.Assertions.assertThat(webhook.response.getHeader("WWW-Authenticate")).isNull()
        org.assertj.core.api.Assertions.assertThat(slack.response.getHeader("WWW-Authenticate")).isNull()
        org.assertj.core.api.Assertions.assertThat(listOf(webhook.response.status, slack.response.status)).doesNotContain(403)
    }
}
