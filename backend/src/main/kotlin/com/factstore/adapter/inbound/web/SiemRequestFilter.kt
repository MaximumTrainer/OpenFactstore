package com.factstore.adapter.inbound.web

import com.factstore.application.siem.Ocsf
import com.factstore.application.siem.SiemStream
import com.factstore.config.CorrelationIdFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

/** What an authentication filter concluded about the credential a request presented (#199). */
data class AuthAttempt(val method: String, val success: Boolean, val principal: String?, val detail: String?)

/**
 * Streams every API call to the SIEM as it completes (#199): an OCSF Authentication event when a
 * credential was presented, and an OCSF API Activity event for the call itself, both carrying the
 * request's correlation id.
 *
 * It wraps the security filter chain, so it sees calls refused there (401, 429) as well as those
 * that reach a controller. The authentication filters leave their verdict on the request with
 * [record]; error handlers leave the refusal reason with [detail].
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class SiemRequestFilter(private val siem: SiemStream) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.startsWith("/api/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val started = Instant.now()
        var failed = false
        try {
            filterChain.doFilter(request, response)
        } catch (e: Exception) {
            failed = true
            throw e
        } finally {
            val status = if (failed && response.status < 400) 500 else response.status
            val correlationId = CorrelationIdFilter.current()
            val sourceIp = request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim() ?: request.remoteAddr
            val attempt = request.getAttribute(ATTEMPT) as AuthAttempt?
            attempt?.let {
                siem.emit(Ocsf.authentication(started, it.success, it.principal, it.method, it.detail, sourceIp, correlationId))
            }
            siem.emit(
                Ocsf.apiActivity(
                    time = started,
                    method = request.method,
                    path = request.requestURI,
                    statusCode = status,
                    actor = attempt?.takeIf { it.success }?.principal,
                    authMethod = attempt?.takeIf { it.success }?.method,
                    detail = request.getAttribute(DETAIL) as String?,
                    sourceIp = sourceIp,
                    correlationId = correlationId,
                    durationMs = java.time.Duration.between(started, Instant.now()).toMillis()
                )
            )
        }
    }

    companion object {
        private const val ATTEMPT = "factstore.siem.auth-attempt"
        private const val DETAIL = "factstore.siem.detail"

        /** Called by an authentication filter once it has decided about a presented credential. */
        fun record(request: HttpServletRequest, attempt: AuthAttempt) = request.setAttribute(ATTEMPT, attempt)

        /** Called by an error handler: why the current request was refused. */
        fun detail(reason: String?) {
            val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request ?: return
            if (reason != null) request.setAttribute(DETAIL, reason)
        }
    }
}
