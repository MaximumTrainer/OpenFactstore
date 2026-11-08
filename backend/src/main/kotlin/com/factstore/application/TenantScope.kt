package com.factstore.application

import com.factstore.core.port.outbound.ICallerOrganisation
import com.factstore.exception.NotFoundException
import org.springframework.stereotype.Component

/**
 * Constrains reads to the caller's organisation (#155 FR-5.2, FR-5.3).
 *
 * A caller bound to an organisation sees only resources whose `orgSlug` is that organisation;
 * a resource with no organisation, or another one, is invisible to it. Invisible means
 * **not found**: asking for another organisation's flow by id is a 404, never a 403, so a
 * credential cannot use the difference to discover what exists elsewhere.
 *
 * A caller bound to no organisation sees everything, which is how every deployment behaved
 * before keys and sessions carried an organisation.
 */
@Component
class TenantScope(private val caller: ICallerOrganisation) {

    /** The caller's organisation, or null when unbound. */
    fun orgSlug(): String? = caller.orgSlug()

    fun visible(resourceOrgSlug: String?): Boolean {
        val org = caller.orgSlug() ?: return true
        return resourceOrgSlug == org
    }

    /** Returns [resource] when visible, otherwise throws the same 404 a missing one would. */
    fun <T> require(resource: T?, resourceOrgSlug: (T) -> String?, notFound: () -> String): T {
        if (resource == null || !visible(resourceOrgSlug(resource))) throw NotFoundException(notFound())
        return resource
    }

    companion object {
        /** No caller context: sees everything. For unit tests and internal jobs. */
        val UNSCOPED = TenantScope(object : ICallerOrganisation { override fun orgSlug(): String? = null })
    }
}
