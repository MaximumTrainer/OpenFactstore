package com.factstore.security

import com.factstore.application.auth.JwkSourceFactory
import com.factstore.application.schema.SchemaRegistryService
import com.factstore.core.port.outbound.IFactRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * #189 — CI runners authenticate with short-lived workload identities instead of static keys.
 *
 * A GitHub Actions or GitLab CI OIDC token, or a SPIFFE JWT-SVID, is presented as
 * `Authorization: Bearer`. It is believed only when its signature verifies against a trusted
 * issuer's keys, its audience and expiry check out, and its subject matches a configured trust
 * pattern; it then carries only the workload scopes. `X-Spiffe-ID` is never trusted on its own:
 * it must name the verified token's subject.
 *
 * Issuers are served by a local key set; nothing calls a real provider.
 */
@SpringBootTest(
    properties = [
        "security.enforce-auth=true",
        "factstore.oidc.audience=factstore",
        "factstore.oidc.extra-issuers=https://spire.example.org|https://spire.example.org/keys",
        "factstore.oidc.allowed-subjects=repo:org/service:*,spiffe://example.org/ns/ci/sa/*",
        // Workloads emit facts only for their own service (#190).
        "factstore.abac.spiffe-services=spiffe://example.org/ns/ci/sa/pipeline-runner=org/service"
    ]
)
@AutoConfigureMockMvc
@Transactional
@Import(WorkloadIdentityTest.LocalKeys::class)
class WorkloadIdentityTest {

    companion object {
        val issuerKey: RSAKey = RSAKeyGenerator(2048).keyID("ci-key").generate()
        val strangerKey: RSAKey = RSAKeyGenerator(2048).keyID("ci-key").generate()
        const val GITHUB = "https://token.actions.githubusercontent.com"
        const val SPIRE = "https://spire.example.org"
    }

    @TestConfiguration
    class LocalKeys {
        @Bean
        @Primary
        fun localJwks(): JwkSourceFactory = JwkSourceFactory { ImmutableJWKSet<SecurityContext>(JWKSet(issuerKey.toPublicJWK())) }
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var registry: SchemaRegistryService
    @Autowired lateinit var facts: IFactRepository

    private val schemaName = "workload-${System.nanoTime()}"

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        registry.publish(schemaName, 1, jacksonObjectMapper().readTree("""{"type":"object"}"""))
        SecurityContextHolder.clearContext()
    }

    private fun token(
        issuer: String = GITHUB,
        subject: String = "repo:org/service:ref:refs/heads/main",
        key: RSAKey = issuerKey,
        audience: String = "factstore",
        expiresAt: Instant = Instant.now().plusSeconds(300)
    ): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(subject)
            .claim("repository", "org/service")
            .audience(audience)
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(expiresAt))
            .build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
            .apply { sign(RSASSASigner(key)) }.serialize()
    }

    private fun fact(factId: UUID = UUID.randomUUID()) = """
        {"fact_id":"$factId","subject":{"sha256":"${"c".repeat(64)}","repository":"org/service"},
         "governance":{"schema_ref":"urn:schema:$schemaName:v1"},"tool":{}}
    """.trimIndent()

    private fun ingest(bearer: String?, spiffeId: String? = null, body: String = fact()) =
        mockMvc.post("/api/v1/commands/facts") {
            contentType = MediaType.APPLICATION_JSON
            content = body
            bearer?.let { header("Authorization", "Bearer $it") }
            spiffeId?.let { header("X-Spiffe-ID", it) }
        }

    @Test
    fun `#189 a GitHub Actions token from a trusted repository ingests a fact`() {
        val factId = UUID.randomUUID()

        ingest(token(), body = fact(factId)).andExpect { status { isAccepted() } }

        assertThat(facts.findById(factId)!!.ingestedBy).isEqualTo("workload:repo:org/service:ref:refs/heads/main")
    }

    @Test
    fun `#189 a SPIFFE JWT-SVID whose X-Spiffe-ID matches its subject ingests a fact`() {
        val svid = "spiffe://example.org/ns/ci/sa/pipeline-runner"
        ingest(token(issuer = SPIRE, subject = svid), spiffeId = svid).andExpect { status { isAccepted() } }
    }

    @Test
    fun `#189 an X-Spiffe-ID that does not name the token's subject is refused`() {
        ingest(
            token(issuer = SPIRE, subject = "spiffe://example.org/ns/ci/sa/pipeline-runner"),
            spiffeId = "spiffe://example.org/ns/ci/sa/someone-else"
        ).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `#189 an X-Spiffe-ID header without a verified token is not an identity`() {
        ingest(bearer = null, spiffeId = "spiffe://example.org/ns/ci/sa/pipeline-runner")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `#189 a token from a repository outside the trust patterns is refused`() {
        ingest(token(subject = "repo:attacker/fork:ref:refs/heads/main")).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `#189 a token signed by a key the issuer does not publish is refused`() {
        ingest(token(key = strangerKey)).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `#189 a token minted for another audience is refused`() {
        ingest(token(audience = "sts.amazonaws.com")).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `#189 an expired token is refused`() {
        ingest(token(expiresAt = Instant.now().minusSeconds(3600))).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `#189 a workload identity carries only the workload scopes`() {
        mockMvc.put("/api/v1/commands/schemas/$schemaName/v2") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"type":"object"}"""
            header("Authorization", "Bearer ${token()}")
        }.andExpect { status { isForbidden() } }
    }
}
