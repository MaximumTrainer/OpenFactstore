package com.factstore.adapter.inbound.web

/**
 * #250 — marks a handler method as superseded. The endpoint keeps working until [sunset], but every
 * response carries `Deprecation` (RFC 9745), `Sunset` (RFC 8594) and a `Link` to [successor], and
 * the OpenAPI document marks the operation `deprecated`. Each use must have a matching decision in
 * `tools/endpoint-inventory/decisions.json`; the docs-drift tests check that.
 *
 * @param since date the endpoint was deprecated, `yyyy-MM-dd` (UTC)
 * @param sunset date after which it may be removed, `yyyy-MM-dd` (UTC)
 * @param successor path template of the replacement, with the same method and path variables
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class DeprecatedEndpoint(val since: String, val sunset: String, val successor: String)

/** The dates every endpoint deprecated in the same release shares. */
object ApiDeprecations {
    const val SINCE_2026_11 = "2026-11-14"
    const val SUNSET_2027_05 = "2027-05-14"
}
