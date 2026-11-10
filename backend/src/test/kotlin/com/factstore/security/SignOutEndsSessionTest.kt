package com.factstore.security

import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/**
 * #209 / #156 FR-3.3 — signing out ends the session, whatever else the browser still holds.
 *
 * Found by the enforced end-to-end suite: after logout revoked the session and cleared the
 * `fs_session` cookie, `/auth/me` still answered 200, because the signed-in principal had also
 * been saved into the servlet `HttpSession` and came back with the `JSESSIONID` cookie. A revoked
 * session must not survive in a second store.
 */
@SpringBootTest(properties = ["security.enforce-auth=true", "security.dev-login.enabled=true"])
@AutoConfigureMockMvc
@Transactional
class SignOutEndsSessionTest {

    @Autowired lateinit var mockMvc: MockMvc

    @BeforeEach
    fun clear() {
        SecurityContextHolder.clearContext()
        TestSecurityContextHolder.clearContext()
    }

    private fun sessionCookie(setCookies: List<String>): Cookie =
        setCookies.first { it.startsWith("fs_session=") }.substringAfter("fs_session=").substringBefore(";")
            .let { Cookie("fs_session", it) }

    @Test
    fun `after logout neither the cookie nor the servlet session authenticates`() {
        val httpSession = MockHttpSession()
        val login = mockMvc.post("/api/v1/auth/dev-login") {
            session = httpSession
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"signout-${System.nanoTime()}@localhost","role":"VIEWER"}"""
        }.andReturn()
        val cookie = sessionCookie(login.response.getHeaders("Set-Cookie"))

        mockMvc.get("/api/v1/auth/me") { session = httpSession; cookie(cookie) }.andExpect { status { isOk() } }

        mockMvc.post("/api/v1/auth/logout") {
            session = httpSession
            cookie(cookie)
            header("X-Factstore-Client", "web")
        }

        // The browser has dropped fs_session; only the servlet session (JSESSIONID) remains.
        val after = mockMvc.get("/api/v1/auth/me") { session = httpSession }.andReturn()
        assertThat(after.response.status).isEqualTo(401)
    }

    @Test
    fun `the signed-in principal is not stored in the servlet session at all`() {
        val httpSession = MockHttpSession()
        val login = mockMvc.post("/api/v1/auth/dev-login") {
            session = httpSession
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"stateless-${System.nanoTime()}@localhost"}"""
        }.andReturn()
        mockMvc.get("/api/v1/auth/me") { session = httpSession; cookie(sessionCookie(login.response.getHeaders("Set-Cookie"))) }

        assertThat(httpSession.getAttribute("SPRING_SECURITY_CONTEXT")).isNull()
    }
}
