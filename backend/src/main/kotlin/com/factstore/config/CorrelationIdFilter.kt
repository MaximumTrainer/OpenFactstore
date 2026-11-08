package com.factstore.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Gives every request a correlation id (#185).
 *
 * The id is taken from an incoming `X-Request-Id` when it is well formed — so a CI pipeline or an
 * upstream proxy can thread its own id through — and generated otherwise. It is put into the log
 * MDC for the life of the request and returned in the `X-Request-Id` response header, so the id a
 * client quotes from an error body finds the matching log lines.
 *
 * Runs ahead of the security filter chain so authentication failures are correlated too.
 *
 * An incoming id is only accepted from a strict character set: it is written into every log line
 * for the request, and a header carrying a newline would otherwise let a caller forge log entries.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {

    companion object {
        const val HEADER = "X-Request-Id"
        const val MDC_KEY = "correlationId"
        const val MDC_PATH = "path"
        const val MDC_METHOD = "method"
        const val MDC_STATUS = "status"
        private val VALID = Regex("^[A-Za-z0-9._:\\-]{1,128}$")

        /** The correlation id of the request being handled on this thread, if any. */
        fun current(): String? = MDC.get(MDC_KEY)

        fun accept(candidate: String?): String =
            candidate?.takeIf { VALID.matches(it) } ?: UUID.randomUUID().toString()
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val id = accept(request.getHeader(HEADER))
        MDC.put(MDC_KEY, id)
        MDC.put(MDC_PATH, request.requestURI)
        MDC.put(MDC_METHOD, request.method)
        response.setHeader(HEADER, id)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
            MDC.remove(MDC_PATH)
            MDC.remove(MDC_METHOD)
            MDC.remove(MDC_STATUS)
        }
    }
}
