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
}
