package com.factstore.core.port.outbound

/**
 * The organisation the current caller is acting in (#155 FR-5).
 *
 * `null` means the caller is not bound to one organisation: an anonymous caller on an instance
 * without enforcement, an API key created without `orgSlug`, or a user with no active
 * organisation. Such a caller sees every organisation, as before tenant binding existed.
 */
interface ICallerOrganisation {
    fun orgSlug(): String?

    /** A real credential was presented: not an anonymous caller on an unenforced instance. */
    fun isAuthenticated(): Boolean = false

    /** The caller holds the `operator` scope (#207). */
    fun isOperator(): Boolean = false

    /**
     * Note that an unbound caller read a resource belonging to [orgSlug] in this request, so the
     * request is audited once as a cross-organisation read (#155 FR-5.4).
     */
    fun recordCrossTenantRead(orgSlug: String) {}
}
