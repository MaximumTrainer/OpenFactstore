package com.factstore.security

import com.factstore.adapter.inbound.web.GitHubSignInSuccessHandler
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.MemberRole
import com.factstore.core.domain.OrganisationMembership
import com.factstore.core.domain.User
import com.factstore.core.port.outbound.IAuditEventRepository
import com.factstore.core.port.outbound.IOrganisationMembershipRepository
import com.factstore.core.port.outbound.IUserRepository
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

/**
 * #156 FR-1.2 — signing in with GitHub creates or matches a `User` and gives them a session,
 * the same way the organisation OIDC path does.
 *
 * GitHub itself is not called: the test hands the success handler the principal that Spring
 * Security builds from GitHub's `/user` response, then proves the result through `/auth/me`.
 */
@SpringBootTest(properties = ["security.github.default-org=gh-acme"])
@AutoConfigureMockMvc
@Transactional
class GitHubSignInTest {

    @Autowired lateinit var handler: GitHubSignInSuccessHandler
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var users: IUserRepository
    @Autowired lateinit var memberships: IOrganisationMembershipRepository
    @Autowired lateinit var auditEvents: IAuditEventRepository

    @BeforeEach
    fun clearSecurity() {
        SecurityContextHolder.clearContext()
        TestSecurityContextHolder.clearContext()
    }

    private fun gitHubUser(id: Long, login: String, email: String?, name: String? = "Octo Cat"): OAuth2AuthenticationToken {
        val attributes = buildMap<String, Any> {
            put("id", id)
            put("login", login)
            email?.let { put("email", it) }
            name?.let { put("name", it) }
        }
        val authorities = listOf(SimpleGrantedAuthority("OAUTH2_USER"))
        return OAuth2AuthenticationToken(DefaultOAuth2User(authorities, attributes, "id"), authorities, "github")
    }

    private fun signIn(token: OAuth2AuthenticationToken): MockHttpServletResponse {
        val request = MockHttpServletRequest("GET", "/login/oauth2/code/github").apply { serverName = "localhost" }
        val response = MockHttpServletResponse()
        handler.onAuthenticationSuccess(request, response, token)
        return response
    }

    private fun sessionCookie(response: MockHttpServletResponse): Cookie? =
        response.getHeaders("Set-Cookie").firstOrNull { it.startsWith("fs_session=") }
            ?.substringAfter("fs_session=")?.substringBefore(";")
            ?.let { Cookie("fs_session", it) }

    @Test
    fun `a first GitHub sign-in creates a user linked to the GitHub account and signs them in`() {
        val id = System.nanoTime()
        val response = signIn(gitHubUser(id, "octo-$id", "octo-$id@example.com"))

        assertThat(response.redirectedUrl).isEqualTo("/")
        val cookie = sessionCookie(response)
        assertThat(cookie).isNotNull
        assertThat(users.findByGithubId(id.toString())?.email).isEqualTo("octo-$id@example.com")

        mockMvc.get("/api/v1/auth/me") { cookie(cookie!!) }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value("octo-$id@example.com") }
            jsonPath("$.orgSlug") { value("gh-acme") }
        }
    }

    @Test
    fun `a first GitHub sign-in with no membership is a VIEWER, not a privileged user`() {
        val id = System.nanoTime()
        signIn(gitHubUser(id, "viewer-$id", "viewer-$id@example.com"))

        val user = users.findByGithubId(id.toString())!!
        assertThat(memberships.findByOrgSlugAndUserId("gh-acme", user.id)?.role).isEqualTo(MemberRole.VIEWER)
    }

    @Test
    fun `an existing member keeps the role an administrator gave them`() {
        val id = System.nanoTime()
        val existing = users.save(User(email = "admin-$id@example.com", name = "Admin"))
        memberships.save(OrganisationMembership(orgSlug = "gh-acme", userId = existing.id, role = MemberRole.ADMIN))

        val response = signIn(gitHubUser(id, "admin-$id", "admin-$id@example.com"))

        assertThat(users.findByGithubId(id.toString())?.id).isEqualTo(existing.id)
        assertThat(memberships.findByOrgSlugAndUserId("gh-acme", existing.id)?.role).isEqualTo(MemberRole.ADMIN)
        mockMvc.get("/api/v1/auth/me") { cookie(sessionCookie(response)!!) }.andExpect {
            jsonPath("$.role") { value("ADMIN") }
        }
    }

    @Test
    fun `a GitHub account with a private email is matched by its no-reply address`() {
        val id = System.nanoTime()
        signIn(gitHubUser(id, "private-$id", email = null))

        assertThat(users.findByGithubId(id.toString())?.email).isEqualTo("$id+private-$id@users.noreply.github.com")
    }

    @Test
    fun `an email already linked to a different GitHub account is refused without a session`() {
        val id = System.nanoTime()
        users.save(User(email = "taken-$id@example.com", name = "Taken", githubId = "someone-else-$id"))

        val response = signIn(gitHubUser(id, "intruder-$id", "taken-$id@example.com"))

        assertThat(response.redirectedUrl).isEqualTo("/login?reason=failed")
        assertThat(sessionCookie(response)).isNull()
    }

    @Test
    fun `a GitHub sign-in is audited with its provider`() {
        val id = System.nanoTime()
        signIn(gitHubUser(id, "audited-$id", "audited-$id@example.com"))

        val signIns = auditEvents.findWithFilters(eventType = AuditEventType.USER_SIGNED_IN, actor = "audited-$id@example.com")
            .filter { it.payload.contains("\"provider\":\"GITHUB\"") }
        assertThat(signIns).hasSize(1)
    }
}
