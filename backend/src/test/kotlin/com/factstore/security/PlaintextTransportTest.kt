package com.factstore.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * #208 (#155 FR-10.1) — with enforcement on, a bearer credential must not travel in clear text.
 * A plaintext request from another machine is refused with `426 Upgrade Required` before any
 * credential is read. A redirect would not help: the client has already sent the key.
 *
 * Loopback callers are allowed, health probes are always answered (orchestrators probe over
 * plain HTTP), and a proxy that terminates TLS is honoured through `X-Forwarded-Proto` when
 * `server.forward-headers-strategy=framework`.
 */
class PlaintextTransportTest {

    abstract inner class Base {
        @Autowired lateinit var mockMvc: MockMvc

        @BeforeEach
        fun clear() {
            SecurityContextHolder.clearContext()
            TestSecurityContextHolder.clearContext()
        }

        fun status(path: String, remote: String, secure: Boolean = false, forwardedProto: String? = null): Int =
            mockMvc.get(path) {
                with { it.remoteAddr = remote; it.isSecure = secure; it }
                forwardedProto?.let { header("X-Forwarded-Proto", it) }
            }.andReturn().response.status
    }

    @Nested
    @SpringBootTest(properties = ["security.enforce-auth=true", "server.forward-headers-strategy=framework"])
    @AutoConfigureMockMvc
    inner class Enforced : Base() {
        @Test
        fun `a plaintext request from another machine is refused with 426`() {
            assertThat(status("/api/v1/flows", "10.0.0.5")).isEqualTo(426)
        }

        @Test
        fun `the refusal says to upgrade to TLS`() {
            val response = mockMvc.get("/api/v1/flows") { with { it.remoteAddr = "10.0.0.5"; it } }.andReturn().response
            assertThat(response.getHeader("Upgrade")).contains("TLS")
            assertThat(response.contentAsString).contains("https")
        }

        @Test
        fun `a TLS request from another machine reaches authentication`() {
            assertThat(status("/api/v1/flows", "10.0.0.5", secure = true)).isEqualTo(401)
        }

        @Test
        fun `a request a TLS-terminating proxy forwarded is honoured`() {
            assertThat(status("/api/v1/flows", "10.0.0.5", forwardedProto = "https")).isEqualTo(401)
        }

        @Test
        fun `a loopback plaintext request is allowed`() {
            assertThat(status("/api/v1/flows", "127.0.0.1")).isEqualTo(401)
        }

        @Test
        fun `health probes are answered over plain HTTP`() {
            assertThat(status("/actuator/health", "10.0.0.5")).isEqualTo(200)
        }
    }

    @Nested
    @SpringBootTest(properties = ["security.enforce-auth=true", "security.transport.require-tls=false"])
    @AutoConfigureMockMvc
    inner class TlsNotRequired : Base() {
        @Test
        fun `a deployment can opt out, for example a private network without TLS`() {
            assertThat(status("/api/v1/flows", "10.0.0.5")).isEqualTo(401)
        }
    }

    @Nested
    @SpringBootTest(properties = ["security.enforce-auth=false"])
    @AutoConfigureMockMvc
    inner class NotEnforced : Base() {
        @Test
        fun `without enforcement plaintext is not refused`() {
            assertThat(status("/api/v1/flows", "10.0.0.5")).isEqualTo(200)
        }
    }
}
