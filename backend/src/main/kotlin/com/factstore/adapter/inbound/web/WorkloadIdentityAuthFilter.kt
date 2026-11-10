package com.factstore.adapter.inbound.web

import com.factstore.application.auth.WorkloadTokenVerifier
import com.factstore.application.observability.FailureMetrics
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.AuthFailureReason
import com.factstore.core.domain.SpiffeId
import com.factstore.core.domain.security.Permission
import com.factstore.core.port.inbound.IAuditService
import com.factstore.dto.AuthProblemResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jwt.SignedJWT
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
 * Authenticates a CI/CD workload by a short-lived identity instead of a static key (#189).
 *
 * The workload presents `Authorization: Bearer <JWT>`: a GitHub Actions or GitLab CI OIDC token,
 * or a SPIFFE JWT-SVID from a SPIRE issuer added with `factstore.oidc.extra-issuers`. Only a
 * token whose issuer is trusted is handled here; anything else is left for the other filters.
 * The token is accepted when:
 *
 * - [WorkloadTokenVerifier] verifies its signature against the issuer's keys, its audience, and
 *   its expiry;
 * - its `sub` matches one of `factstore.oidc.allowed-subjects` (comma-separated, `*` as a
 *   wildcard, for example `repo:my-org/service:` followed by `*`). The default is
 *   empty, so no workload is trusted until an operator says which;
 * - an `X-Spiffe-ID` header, if sent, names exactly that verified subject. The header is never an
 *   identity on its own.
 *
 * An accepted workload carries only `factstore.oidc.workload-scopes` (default
 * `attestations:write`), plus `ROLE_WORKLOAD` so its actions are attributed as
 * `workload:<sub>`. A refused one gets `401`, is counted, and is audited.
 */
@Component
class WorkloadIdentityAuthFilter(
    private val verifier: WorkloadTokenVerifier,
    private val auditService: IAuditService,
    private val objectMapper: ObjectMapper,
    private val failureMetrics: FailureMetrics,
    @Value("\${factstore.oidc.allowed-subjects:}") allowedSubjects: String,
    @Value("\${factstore.oidc.workload-scopes:attestations:write}") workloadScopes: String,
    @Value("\${security.warn-mode:false}") private val warnMode: Boolean
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(WorkloadIdentityAuthFilter::class.java)

    private val subjectPatterns: List<Regex> = allowedSubjects.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        .map { pattern -> Regex(pattern.split("*").joinToString(".*") { Regex.escape(it) }) }

    private val authorities: List<SimpleGrantedAuthority> =
        (Permission.parse(workloadScopes.split(",")).permissions.map { it.authority } + ROLE_WORKLOAD)
            .map { SimpleGrantedAuthority(it) }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val claimedSpiffeId = request.getHeader(SPIFFE_HEADER)?.trim()?.takeIf { it.isNotEmpty() }
        val token = workloadToken(request)

        if (token == null) {
            if (claimedSpiffeId != null) {
                return refuse(request, response, filterChain, "$SPIFFE_HEADER is only accepted with a verified workload token")
            }
            filterChain.doFilter(request, response)
            return
        }

        val subject = try {
            verifier.verify(token)["sub"] as? String
        } catch (e: Exception) {
            return refuse(request, response, filterChain, "The workload token could not be verified")
        } ?: return refuse(request, response, filterChain, "The workload token has no subject")

        if (subjectPatterns.none { it.matches(subject) }) {
            return refuse(request, response, filterChain, "The workload is not trusted by this Factstore")
        }
        if (claimedSpiffeId != null) {
            val valid = runCatching { SpiffeId(claimedSpiffeId) }.isSuccess
            if (!valid || claimedSpiffeId != subject) {
                return refuse(request, response, filterChain, "$SPIFFE_HEADER does not name the verified workload")
            }
        }

        val auth = UsernamePasswordAuthenticationToken(subject, null, authorities)
        auth.details = WorkloadPrincipalDetails(subject = subject, spiffeId = subject.takeIf { it.startsWith(SpiffeId.SCHEME) })
        SecurityContextHolder.getContext().authentication = auth
        filterChain.doFilter(request, response)
    }

    /**
     * A Bearer JWT from a trusted workload issuer, when nobody has authenticated yet. Session
     * tokens and API keys are not workload tokens and are left alone.
     */
    private fun workloadToken(request: HttpServletRequest): String? {
        if (SecurityContextHolder.getContext().authentication != null) return null
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith(BEARER_PREFIX)) return null
        val token = header.removePrefix(BEARER_PREFIX).trim()
        val issuer = runCatching { SignedJWT.parse(token).jwtClaimsSet.issuer }.getOrNull() ?: return null
        return token.takeIf { issuer in verifier.trustedIssuers }
    }

    private fun refuse(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain, message: String) {
        failureMetrics.authFailure(REASON)
        runCatching {
            auditService.record(
                eventType = AuditEventType.AUTH_FAILED,
                actor = "unauthenticated",
                payload = mapOf("reason" to REASON, "detail" to message, "method" to request.method, "path" to request.requestURI)
            )
        }.onFailure { log.debug("Could not record workload auth failure: ${it.message}") }

        if (warnMode) {
            failureMetrics.warnModeRefusal("workload")
            log.warn("WARN MODE: would have refused workload identity on {} {}: {}", request.method, request.requestURI, message)
            chain.doFilter(request, response)
            return
        }
        log.warn("Refused workload identity on {} {}: {}", request.method, request.requestURI, message)
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, ApiKeyAuthFilter.WWW_AUTHENTICATE)
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            objectMapper.writeValueAsString(AuthProblemResponse("Unauthorized", AuthFailureReason.UNKNOWN, message))
        )
    }

    companion object {
        const val SPIFFE_HEADER = "X-Spiffe-ID"
        const val ROLE_WORKLOAD = "ROLE_WORKLOAD"
        private const val BEARER_PREFIX = "Bearer "
        private const val REASON = "WORKLOAD_IDENTITY"
    }
}

/** The verified workload behind a request (#189). */
data class WorkloadPrincipalDetails(val subject: String, val spiffeId: String?)
