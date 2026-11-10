package com.factstore.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Refuses plaintext HTTP from other machines when authentication is enforced (#208, #155 FR-10.1).
 *
 * A bearer key sent over plain HTTP can be read by anyone on the path, so the request is refused
 * with `426 Upgrade Required` before any credential is looked at. A redirect would not help: the
 * client has already sent the key once, and would send it again.
 *
 * - Loopback callers are allowed: nothing crosses a network.
 * - Health probes (`/actuator/health/` and below) are always answered. Orchestrators probe over
 *   plain HTTP, and they carry no credential.
 * - Behind a proxy that terminates TLS, set `server.forward-headers-strategy=framework` so the
 *   proxy's `X-Forwarded-Proto: https` marks the request secure.
 * - `security.transport.require-tls=false` turns this off, for a private network that carries
 *   plain HTTP between services (such as the local Docker Compose stack).
 *
 * Runs just after Spring's forwarded-header handling and before authentication.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class TransportSecurityFilter(
    private val objectMapper: ObjectMapper,
    @Value("\${security.enforce-auth:false}") private val enforceAuth: Boolean,
    @Value("\${security.transport.require-tls:true}") private val requireTls: Boolean
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(TransportSecurityFilter::class.java)

    companion object {
        private val LOOPBACK = setOf("127.0.0.1", "::1", "0:0:0:0:0:0:0:1")
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !enforceAuth || !requireTls ||
            request.isSecure ||
            request.remoteAddr in LOOPBACK ||
            request.requestURI == "/actuator/health" || request.requestURI.startsWith("/actuator/health/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        log.warn("Refused plaintext {} {} from {}: TLS is required", request.method, request.requestURI, request.remoteAddr)
        response.status = 426
        response.setHeader("Upgrade", "TLS/1.2, HTTP/1.1")
        response.setHeader(HttpHeaders.CONNECTION, "Upgrade")
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            objectMapper.writeValueAsString(
                mapOf(
                    "status" to 426,
                    "error" to "Upgrade Required",
                    "message" to "This server requires https. Resend the request over TLS, and treat any " +
                        "credential already sent over plain HTTP as exposed."
                )
            )
        )
    }
}
