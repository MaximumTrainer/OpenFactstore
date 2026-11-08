package com.factstore.config

import com.factstore.core.domain.security.Permission
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * The rule every endpoint's `@PreAuthorize` evaluates, as `@authz.has('scope')` (#155 FR-4).
 *
 * Method security is always on (see [MethodSecurityConfig]), so a bare `hasAuthority` would
 * refuse anonymous callers even where `security.enforce-auth` is still `false`, turning on
 * enforcement without anyone choosing to. These rules keep the rollout order #155 §5 prescribes:
 *
 * - **Enforcement off:** an anonymous caller passes, as the route rules already allow. A caller
 *   that presented a credential is held to that credential's scopes, so least privilege applies
 *   to keys from day one.
 * - **Enforcement on:** a credential carrying the scope is required.
 * - **Warn mode** (`security.warn-mode`): a refusal is logged with the scope it lacked, and the
 *   request is allowed, so operators can find every caller that would break before switching.
 *
 * The `admin` scope already expands to every permission when a credential is authenticated, so
 * it needs no special case here.
 */
@Component("authz")
class AuthorisationRules(
    @Value("\${security.enforce-auth:false}") private val enforceAuth: Boolean,
    @Value("\${security.warn-mode:false}") private val warnMode: Boolean
) {
    private val log = LoggerFactory.getLogger(AuthorisationRules::class.java)

    /** The caller holds [scope], or is anonymous on an instance that does not enforce authentication. */
    fun has(scope: String): Boolean = decide(scope) { auth -> auth.holds(scope) }

    /** The caller holds at least one of [scopes]. */
    fun hasAny(vararg scopes: String): Boolean =
        decide(scopes.joinToString(" or ")) { auth -> scopes.any { auth.holds(it) } }

    /** Any authenticated caller, or anyone on an instance that does not enforce authentication. */
    fun authenticated(): Boolean = decide("an authenticated caller") { true }

    private fun decide(requirement: String, check: (Authentication) -> Boolean): Boolean {
        val auth = SecurityContextHolder.getContext().authentication
            ?.takeIf { it.isAuthenticated && it !is AnonymousAuthenticationToken }
        val allowed = when {
            auth != null -> check(auth)
            else -> !enforceAuth
        }
        if (!allowed && warnMode) {
            log.warn("WARN-MODE: would refuse {} lacking {}; allowing", auth?.name ?: "an anonymous caller", requirement)
            return true
        }
        return allowed
    }

    private fun Authentication.holds(scope: String): Boolean =
        authorities.any { it.authority == "${Permission.AUTHORITY_PREFIX}$scope" }
}
