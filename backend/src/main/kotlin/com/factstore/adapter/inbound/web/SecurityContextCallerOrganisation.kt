package com.factstore.adapter.inbound.web

import com.factstore.application.auth.AuthenticatedUser
import com.factstore.core.domain.security.Permission
import com.factstore.core.port.outbound.ICallerOrganisation
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder

/**
 * Reads the caller's organisation from the authenticated principal: a signed-in user's active
 * organisation, or the organisation an API key was bound to at creation.
 */
@Component
class SecurityContextCallerOrganisation : ICallerOrganisation {

    companion object {
        /** Request attribute holding the organisations an unbound caller read in this request. */
        const val CROSS_TENANT_ORGS = "factstore.crossTenantOrgs"
    }

    private fun authentication(): Authentication? =
        SecurityContextHolder.getContext().authentication
            ?.takeIf { it.isAuthenticated && it !is AnonymousAuthenticationToken }

    override fun orgSlug(): String? {
        val auth = authentication() ?: return null
        (auth.principal as? AuthenticatedUser)?.let { return it.orgSlug }
        return (auth.details as? ApiKeyPrincipalDetails)?.orgSlug
    }

    override fun isAuthenticated(): Boolean = authentication() != null

    override fun isOperator(): Boolean =
        authentication()?.authorities?.any {
            it.authority == Permission.OPERATOR.authority || it.authority == Permission.ADMIN.authority
        } ?: false

    override fun recordCrossTenantRead(orgSlug: String) {
        val attributes = RequestContextHolder.getRequestAttributes() ?: return
        @Suppress("UNCHECKED_CAST")
        val orgs = attributes.getAttribute(CROSS_TENANT_ORGS, RequestAttributes.SCOPE_REQUEST) as? MutableSet<String>
            ?: linkedSetOf<String>().also { attributes.setAttribute(CROSS_TENANT_ORGS, it, RequestAttributes.SCOPE_REQUEST) }
        orgs += orgSlug
    }
}
