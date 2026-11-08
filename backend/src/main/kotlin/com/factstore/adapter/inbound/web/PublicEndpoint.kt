package com.factstore.adapter.inbound.web

/**
 * Marks an endpoint that is deliberately callable without a Factstore credential (#155 FR-4.1).
 *
 * Every handler method carries either `@PreAuthorize` or this, and `EveryEndpointIsAuthorisedTest`
 * fails the build otherwise. The [reason] is mandatory and appears in
 * `docs/api-authorisation-matrix.md`, so "public" is always a recorded decision. A public endpoint
 * must still authenticate its caller some other way when it changes anything, as the inbound
 * webhook and Slack handlers do by verifying a signature.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PublicEndpoint(val reason: String)
