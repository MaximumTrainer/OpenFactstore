package com.factstore.application

import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.domain.OidcJtiLog
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IOidcJtiLogRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.Base64
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class OidcAttestationServiceTest {

    @Mock private lateinit var trailRepository: ITrailRepository
    @Mock private lateinit var attestationRepository: IAttestationRepository
    @Mock private lateinit var jtiLogRepository: IOidcJtiLogRepository

    private val objectMapper = ObjectMapper()

    private val service: OidcAttestationService by lazy {
        OidcAttestationService(trailRepository, attestationRepository, jtiLogRepository, objectMapper)
    }

    private fun buildJwt(claims: Map<String, Any>): String {
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(objectMapper.writeValueAsString(claims).toByteArray())
        return "$header.$payload.fake-signature"
    }

    @Test
    fun `record OIDC attestation from GitHub Actions creates PASSED attestation`() {
        val trailId = UUID.randomUUID()
        val claims = mapOf(
            "iss" to "https://token.actions.githubusercontent.com",
            "jti" to "unique-jti-1",
            "sub" to "repo:org/repo:ref:refs/heads/main",
            "repository" to "org/repo",
            "workflow" to "ci.yml",
            "ref" to "refs/heads/main",
            "sha" to "abc123",
            "actor" to "bot",
            "run_id" to "12345",
            "iat" to 1700000000
        )
        whenever(trailRepository.existsById(trailId)).thenReturn(true)
        whenever(jtiLogRepository.existsByJti("unique-jti-1")).thenReturn(false)
        whenever(jtiLogRepository.save(any())).thenAnswer { it.arguments[0] as OidcJtiLog }
        whenever(attestationRepository.save(any())).thenAnswer { it.arguments[0] as Attestation }

        val response = service.recordOidcAttestation(OidcAttestationRequest(trailId, buildJwt(claims)))

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
            "ref" to "main",
            "iat" to 1700000000
        )
        whenever(trailRepository.existsById(trailId)).thenReturn(true)
        whenever(jtiLogRepository.existsByJti("gitlab-jti-99")).thenReturn(false)
        whenever(jtiLogRepository.save(any())).thenAnswer { it.arguments[0] as OidcJtiLog }
        whenever(attestationRepository.save(any())).thenAnswer { it.arguments[0] as Attestation }

        val response = service.recordOidcAttestation(OidcAttestationRequest(trailId, buildJwt(claims)))

        assertEquals("oidc-provenance", response.type)
        assertEquals(AttestationStatus.PASSED, response.status)
    }

    @Test
    fun `throws NotFoundException when trail not found`() {
        val trailId = UUID.randomUUID()
        whenever(trailRepository.existsById(trailId)).thenReturn(false)

        assertThrows(NotFoundException::class.java) {
            service.recordOidcAttestation(
                OidcAttestationRequest(trailId, buildJwt(mapOf("iss" to "https://token.actions.githubusercontent.com")))
            )
        }
    }

    @Test
    fun `throws IllegalArgumentException for untrusted issuer`() {
        val trailId = UUID.randomUUID()
        val claims = mapOf("iss" to "https://evil.com", "jti" to "evil-jti")
        whenever(trailRepository.existsById(trailId)).thenReturn(true)
        // jtiLogRepository is NOT stubbed: the service throws before reaching the JTI check

        assertThrows(IllegalArgumentException::class.java) {
            service.recordOidcAttestation(OidcAttestationRequest(trailId, buildJwt(claims)))
        }
    }

    @Test
    fun `throws ConflictException when jti already used`() {
        val trailId = UUID.randomUUID()
        val claims = mapOf(
            "iss" to "https://token.actions.githubusercontent.com",
            "jti" to "already-used-jti"
        )
        whenever(trailRepository.existsById(trailId)).thenReturn(true)
        whenever(jtiLogRepository.existsByJti("already-used-jti")).thenReturn(true)

        assertThrows(ConflictException::class.java) {
            service.recordOidcAttestation(OidcAttestationRequest(trailId, buildJwt(claims)))
        }
    }

    @Test
    fun `throws IllegalArgumentException for missing iss claim`() {
        val trailId = UUID.randomUUID()
        val claims = mapOf("jti" to "some-jti", "sub" to "some-subject")
        whenever(trailRepository.existsById(trailId)).thenReturn(true)

        assertThrows(IllegalArgumentException::class.java) {
            service.recordOidcAttestation(OidcAttestationRequest(trailId, buildJwt(claims)))
        }
    }
}
