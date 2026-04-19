package com.factstore.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Rejects mutating HTTP methods (POST, PUT, PATCH, DELETE) when the
 * service is running in query (read-only) mode.
 *
 * Activated by `factstore.cqrs.role=query`.  Without this guard the query
 * container — which runs the same JAR as the command service — would
 * still expose v1/v2 command endpoints and could accept writes against
 * the read database.
 */
@Component
@ConditionalOnProperty(name = ["factstore.cqrs.role"], havingValue = "query")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class QueryServiceReadOnlyFilter : OncePerRequestFilter() {

    private val readOnlyMethods = setOf("GET", "HEAD", "OPTIONS", "TRACE")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.method !in readOnlyMethods) {
            response.sendError(
                HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "Query service is read-only — mutations must be sent to the command service"
            )
            return
        }
        filterChain.doFilter(request, response)
    }
}
