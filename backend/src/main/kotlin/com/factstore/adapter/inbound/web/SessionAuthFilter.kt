package com.factstore.adapter.inbound.web

import com.factstore.application.auth.AuthenticatedUser
import com.factstore.application.auth.SessionService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authenticates a person's session (#156 FR-3, FR-6.2), replacing the hand-rolled
 * `SsoJwtAuthFilter` that granted a flat `ROLE_SSO_USER` to any token whose HMAC matched.
 *
 * The credential is read from, in order:
 *  1. the `fs_session` cookie — the web UI's path, `HttpOnly` so it is unreachable from
 *     JavaScript and therefore not stealable by XSS;
 *  2. `Authorization: Bearer <token>` — for API clients that hold a session token.
 *
 * A cookie is an *ambient* credential, so it is only honoured for a mutating request that also
 * carries [CLIENT_HEADER]. A cross-site form post cannot set a custom header, and CORS is
 * deny-by-default, so this closes CSRF without a token exchange. Bearer credentials are not
 * ambient and need no such check.
 */
@Component
class SessionAuthFilter(private val sessionService: SessionService) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(SessionAuthFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (SecurityContextHolder.getContext().authentication == null) {
            val presented = extractCredential(request)
            if (presented != null) {
                if (presented.fromCookie && isMutating(request) && !hasClientHeader(request)) {
                    log.debug("Ignoring cookie credential on a mutating request without $CLIENT_HEADER")
                } else {
                    val user = sessionService.resolve(presented.token)
                    if (user != null) {
                        authenticate(user)
                        SiemRequestFilter.record(request, AuthAttempt("session", true, user.email, null))
                    } else if (!presented.fromCookie) {
                        SiemRequestFilter.record(request, AuthAttempt("session", false, null, "INVALID_SESSION"))
                    }
                }
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun authenticate(user: AuthenticatedUser) {
        val auth = UsernamePasswordAuthenticationToken(
            user,
            null,
            user.authorities.map { SimpleGrantedAuthority(it) }
        )
        SecurityContextHolder.getContext().authentication = auth
    }

    private fun extractCredential(request: HttpServletRequest): PresentedCredential? {
        request.cookies?.firstOrNull { it.name == SESSION_COOKIE }?.value?.takeIf { it.isNotBlank() }
            ?.let { return PresentedCredential(it, fromCookie = true) }

        val header = request.getHeader("Authorization") ?: return null
        if (!header.startsWith(BEARER_PREFIX)) return null
        val token = header.removePrefix(BEARER_PREFIX).trim()
        // An API key is not a session token; leave it to ApiKeyAuthFilter.
        if (token.isBlank() || token.startsWith("fsp_") || token.startsWith("fss_")) return null
        return PresentedCredential(token, fromCookie = false)
    }

    private fun isMutating(request: HttpServletRequest): Boolean =
        request.method.uppercase() !in SAFE_METHODS

    private fun hasClientHeader(request: HttpServletRequest): Boolean =
        !request.getHeader(CLIENT_HEADER).isNullOrBlank()

    private data class PresentedCredential(val token: String, val fromCookie: Boolean)

    companion object {
        const val SESSION_COOKIE = "fs_session"
        /** The web client marks its own requests, which a cross-site form post cannot do. */
        const val CLIENT_HEADER = "X-Factstore-Client"
        private const val BEARER_PREFIX = "Bearer "
        private val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS", "TRACE")
    }
}
