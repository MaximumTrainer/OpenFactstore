package com.factstore.adapter.inbound.web

import com.factstore.application.observability.FailureMetrics
import com.factstore.application.ApiKeyRateLimiter
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.AuthFailureReason
import com.factstore.core.domain.security.Permission
import com.factstore.core.domain.security.RoleModel
import com.factstore.core.port.inbound.IApiKeyService
import com.factstore.core.port.inbound.IAuditService
import com.factstore.dto.ApiKeyResponse
import com.factstore.dto.AuthProblemResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authenticates an API key and grants the authorities its **scopes** allow (#155 FR-2, FR-3).
 *
 * Two behaviours changed here:
 *
 *  - **A bad credential is rejected immediately**, with `401`,
 *    `WWW-Authenticate: Bearer realm="factstore"` and a problem body distinguishing missing,
 *    malformed, expired and revoked — without revealing whether a given key exists. It used to
 *    fall through and let a route decide, so a wrong key looked exactly like no key, and with
 *    enforcement off it looked like success.
 *  - **Authorities come from the key's scopes**, not the fixed `ROLE_API_USER` every valid key
 *    used to receive.
 *
 * Schemes, in order: `X-API-Key`, `Authorization: Bearer`, and the deprecated
 * `Authorization: ApiKey` (still accepted, logged by prefix only).
 */
@Component
class ApiKeyAuthFilter(
    private val apiKeyService: IApiKeyService,
    private val rateLimiter: ApiKeyRateLimiter,
    private val auditService: IAuditService,
    private val objectMapper: ObjectMapper,
    private val failureMetrics: FailureMetrics,
    @Value("\${security.enforce-auth:false}") private val enforceAuth: Boolean,
    @Value("\${security.warn-mode:false}") private val warnMode: Boolean
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(ApiKeyAuthFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Another filter already authenticated this request (a signed-in person), or this path
        // does not need a credential.
        if (SecurityContextHolder.getContext().authentication != null || isPublic(request)) {
            filterChain.doFilter(request, response)
            return
        }

        val presented = extractKey(request)
        if (presented == null) {
            filterChain.doFilter(request, response)
            return
        }

        val source = clientIp(request)
        val prefix = presented.value.take(PREFIX_LENGTH)

        if (rateLimiter.isBlocked(source, prefix)) {
            tooManyRequests(request, response, source, prefix)
            return
        }

        val validation = apiKeyService.validateWithReason(presented.value)
        val key = validation.response

        if (key != null) {
            rateLimiter.recordSuccess(source, prefix)
            if (presented.deprecatedScheme) {
                log.warn(
                    "Deprecated 'Authorization: ApiKey' scheme used by key prefix $prefix; " +
                        "use 'Authorization: Bearer' instead"
                )
            }
            authenticate(key)
            SiemRequestFilter.record(request, AuthAttempt("api_key", true, "api-key:${key.ownerId}", null))
            warnIfExpiringSoon(response, key)
            filterChain.doFilter(request, response)
            return
        }

        // The credential was presented and is not good. Refuse it here rather than letting a
        // route decide, so an invalid key never looks like an anonymous request.
        val reason = validation.reason ?: AuthFailureReason.UNKNOWN
        rateLimiter.recordFailure(source, prefix)
        auditFailure(request, source, prefix, reason)
        SiemRequestFilter.record(request, AuthAttempt("api_key", false, "key-prefix:$prefix", reason.name))

        if (warnMode) {
            failureMetrics.warnModeRefusal("credential")
            log.warn(
                "WARN MODE: would have rejected ${request.method} ${request.requestURI} " +
                    "from $source (key prefix $prefix, reason $reason)"
            )
            filterChain.doFilter(request, response)
            return
        }

        unauthorized(response, reason, prefix)
    }

    private fun authenticate(key: ApiKeyResponse) {
        val permissions = Permission.parse(key.scopes).permissions
        val authorities = RoleModel.authoritiesForScopes(permissions) +
            // Kept so pre-existing `hasRole('API_USER')` expressions and clients continue to
            // work while scopes roll out.
            setOf("ROLE_API_USER")

        val auth = UsernamePasswordAuthenticationToken(
            key.ownerId.toString(),
            null,
            authorities.map { SimpleGrantedAuthority(it) }
        )
        auth.details = ApiKeyPrincipalDetails(
            apiKeyId = key.id,
            ownerId = key.ownerId,
            orgSlug = key.orgSlug,
            scopes = key.scopes
        )
        SecurityContextHolder.getContext().authentication = auth
    }

    /** A pipeline should learn its key is about to expire before it breaks (#155 FR-6.3). */
    private fun warnIfExpiringSoon(response: HttpServletResponse, key: ApiKeyResponse) {
        if (key.expiringSoon && key.daysUntilExpiry != null) {
            response.setHeader(
                EXPIRY_WARNING_HEADER,
                "API key ${key.keyPrefix} expires in ${key.daysUntilExpiry} day(s); rotate it"
            )
        }
    }

    private fun unauthorized(
        response: HttpServletResponse,
        reason: AuthFailureReason,
        prefix: String
    ) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE)
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            objectMapper.writeValueAsString(
                AuthProblemResponse(
                    error = "Unauthorized",
                    reason = reason,
                    message = messageFor(reason),
                    credentialPrefix = prefix
                )
            )
        )
    }

    private fun tooManyRequests(
        request: HttpServletRequest,
        response: HttpServletResponse,
        source: String,
        prefix: String
    ) {
        val retryAfter = rateLimiter.retryAfterSeconds(source, prefix)
        log.warn("Rate limited authentication from $source (key prefix $prefix)")
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.setHeader(HttpHeaders.RETRY_AFTER, retryAfter.toString())
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, WWW_AUTHENTICATE)
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            objectMapper.writeValueAsString(
                AuthProblemResponse(
                    error = "Too Many Requests",
                    reason = AuthFailureReason.UNKNOWN,
                    message = "Too many failed authentication attempts. Retry after $retryAfter seconds.",
                    credentialPrefix = prefix
                )
            )
        )
    }

    /**
     * Messages deliberately do not distinguish "no such key" from "wrong key", so a caller
     * cannot use the response to discover whether a key exists.
     */
    private fun messageFor(reason: AuthFailureReason): String = when (reason) {
        AuthFailureReason.MISSING -> "No credential was presented"
        AuthFailureReason.MALFORMED -> "The credential is not a well-formed API key"
        AuthFailureReason.EXPIRED -> "The credential has expired"
        AuthFailureReason.REVOKED -> "The credential has been revoked"
        else -> "The credential was not accepted"
    }

    private fun auditFailure(
        request: HttpServletRequest,
        source: String,
        prefix: String,
        reason: AuthFailureReason
    ) {
        // Counted by the bounded reason only, never by prefix or source: those are unbounded (#185).
        failureMetrics.authFailure(reason.name)
        runCatching {
            auditService.record(
                eventType = AuditEventType.AUTH_FAILED,
                actor = "unauthenticated",
                // Prefix only. An audit log that contains working credentials is a liability,
                // not a control (#155 FR-8.3).
                payload = mapOf(
                    "reason" to reason.name,
                    "credentialPrefix" to prefix,
                    "sourceIp" to source,
                    "method" to request.method,
                    "path" to request.requestURI
                )
            )
        }.onFailure { log.debug("Could not record auth failure: ${it.message}") }
    }

    private fun isPublic(request: HttpServletRequest): Boolean =
        PUBLIC_PREFIXES.any { request.requestURI.startsWith(it) }

    private fun clientIp(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.remoteAddr
            ?: "unknown"

    private data class PresentedKey(val value: String, val deprecatedScheme: Boolean)

    private fun extractKey(request: HttpServletRequest): PresentedKey? {
        request.getHeader(API_KEY_HEADER)?.takeIf { it.isNotBlank() }
            ?.let { return PresentedKey(it.trim(), deprecatedScheme = false) }

        val authHeader = request.getHeader(HttpHeaders.AUTHORIZATION)?.takeIf { it.isNotBlank() }
            ?: return null

        if (authHeader.startsWith(BEARER_PREFIX)) {
            val token = authHeader.removePrefix(BEARER_PREFIX).trim()
            // A session token is not an API key; SessionAuthFilter has already had its turn.
            if (!token.startsWith("fsp_") && !token.startsWith("fss_")) return null
            return PresentedKey(token, deprecatedScheme = false)
        }
        if (authHeader.startsWith(API_KEY_SCHEME)) {
            return PresentedKey(authHeader.removePrefix(API_KEY_SCHEME).trim(), deprecatedScheme = true)
        }
        return null
    }

    companion object {
        const val API_KEY_HEADER = "X-API-Key"
        const val WWW_AUTHENTICATE = "Bearer realm=\"factstore\""
        const val EXPIRY_WARNING_HEADER = "X-Factstore-Credential-Warning"
        private const val BEARER_PREFIX = "Bearer "
        private const val API_KEY_SCHEME = "ApiKey "
        private const val PREFIX_LENGTH = 12

        /** Paths that never need a credential, so a bad one there is not worth refusing over. */
        private val PUBLIC_PREFIXES = listOf(
            "/actuator/health",
            "/actuator/info",
            "/api/v1/auth/logout",
            "/login",
            "/oauth2"
        )
    }
}

/** Details of the authenticating key, so downstream code can scope by organisation. */
data class ApiKeyPrincipalDetails(
    val apiKeyId: java.util.UUID,
    val ownerId: java.util.UUID,
    val orgSlug: String?,
    val scopes: List<String>
)
