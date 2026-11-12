package com.factstore.exception

class NotFoundException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)
class IntegrityException(message: String) : RuntimeException(message)
class BadRequestException(message: String) : RuntimeException(message)

/** A stored event could not be signed (#184): the write is rolled back and the command fails. */
class EventSigningException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class PullRequestNotFoundException(message: String) : RuntimeException(message)

/**
 * The caller is authenticated but not permitted (#155 FR-3.4, FR-4).
 *
 * Distinct from [NotFoundException]: a 403 says "you exist and may not do this", which is the
 * right answer for a scope the credential does not hold. Cross-*tenant* access is deliberately
 * a 404 instead, so probing cannot enumerate another organisation's resources (FR-5.2).
 */
class ForbiddenException(message: String) : RuntimeException(message)

/**
 * A fact refused at ingress (#196): its envelope was malformed, it named an unregistered schema,
 * or its payload did not conform. Rendered as `400` with every violation found.
 */
class FactRejectedException(
    val error: String,
    val schemaRef: String?,
    val violations: List<com.factstore.application.schema.SchemaViolation>,
    /** For `unmasked_secret_detected` (#198): the ruleset the payload was checked against. */
    val maskingRuleset: String? = null
) : RuntimeException("$error: ${violations.joinToString("; ") { "${it.path} ${it.message}" }}") {
    companion object {
        const val INVALID_ENVELOPE = "invalid_envelope"
        const val UNKNOWN_SCHEMA = "unknown_schema"
        const val SCHEMA_NONCONFORMANT = "schema_nonconformant"
        const val UNMASKED_SECRET = "unmasked_secret_detected"
    }
}

/** A workload tried to emit a fact for a service it does not belong to (#190). Rendered as `403`. */
class CrossWorkloadInjectionException(message: String) : RuntimeException(message)

/** A fact's source signature is missing or does not verify (#187). Rendered as `401`. */
class SignatureRejectedException(val error: String, message: String) : RuntimeException(message) {
    companion object {
        const val MISSING = "signature_missing"
        const val MALFORMED = "signature_malformed"
        const val UNKNOWN_KEY = "unknown_signing_key"
        const val REVOKED_KEY = "signing_key_revoked"
        const val INVALID = "signature_invalid"
    }
}
