package com.factstore.application

import com.factstore.application.auth.WorkloadTokenVerifier
import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.OidcJtiLog
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IOidcJtiLogRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.exception.BadRequestException
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * OIDC provenance attestations (#189): the CI provider's token is only believed once its
 * signature verifies against the provider's keys, and its audience and expiry check out.
 *
 * GitHub and GitLab are served here by a local key set, so nothing calls a real provider.
 */
@ExtendWith(MockitoExtension::class)
class OidcAttestationServiceTest {

    @Mock private lateinit var trailRepository: ITrailRepository
    @Mock private lateinit var attestationRepository: IAttestationRepository
    @Mock private lateinit var jtiLogRepository: IOidcJtiLogRepository

    private val objectMapper = ObjectMapper()

    private val providerKey: RSAKey = RSAKeyGenerator(2048).keyID("provider-key").generate()
    private val strangerKey: RSAKey = RSAKeyGenerator(2048).keyID("provider-key").generate()

    private val verifier = WorkloadTokenVerifier(
        jwkSourceFactory = { ImmutableJWKSet<SecurityContext>(JWKSet(providerKey.toPublicJWK())) },
        audience = "openfactstore",
        extraIssuers = "",
        clockSkewSeconds = 60
    )

    private val service: OidcAttestationService by lazy {
        OidcAttestationService(trailRepository, attestationRepository, jtiLogRepository, objectMapper, verifier)
    }

    private fun signed(
        claims: Map<String, Any>,
        key: RSAKey = providerKey,
        audience: String = "openfactstore",
        expiresAt: Instant = Instant.now().plusSeconds(300)
    ): String {
        val builder = JWTClaimsSet.Builder()
            .audience(audience)
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(expiresAt))
        claims.forEach { (k, v) -> builder.claim(k, v) }
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), builder.build())
            .apply { sign(RSASSASigner(key)) }
            .serialize()
    }

    private val github = mapOf(
        "iss" to "https://token.actions.githubusercontent.com",
        "jti" to "unique-jti-1",
        "sub" to "repo:org/repo:ref:refs/heads/main",
        "repository" to "org/repo",
        "workflow" to "ci.yml",
        "ref" to "refs/heads/main",
        "sha" to "abc123",
        "actor" to "bot",
        "run_id" to "12345"
    )

    private fun acceptsAttestation() {
        whenever(jtiLogRepository.existsByJti(any())).thenReturn(false)
        whenever(jtiLogRepository.save(any())).thenAnswer { it.arguments[0] as OidcJtiLog }
        whenever(attestationRepository.save(any())).thenAnswer { it.arguments[0] as Attestation }
    }

    @Test
    fun `record OIDC attestation from GitHub Actions creates PASSED attestation`() {
        val trailId = UUID.randomUUID()
        whenever(trailRepository.existsById(trailId)).thenReturn(true)
        acceptsAttestation()

        val response = service.recordOidcAttestation(OidcAttestationRequest(trailId, signed(github)))

        assertEquals("oidc-provenance", response.type)
        assertEquals(AttestationStatus.PASSED, response.status)
    }

    @Test
    fun `record OIDC attestation from GitLab CI creates PASSED attestation`() {
        val trailId = UUID.randomUUID()
        val claims = mapOf(
            "iss" to "https://gitlab.com",
            "jti" to "gitlab-jti-99",
            "sub" to "project_path:org/repo:ref_type:branch:ref:main",
            "project_path" to "org/repo",
            "ref" to "main"
        )
        whenever(trailRepository.existsById(trailId)).thenReturn(true)
        acceptsAttestation()

        val response = service.recordOidcAttestation(OidcAttestationRequest(trailId, signed(claims)))

        assertEquals("oidc-provenance", response.type)
        assertEquals(AttestationStatus.PASSED, response.status)
    }

    @Test
    fun `a token with a forged signature is refused and nothing is recorded`() {
        val trailId = UUID.randomUUID()
        whenever(trailRepository.existsById(trailId)).thenReturn(true)
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(github))
        val forged = "eyJhbGciOiJSUzI1NiJ9.$payload.fake-signature"

        assertThrows(BadRequestException::class.java) {
            service.recordOidcAttestation(OidcAttestationRequest(trailId, forged))
        }
        verify(attestationRepository, never()).save(any())
    }

    @Test
    fun `a token signed by a key the provider does not publish is refused`() {
        val trailId = UUID.randomUUID()
        whenever(trailRepository.existsById(trailId)).thenReturn(true)

        assertThrows(BadRequestException::class.java) {
            service.recordOidcAttestation(OidcAttestationRequest(trailId, signed(github, key = strangerKey)))
        }
        verify(attestationRepository, never()).save(any())
    }

    @Test
    fun `a token minted for another audience is refused`() {
        val trailId = UUID.randomUUID()
        whenever(trailRepository.existsById(trailId)).thenReturn(true)

        assertThrows(BadRequestException::class.java) {
            service.recordOidcAttestation(OidcAttestationRequest(trailId, signed(github, audience = "https://github.com/org")))
        }
    }

    @Test
    fun `an expired token is refused`() {
        val trailId = UUID.randomUUID()
        whenever(trailRepository.existsById(trailId)).thenReturn(true)

        assertThrows(BadRequestException::class.java) {
            service.recordOidcAttestation(
                OidcAttestationRequest(trailId, signed(github, expiresAt = Instant.now().minusSeconds(3600)))
            )
        }
    }

    @Test
    fun `throws NotFoundException when trail not found`() {
        val trailId = UUID.randomUUID()
        whenever(trailRepository.existsById(trailId)).thenReturn(false)

        assertThrows(NotFoundException::class.java) {
            service.recordOidcAttestation(OidcAttestationRequest(trailId, signed(github)))
        }
    }

    @Test
    fun `an untrusted issuer is a bad request, not a server error`() {
        val trailId = UUID.randomUUID()
        whenever(trailRepository.existsById(trailId)).thenReturn(true)

        assertThrows(BadRequestException::class.java) {
            service.recordOidcAttestation(OidcAttestationRequest(trailId, signed(github + ("iss" to "https://evil.com"))))
        }
    }

    @Test
    fun `throws ConflictException when jti already used`() {
        val trailId = UUID.randomUUID()
        whenever(trailRepository.existsById(trailId)).thenReturn(true)
        whenever(jtiLogRepository.existsByJti("already-used-jti")).thenReturn(true)

        assertThrows(ConflictException::class.java) {
            service.recordOidcAttestation(OidcAttestationRequest(trailId, signed(github + ("jti" to "already-used-jti"))))
        }
    }

    @Test
    fun `a missing iss claim is a bad request, not a server error`() {
        val trailId = UUID.randomUUID()
        whenever(trailRepository.existsById(trailId)).thenReturn(true)

        assertThrows(BadRequestException::class.java) {
            service.recordOidcAttestation(OidcAttestationRequest(trailId, signed(github - "iss")))
        }
    }
}
