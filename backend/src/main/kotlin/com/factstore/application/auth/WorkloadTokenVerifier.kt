package com.factstore.application.auth

import com.factstore.exception.BadRequestException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Where a trusted issuer's signing keys are fetched from. Replaced in tests by a local key set. */
fun interface JwkSourceFactory {
    fun forUri(jwksUri: String): JWKSource<SecurityContext>
}

@Component
class RemoteJwkSourceFactory : JwkSourceFactory {
    // Nimbus caches the keys and refetches on an unknown `kid`, which handles key rotation.
    override fun forUri(jwksUri: String): JWKSource<SecurityContext> = JWKSourceBuilder.create<SecurityContext>(URL(jwksUri)).build()
}

/**
 * Verifies a CI workload's OIDC token (#189), such as a GitHub Actions or GitLab CI ID token.
 *
 * `OidcAttestationService` used to base64-decode the token and trust it, with a comment saying
 * signature checking was left for production. Anyone could then mint a "GitHub Actions" token
 * naming any repository and have it recorded as provenance. Now:
 *
 * - the issuer must be trusted, and the **signature** is checked against that issuer's JWKS;
 * - `aud` must contain the configured audience (`factstore.oidc.audience`, default
 *   `factstore`, the audience the user guide tells pipelines to request), so a token minted for another service cannot be replayed here;
 * - `exp` must be in the future and `iat` present, with a small clock skew.
 *
 * Trusted by default: GitHub Actions and GitLab.com. More issuers (for example a self-managed
 * GitLab) are added with `factstore.oidc.extra-issuers=<issuer>|<jwks-uri>,...`.
 */
@Component
class WorkloadTokenVerifier(
    private val jwkSourceFactory: JwkSourceFactory,
    @Value("\${factstore.oidc.audience:factstore}") private val audience: String,
    @Value("\${factstore.oidc.extra-issuers:}") extraIssuers: String,
    @Value("\${sso.jwt.clock-skew-seconds:60}") private val clockSkewSeconds: Long
) {
    private val log = LoggerFactory.getLogger(WorkloadTokenVerifier::class.java)

    /** Trusted issuer -> its JWKS URI. */
    val trustedIssuers: Map<String, String> = DEFAULT_ISSUERS + extraIssuers.split(",")
        .map { it.trim() }.filter { it.isNotEmpty() }
        .associate { entry ->
            val parts = entry.split("|")
            require(parts.size == 2) { "factstore.oidc.extra-issuers entries are <issuer>|<jwks-uri>, got '$entry'" }
            parts[0].trim() to parts[1].trim()
        }

    private val processors = ConcurrentHashMap<String, DefaultJWTProcessor<SecurityContext>>()

    companion object {
        val DEFAULT_ISSUERS = mapOf(
            "https://token.actions.githubusercontent.com" to "https://token.actions.githubusercontent.com/.well-known/jwks",
            "https://gitlab.com" to "https://gitlab.com/oauth/discovery/keys"
        )
        private val ALGORITHMS = setOf(JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512, JWSAlgorithm.ES256, JWSAlgorithm.ES384)
        private const val NOT_VERIFIED = "The OIDC token could not be verified"
    }

    /** The verified claims. Throws [BadRequestException] for anything unverifiable. */
    fun verify(token: String): Map<String, Any> {
        val issuer = try {
            SignedJWT.parse(token).jwtClaimsSet.issuer
        } catch (e: Exception) {
            throw BadRequestException("The OIDC token is not a signed JWT")
        } ?: throw BadRequestException("The OIDC token has no 'iss' claim")

        val jwksUri = trustedIssuers[issuer] ?: throw BadRequestException("Untrusted OIDC issuer: $issuer")
        val processor = processors.computeIfAbsent(issuer) { buildProcessor(issuer, jwksUri) }

        val claims: JWTClaimsSet = try {
            processor.process(token, null)
        } catch (e: Exception) {
            log.warn("OIDC workload token rejected for issuer={}: {}: {}", issuer, e.javaClass.simpleName, e.message)
            throw BadRequestException(NOT_VERIFIED)
        }
        return claims.claims
    }

    private fun buildProcessor(issuer: String, jwksUri: String): DefaultJWTProcessor<SecurityContext> =
        DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = JWSVerificationKeySelector(ALGORITHMS, jwkSourceFactory.forUri(jwksUri))
            jwtClaimsSetVerifier = DefaultJWTClaimsVerifier<SecurityContext>(
                audience,
                JWTClaimsSet.Builder().issuer(issuer).build(),
                setOf("iss", "exp", "iat")
            ).also { it.maxClockSkew = clockSkewSeconds.toInt() }
        }
}
