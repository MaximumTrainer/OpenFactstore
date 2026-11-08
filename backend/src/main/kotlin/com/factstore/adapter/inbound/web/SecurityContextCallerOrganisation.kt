package com.factstore.adapter.inbound.web

import com.factstore.application.auth.AuthenticatedUser
import com.factstore.core.port.outbound.ICallerOrganisation
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Reads the caller's organisation from the authenticated principal: a signed-in user's active
 * organisation, or the organisation an API key was bound to at creation.
 */
@Component
class SecurityContextCallerOrganisation : ICallerOrganisation {
    override fun orgSlug(): String? {
        val auth = SecurityContextHolder.getContext().authentication ?: return null
        (auth.principal as? AuthenticatedUser)?.let { return it.orgSlug }
        return (auth.details as? ApiKeyPrincipalDetails)?.orgSlug
    }
}
