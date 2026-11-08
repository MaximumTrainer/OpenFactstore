package com.factstore.adapter.inbound.web

import com.factstore.application.auth.SessionService
import com.factstore.core.domain.AuthProvider
import com.factstore.core.domain.MemberRole
import com.factstore.core.domain.OrganisationMembership
import com.factstore.core.port.inbound.IUserService
import com.factstore.core.port.outbound.IOrganisationMembershipRepository
import com.factstore.exception.ConflictException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Completes a GitHub OAuth sign-in (#156 FR-1.2).
 *
 * Spring Security has already exchanged the code and fetched GitHub's `/user`. This maps that
 * principal onto a Factstore `User` (by GitHub id, then by email, as
 * [IUserService.findOrCreateByGithub] does), gives a first-time user a `VIEWER` membership in the
 * configured default organisation (FR-5.3: never privileged by default), and issues the same
 * session the OIDC path issues — an `HttpOnly` cookie, with the role read from the membership
 * table on every request.
 *
 * GitHub does not return an email for accounts that keep it private, so those accounts are keyed
 * by GitHub's own no-reply address, `<id>+<login>@users.noreply.github.com`.
 */
@Component
class GitHubSignInSuccessHandler(
    private val userService: IUserService,
    private val memberships: IOrganisationMembershipRepository,
    private val sessionService: SessionService,
    @Value("\${security.github.default-org:}") private val defaultOrg: String
) : AuthenticationSuccessHandler {

    private val log = LoggerFactory.getLogger(GitHubSignInSuccessHandler::class.java)

    @Transactional
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val token = authentication as? OAuth2AuthenticationToken
        val attributes = token?.principal?.attributes
        val githubId = attributes?.get("id")?.toString()
        val login = attributes?.get("login") as? String
        if (githubId == null || login == null) {
            log.warn("GitHub sign-in returned no id/login; refusing")
            response.sendRedirect(FAILED)
            return
        }
        val email = (attributes["email"] as? String)?.takeIf { it.isNotBlank() }
            ?: "$githubId+$login@users.noreply.github.com"
        val name = (attributes["name"] as? String)?.takeIf { it.isNotBlank() } ?: login

        val user = try {
            userService.findOrCreateByGithub(githubId, email, name)
        } catch (e: ConflictException) {
            // The email belongs to a user already linked to a *different* GitHub account. Signing
            // this one in would hand it someone else's identity.
            log.warn("GitHub sign-in refused for githubId={}: {}", githubId, e.message)
            response.sendRedirect(FAILED)
            return
        }

        val orgSlug = defaultOrg.takeIf { it.isNotBlank() }?.also { org ->
            if (memberships.findByOrgSlugAndUserId(org, user.id) == null) {
                memberships.save(OrganisationMembership(orgSlug = org, userId = user.id, role = MemberRole.VIEWER))
            }
        } ?: memberships.findByUserId(user.id).firstOrNull()?.orgSlug

        val issued = sessionService.issue(
            userId = user.id,
            orgSlug = orgSlug,
            provider = AuthProvider.GITHUB,
            sourceIp = request.remoteAddr,
            userAgent = request.getHeader(HttpHeaders.USER_AGENT)
        )
        response.addHeader(HttpHeaders.SET_COOKIE, AuthController.sessionCookie(issued.token, request))
        response.sendRedirect("/")
    }

    companion object {
        const val FAILED = "/login?reason=failed"
    }
}
