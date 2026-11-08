package com.factstore.security

import com.factstore.config.DevSignInGuard
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.env.MockEnvironment
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/**
 * #156 FR-8.4 — a documented dev mode that needs no identity provider.
 *
 * Local development must stay low-friction once enforcement is on, so a developer can sign in as
 * any email and role without setting up GitHub or an IdP. It is a loaded gun in production, so it
 * is off unless switched on, refuses to start under the `prod` profile, and by default only
 * answers requests from the machine it runs on.
 */
class DevSignInTest {

    private fun cookieFrom(setCookie: List<String>): Cookie =
        setCookie.first { it.startsWith("fs_session=") }.substringAfter("fs_session=").substringBefore(";")
            .let { Cookie("fs_session", it) }

    @Nested
    @SpringBootTest(properties = ["security.dev-login.enabled=true"])
    @AutoConfigureMockMvc
    @Transactional
    inner class WhenEnabled {
        @Autowired lateinit var mockMvc: MockMvc

        @BeforeEach
        fun clear() {
            SecurityContextHolder.clearContext()
            TestSecurityContextHolder.clearContext()
        }

        @Test
        fun `the login page can tell dev sign-in is available`() {
            mockMvc.get("/api/v1/auth/dev-login").andExpect {
                status { isOk() }
                jsonPath("$.enabled") { value(true) }
            }
        }

        @Test
        fun `a developer signs in as any email and role and gets a working session`() {
            val result = mockMvc.post("/api/v1/auth/dev-login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"dev-${System.nanoTime()}@localhost","role":"VIEWER","orgSlug":"dev-org"}"""
            }.andExpect { status { isOk() } }.andReturn()

            val cookie = cookieFrom(result.response.getHeaders("Set-Cookie"))
            mockMvc.get("/api/v1/auth/me") { cookie(cookie) }.andExpect {
                status { isOk() }
                jsonPath("$.role") { value("VIEWER") }
                jsonPath("$.orgSlug") { value("dev-org") }
            }
        }

        @Test
        fun `the role defaults to ADMIN so a fresh checkout can do everything`() {
            val result = mockMvc.post("/api/v1/auth/dev-login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"dev-admin-${System.nanoTime()}@localhost"}"""
            }.andReturn()

            mockMvc.get("/api/v1/auth/me") { cookie(cookieFrom(result.response.getHeaders("Set-Cookie"))) }.andExpect {
                jsonPath("$.role") { value("ADMIN") }
                jsonPath("$.orgSlug") { value("dev") }
            }
        }

        @Test
        fun `a request from another machine is refused`() {
            mockMvc.post("/api/v1/auth/dev-login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"remote@localhost"}"""
                with { it.remoteAddr = "10.1.2.3"; it }
            }.andExpect {
                status { isForbidden() }
                header { doesNotExist("Set-Cookie") }
            }
        }
    }

    @Nested
    @SpringBootTest(properties = ["security.dev-login.enabled=true", "security.enforce-auth=true"])
    @AutoConfigureMockMvc
    @Transactional
    inner class WhenEnforcementIsOn {
        @Autowired lateinit var mockMvc: MockMvc

        @Test
        fun `dev sign-in works with enforcement on, which is the point of it`() {
            SecurityContextHolder.clearContext()
            TestSecurityContextHolder.clearContext()
            // Refused without a credential. (401-vs-403 for that case belongs to #155.)
            mockMvc.get("/api/v1/flows").andExpect { status { is4xxClientError() } }

            val result = mockMvc.post("/api/v1/auth/dev-login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"enforced-${System.nanoTime()}@localhost"}"""
            }.andExpect { status { isOk() } }.andReturn()

            mockMvc.get("/api/v1/flows") { cookie(cookieFrom(result.response.getHeaders("Set-Cookie"))) }
                .andExpect { status { isOk() } }
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @Transactional
    inner class WhenDisabled {
        @Autowired lateinit var mockMvc: MockMvc

        @Test
        fun `dev sign-in does not exist unless switched on`() {
            mockMvc.post("/api/v1/auth/dev-login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"nobody@localhost"}"""
            }.andExpect {
                status { isNotFound() }
                header { doesNotExist("Set-Cookie") }
            }
            mockMvc.get("/api/v1/auth/dev-login").andExpect { status { isNotFound() } }
        }
    }

    @Test
    fun `the application refuses to start with dev sign-in on under the prod profile`() {
        val env = MockEnvironment().withProperty("security.dev-login.enabled", "true")
        env.setActiveProfiles("prod")

        assertThatThrownBy { DevSignInGuard(env).check() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("security.dev-login.enabled")
    }

    @Test
    fun `dev sign-in on outside prod starts`() {
        val env = MockEnvironment().withProperty("security.dev-login.enabled", "true")
        env.setActiveProfiles("local")

        DevSignInGuard(env).check()
        assertThat(env.activeProfiles).contains("local")
    }
}
