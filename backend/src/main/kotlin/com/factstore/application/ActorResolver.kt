package com.factstore.application

import com.factstore.application.auth.AuthenticatedUser
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Resolves the actor to attribute an audit event to (#156 FR-7).
 *
 * "Who did this" is a question a compliance product has to answer, so an audit event naming
 * `system` for something a person did is a defect, not a detail. This reads whatever principal
 * the authentication filters put on the security context:
 *
 *  - a signed-in person → their email;
 *  - an API key → `api-key:<owner id>`, so the key's owner is on the record too;
 *  - a CI workload identity (#189) → `workload:<token subject>`;
 *  - nothing → `system`, which is then genuinely true: an unauthenticated or internal action.
 */
@Component
class ActorResolver {

    fun current(): String = resolve()?.actor ?: SYSTEM

    /** The signed-in person, when a person is acting. Null for a key or an internal action. */
    fun currentUser(): AuthenticatedUser? = resolve()?.user

    /** The organisation the caller is acting in, for scoping. */
    fun currentOrgSlug(): String? = resolve()?.user?.orgSlug

    private fun resolve(): ResolvedActor? {
        val authentication = SecurityContextHolder.getContext().authentication ?: return null
        if (!authentication.isAuthenticated) return null

        (authentication.principal as? AuthenticatedUser)?.let { user ->
            return ResolvedActor(actor = user.email, user = user)
        }

        val name = authentication.name
        if (name.isNullOrBlank() || name == "anonymousUser") return null

        if (authentication.authorities.any { it.authority == WORKLOAD_ROLE }) {
            return ResolvedActor(actor = "$WORKLOAD_PREFIX$name", user = null)
        }

        // ApiKeyAuthFilter puts the key owner's id on the context as the principal name.
        val isApiKey = authentication.authorities.any { it.authority == "ROLE_API_USER" || it.authority == "ROLE_SERVICE_ACCOUNT" }
        return ResolvedActor(actor = if (isApiKey) "$API_KEY_PREFIX$name" else name, user = null)
    }

    private data class ResolvedActor(val actor: String, val user: AuthenticatedUser?)

    companion object {
        const val SYSTEM = "system"
        const val API_KEY_PREFIX = "api-key:"
        const val WORKLOAD_PREFIX = "workload:"
        private const val WORKLOAD_ROLE = "ROLE_WORKLOAD"
    }
}
