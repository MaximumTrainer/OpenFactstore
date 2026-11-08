package com.factstore.adapter.inbound.web

import com.factstore.application.auth.AuthenticatedUser
import com.factstore.application.auth.SessionService
import com.factstore.core.domain.SessionRevocationReason
import com.factstore.core.domain.UserSession
import com.factstore.core.domain.security.Permission
import com.factstore.core.domain.security.RoleModel
import com.factstore.core.port.inbound.IApiKeyService
import com.factstore.core.port.outbound.IOrganisationMembershipRepository
import com.factstore.dto.AuthConfigResponse
import com.factstore.dto.AuthenticatedPrincipalResponse
import com.factstore.dto.PrincipalOrganisation
import com.factstore.dto.PrincipalType
import com.factstore.dto.RefreshedSessionResponse
import com.factstore.dto.SessionResponse
import com.factstore.dto.SwitchOrganisationRequest
import com.factstore.exception.BadRequestException
import com.factstore.exception.NotFoundException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * Identity and session endpoints (#156 FR-3, FR-4).
 *
 * `/auth/me` is the endpoint the UI drives every navigation and control decision from, and the
 * one `factstore login` uses to confirm a credential works. It answers for a user session and
 * for an API key, so there is one place to ask "who am I and what may I do".
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Sign-in identity, sessions and sign-out")
class AuthController(
    private val sessionService: SessionService,
    private val apiKeyService: IApiKeyService,
    private val membershipRepository: IOrganisationMembershipRepository,
    @Value("\${security.enforce-auth:false}") private val enforceAuth: Boolean
) {

    /**
     * Whether this instance enforces authentication.
     *
     * Public, and deliberately so: a client has to be able to ask before it has a credential,
     * and the UI must gate exactly when the server gates — `security.enforce-auth` defaults to
     * `false` during the #155 rollout, so a UI that assumed enforcement bounced every route to
     * `/login` on an instance that was not asking anyone to sign in.
     *
     * It discloses only enforcement. That is no more than an unauthenticated request already
     * reveals by being accepted or refused, and deliberately not the issuer, client id or
     * bootstrap state.
     */
    @PublicEndpoint("a client must learn whether sign-in is required before it has a credential")
    @GetMapping("/config")
    @Operation(
        summary = "Whether this instance enforces authentication",
        description = "Answered without a credential, because a client must be able to ask " +
            "before it has one. Discloses nothing else about the deployment."
    )
    fun config(): ResponseEntity<AuthConfigResponse> =
        ResponseEntity.ok(AuthConfigResponse(enforceAuth = enforceAuth))

    @PreAuthorize("@authz.authenticated()")
    @GetMapping("/me")
    @Operation(
        summary = "The authenticated principal, its organisation, role and permissions",
        description = "Answers for both a user session and an API key. Returns 401 when " +
            "unauthenticated — never an anonymous placeholder."
    )
    fun me(authentication: Authentication?): ResponseEntity<AuthenticatedPrincipalResponse> {
        val principal = authentication?.principal
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE_BEARER)
                .build()

        return when (principal) {
            is AuthenticatedUser -> ResponseEntity.ok(userPrincipal(principal))
            else -> apiKeyPrincipal(authentication)
                ?: ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE_BEARER)
                    .build()
        }
    }

    @PublicEndpoint("must clear the cookie even for an expired or revoked session")
    @PostMapping("/logout")
    @Operation(
        summary = "End the session",
        description = "Revokes the session server-side, so the token stops working immediately " +
            "rather than at its expiry, and clears the session cookie."
    )
    fun logout(
        authentication: Authentication?,
        response: HttpServletResponse
    ): ResponseEntity<Void> {
        (authentication?.principal as? AuthenticatedUser)?.let { user ->
            sessionService.revoke(user.sessionId, SessionRevocationReason.LOGOUT)
        }
        // Clear the cookie whether or not a session was found, so a stale cookie cannot linger.
        response.addHeader(HttpHeaders.SET_COOKIE, clearedSessionCookie())
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("@authz.authenticated()")
    @PostMapping("/refresh")
    @Operation(
        summary = "Extend the session without returning to the identity provider",
        description = "Bounded by the session's absolute lifetime, so a session cannot be " +
            "renewed indefinitely."
    )
    fun refresh(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<RefreshedSessionResponse> {
        val token = presentedToken(request)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE_BEARER).build()

        val refreshed = sessionService.refresh(token)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE_BEARER).build()

        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(refreshed.token, request))
        return ResponseEntity.ok(RefreshedSessionResponse(expiresAt = refreshed.expiresAt))
    }

    @PreAuthorize("@authz.authenticated()")
    @GetMapping("/sessions")
    @Operation(summary = "The signed-in user's own active sessions")
    fun mySessions(authentication: Authentication?): ResponseEntity<List<SessionResponse>> {
        val user = authentication?.principal as? AuthenticatedUser
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(
            sessionService.activeSessions(user.userId).map { it.toResponse(user.sessionId) }
        )
    }

    @PreAuthorize("@authz.authenticated()")
    @DeleteMapping("/sessions/{sessionId}")
    @Operation(
        summary = "Revoke one of the signed-in user's own sessions",
        description = "Signing out another device. Revoking someone else's session requires ADMIN."
    )
    fun revokeMySession(
        @PathVariable sessionId: String,
        authentication: Authentication?
    ): ResponseEntity<Void> {
        val user = authentication?.principal as? AuthenticatedUser
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val owned = sessionService.activeSessions(user.userId).any { it.jti == sessionId }
        if (!owned) throw NotFoundException("Session not found: $sessionId")
        sessionService.revoke(sessionId, SessionRevocationReason.LOGOUT)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/users/{userId}/sessions")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(summary = "List a user's active sessions (administrator)")
    fun userSessions(@PathVariable userId: UUID): ResponseEntity<List<SessionResponse>> =
        ResponseEntity.ok(sessionService.activeSessions(userId).map { it.toResponse(null) })

    @DeleteMapping("/users/{userId}/sessions")
    @PreAuthorize("hasAuthority('SCOPE_admin')")
    @Operation(
        summary = "Revoke every active session for a user (administrator)",
        description = "The lever to pull when an account is compromised or someone leaves."
    )
    fun revokeUserSessions(@PathVariable userId: UUID): ResponseEntity<Map<String, Int>> {
        val revoked = sessionService.revokeAllForUser(userId, SessionRevocationReason.ADMIN_REVOKED)
        return ResponseEntity.ok(mapOf("revoked" to revoked))
    }

    @PreAuthorize("@authz.authenticated()")
    @PostMapping("/organisation")
    @Operation(
        summary = "Switch the organisation this session acts in",
        description = "Every subsequent request is scoped to it. Refused unless the user is a " +
            "member of the target organisation."
    )
    fun switchOrganisation(
        @RequestBody request: SwitchOrganisationRequest,
        authentication: Authentication?
    ): ResponseEntity<AuthenticatedPrincipalResponse> {
        val user = authentication?.principal as? AuthenticatedUser
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (request.orgSlug.isBlank()) throw BadRequestException("orgSlug is required")

        val updated = sessionService.switchOrganisation(user.sessionId, request.orgSlug)
            ?: throw NotFoundException("You are not a member of organisation '${request.orgSlug}'")
        return ResponseEntity.ok(userPrincipal(updated))
    }

    // -----------------------------------------------------------------------

    private fun userPrincipal(user: AuthenticatedUser) = AuthenticatedPrincipalResponse(
        type = PrincipalType.USER,
        userId = user.userId,
        email = user.email,
        name = user.name,
        orgSlug = user.orgSlug,
        role = user.role,
        permissions = RoleModel.permissionsFor(user.role).map { it.scope }.sorted(),
        sessionId = user.sessionId,
        sessionExpiresAt = user.sessionExpiresAt,
        organisations = membershipRepository.findByUserId(user.userId)
            .map { PrincipalOrganisation(orgSlug = it.orgSlug, role = it.role) }
            .sortedBy { it.orgSlug }
    )

    /**
     * An API key answered. The principal is the key's owner id, put on the context by
     * [ApiKeyAuthFilter]; the scopes come from the authorities it was granted.
     */
    private fun apiKeyPrincipal(authentication: Authentication): ResponseEntity<AuthenticatedPrincipalResponse>? {
        val ownerId = runCatching { UUID.fromString(authentication.name) }.getOrNull() ?: return null
        val scopes = authentication.authorities
            .map { it.authority }
            .filter { it.startsWith(Permission.AUTHORITY_PREFIX) }
            .map { it.removePrefix(Permission.AUTHORITY_PREFIX) }
            .sorted()
        return ResponseEntity.ok(
            AuthenticatedPrincipalResponse(
                type = PrincipalType.API_KEY,
                ownerId = ownerId,
                permissions = scopes
            )
        )
    }

    private fun presentedToken(request: HttpServletRequest): String? {
        request.cookies?.firstOrNull { it.name == SessionAuthFilter.SESSION_COOKIE }
            ?.value?.takeIf { it.isNotBlank() }?.let { return it }
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.removePrefix("Bearer ").trim().takeIf { it.isNotBlank() }
    }

    private fun UserSession.toResponse(currentSessionId: String?) = SessionResponse(
        sessionId = jti,
        provider = provider,
        orgSlug = orgSlug,
        createdAt = createdAt,
        lastSeenAt = lastSeenAt,
        expiresAt = expiresAt,
        absoluteExpiresAt = absoluteExpiresAt,
        sourceIp = sourceIp,
        userAgent = userAgent,
        current = jti == currentSessionId
    )

    companion object {
        const val WWW_AUTHENTICATE_BEARER = "Bearer realm=\"factstore\""

        /**
         * `HttpOnly` so the token is unreachable from JavaScript, `SameSite=Strict` so it is
         * not sent on a cross-site request at all. `Secure` is omitted for a plain-HTTP
         * localhost request only, because a browser will not store a `Secure` cookie there.
         */
        fun sessionCookie(token: String, request: HttpServletRequest): String {
            val secure = request.isSecure || !isLocalhost(request)
            return buildString {
                append("${SessionAuthFilter.SESSION_COOKIE}=$token")
                append("; Path=/; HttpOnly; SameSite=Strict")
                if (secure) append("; Secure")
            }
        }

        fun clearedSessionCookie(): String =
            "${SessionAuthFilter.SESSION_COOKIE}=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0"

        private fun isLocalhost(request: HttpServletRequest): Boolean =
            request.serverName in setOf("localhost", "127.0.0.1", "::1", "[::1]")
    }
}
