package com.factstore.adapter.inbound.web

import com.factstore.application.ActorResolver
import com.factstore.core.domain.AuditEventType
import com.factstore.core.port.inbound.IAuditService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Writes one `CROSS_TENANT_READ` audit event for a request in which an unbound credential read
 * other organisations' resources (#207, #155 FR-5.4), naming the organisations and the route.
 *
 * Ordered inside Spring Security's filter chain, so the caller is still known when the request
 * completes. One event per request, not per row, keeps a list call from flooding the log.
 */
@Component
@Order(0)
class CrossTenantReadAuditFilter(
    private val auditService: IAuditService,
    private val actorResolver: ActorResolver
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(CrossTenantReadAuditFilter::class.java)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        try {
            filterChain.doFilter(request, response)
        } finally {
            @Suppress("UNCHECKED_CAST")
            val orgs = request.getAttribute(SecurityContextCallerOrganisation.CROSS_TENANT_ORGS) as? Set<String>
            if (!orgs.isNullOrEmpty()) {
                try {
                    auditService.record(
                        eventType = AuditEventType.CROSS_TENANT_READ,
                        actor = actorResolver.current(),
                        payload = mapOf(
                            "organisations" to orgs.sorted(),
                            "method" to request.method,
                            "path" to request.requestURI,
                            "status" to response.status
                        )
                    )
                } catch (e: Exception) {
                    log.error("Could not audit a cross-organisation read of {} on {}", orgs, request.requestURI, e)
                }
            }
        }
    }
}
