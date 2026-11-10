package com.factstore.fact

import com.factstore.application.auth.JwkSourceFactory
import com.factstore.application.schema.SchemaRegistryService
import com.factstore.core.domain.AuditEventType
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.outbound.IEventStore
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
import io.micrometer.core.instrument.MeterRegistry
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
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * #190 — strict ABAC. A CI workload may emit facts only for its own service: the fact's
 * `subject.repository` must equal the repository the workload's verified identity belongs to,
 * taken from the token's `repository` (GitHub) or `project_path` (GitLab) claim, or from the
 * configured service binding of a SPIFFE ID. Anything else is cross-workload injection, refused
 * with `403`, audited and counted, and nothing is stored.
 */
@SpringBootTest(
    properties = [
        "security.enforce-auth=true",
        "factstore.oidc.audience=factstore",
        "factstore.oidc.extra-issuers=https://spire.example.org|https://spire.example.org/keys",
        "factstore.oidc.allowed-subjects=repo:org/*,project_path:org/*,spiffe://example.org/ns/ci/sa/*",
        "factstore.abac.spiffe-services=spiffe://example.org/ns/ci/sa/payments-runner=org/payments"
    ]
)
@AutoConfigureMockMvc
@Transactional
@Import(CrossWorkloadInjectionTest.LocalKeys::class)
class CrossWorkloadInjectionTest {

    companion object {
        val issuerKey: RSAKey = RSAKeyGenerator(2048).keyID("ci-key").generate()
    }

    @TestConfiguration
    class LocalKeys {
        @Bean
        @Primary
        fun localJwks(): JwkSourceFactory = JwkSourceFactory { ImmutableJWKSet<SecurityContext>(JWKSet(issuerKey.toPublicJWK())) }
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var registry: SchemaRegistryService
    @Autowired lateinit var signingKeys: com.factstore.application.signing.SigningKeyRegistryService

    /** Facts are signed at the source (#187). */
    private val signer = com.factstore.fact.TestSigner()
    @Autowired lateinit var facts: IFactRepository
    @Autowired lateinit var eventStore: IEventStore
    @Autowired lateinit var audit: IAuditService
    @Autowired lateinit var meters: MeterRegistry

    private val schemaName = "abac-${System.nanoTime()}"

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        registry.publish(schemaName, 1, jacksonObjectMapper().readTree("""{"type":"object"}"""))
        signingKeys.register(signer.keyId, signer.publicKeyPem)
        SecurityContextHolder.clearContext()
    }

    private fun github(repository: String?) = token(
        buildMap {
            put("iss", "https://token.actions.githubusercontent.com")
            put("sub", "repo:${repository ?: "org/unnamed"}:ref:refs/heads/main")
            repository?.let { put("repository", it) }
            put("run_id", "42")
        }
    )

    private fun token(claims: Map<String, Any>): String {
        val builder = JWTClaimsSet.Builder()
            .audience("factstore")
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
        claims.forEach { (k, v) -> builder.claim(k, v) }
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(issuerKey.keyID).build(), builder.build())
            .apply { sign(RSASSASigner(issuerKey)) }.serialize()
    }

    private fun ingest(factId: UUID, repository: String?, bearer: String? = null, spiffeId: String? = null) =
        mockMvc.post("/api/v1/commands/facts") {
            contentType = MediaType.APPLICATION_JSON
            val subject = repository?.let { """{"sha256":"${"f".repeat(64)}","repository":"$it"}""" }
                ?: """{"sha256":"${"f".repeat(64)}"}"""
            signer.signed(this, """{"fact_id":"$factId","subject":$subject,"governance":{"schema_ref":"urn:schema:$schemaName:v1"},"tool":{}}""")
            bearer?.let { header("Authorization", "Bearer $it") }
            spiffeId?.let { header("X-Spiffe-ID", it) }
        }

    private fun denials() = meters.find("factstore.ingest.denials").counters().sumOf { it.count() }

    @Test
    fun `#190 a pipeline emits facts for its own service`() {
        ingest(UUID.randomUUID(), "org/payments", github("org/payments")).andExpect { status { isAccepted() } }
    }

    @Test
    fun `#190 a pipeline cannot emit facts for another service`() {
        val factId = UUID.randomUUID()
        val before = denials()

        ingest(factId, "org/billing", github("org/payments")).andExpect {
            status { isForbidden() }
            jsonPath("$.error") { value("cross_workload_injection") }
        }

        assertThat(facts.findById(factId)).isNull()
        assertThat(eventStore.findByAggregateId(factId)).isEmpty()
        assertThat(denials()).isEqualTo(before + 1)
        val denied = audit.queryEvents(eventType = AuditEventType.AUTH_DENIED, actor = "workload:repo:org/payments:ref:refs/heads/main")
        assertThat(denied.events).anySatisfy { assertThat(it.payload).contains("org/billing").contains(factId.toString()) }
    }

    @Test
    fun `#190 repository matching is exact, not a prefix`() {
        ingest(UUID.randomUUID(), "org/payments-legacy", github("org/payments")).andExpect { status { isForbidden() } }
    }

    @Test
    fun `#190 a workload fact must name the service it is for`() {
        ingest(UUID.randomUUID(), repository = null, bearer = github("org/payments")).andExpect {
            status { isForbidden() }
            jsonPath("$.error") { value("cross_workload_injection") }
        }
    }

    @Test
    fun `#190 a workload whose token names no repository cannot emit facts`() {
        ingest(UUID.randomUUID(), "org/payments", github(repository = null)).andExpect { status { isForbidden() } }
    }

    @Test
    fun `#190 GitLab pipelines are bound by project path`() {
        val gitlab = token(
            mapOf("iss" to "https://gitlab.com", "sub" to "project_path:org/payments:ref_type:branch:ref:main", "project_path" to "org/payments")
        )
        ingest(UUID.randomUUID(), "org/payments", gitlab).andExpect { status { isAccepted() } }
        ingest(UUID.randomUUID(), "org/billing", gitlab).andExpect { status { isForbidden() } }
    }

    @Test
    fun `#190 a SPIFFE workload is bound to the service configured for its ID`() {
        val svid = "spiffe://example.org/ns/ci/sa/payments-runner"
        val jwt = token(mapOf("iss" to "https://spire.example.org", "sub" to svid))

        ingest(UUID.randomUUID(), "org/payments", jwt, spiffeId = svid).andExpect { status { isAccepted() } }
        ingest(UUID.randomUUID(), "org/billing", jwt, spiffeId = svid).andExpect { status { isForbidden() } }
    }

    @Test
    fun `#190 a SPIFFE workload with no service binding cannot emit facts`() {
        val svid = "spiffe://example.org/ns/ci/sa/unbound-runner"
        ingest(UUID.randomUUID(), "org/payments", token(mapOf("iss" to "https://spire.example.org", "sub" to svid)))
            .andExpect { status { isForbidden() } }
    }

    @Test
    @WithMockUser(username = "key-owner", authorities = ["SCOPE_attestations:write", "ROLE_API_USER"])
    fun `#190 a static credential is not bound to a service`() {
        ingest(UUID.randomUUID(), "org/billing").andExpect { status { isAccepted() } }
    }
}
