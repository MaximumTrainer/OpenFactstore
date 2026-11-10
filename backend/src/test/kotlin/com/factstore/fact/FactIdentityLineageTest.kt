package com.factstore.fact

import com.factstore.application.auth.JwkSourceFactory
import com.factstore.application.schema.SchemaRegistryService
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * #191 — identity lineage. The identity that was verified when a fact arrived (issuer, subject,
 * repository, run, actor, workflow, commit) is bound to the fact by the server, stored with it
 * and carried in its `FactIngested` event. A client cannot supply or override it.
 */
@SpringBootTest(
    properties = [
        "security.enforce-auth=true",
        "factstore.oidc.audience=factstore",
        "factstore.oidc.allowed-subjects=repo:org/service:*,project_path:org/service:*"
    ]
)
@AutoConfigureMockMvc
@Transactional
@Import(FactIdentityLineageTest.LocalKeys::class)
class FactIdentityLineageTest {

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
    @Autowired lateinit var facts: IFactRepository
    @Autowired lateinit var eventStore: IEventStore

    private val schemaName = "lineage-${System.nanoTime()}"

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        registry.publish(schemaName, 1, jacksonObjectMapper().readTree("""{"type":"object"}"""))
        SecurityContextHolder.clearContext()
    }

    private fun token(claims: Map<String, Any>): String {
        val builder = JWTClaimsSet.Builder()
            .audience("factstore")
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
        claims.forEach { (k, v) -> builder.claim(k, v) }
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(issuerKey.keyID).build(), builder.build())
            .apply { sign(RSASSASigner(issuerKey)) }.serialize()
    }

    private val github = mapOf(
        "iss" to "https://token.actions.githubusercontent.com",
        "sub" to "repo:org/service:ref:refs/heads/main",
        "repository" to "org/service",
        "run_id" to "9876543210",
        "run_attempt" to "2",
        "actor" to "octocat",
        "workflow_ref" to "org/service/.github/workflows/ci.yml@refs/heads/main",
        "ref" to "refs/heads/main",
        "sha" to "0123456789abcdef0123456789abcdef01234567"
    )

    private fun fact(factId: UUID, extra: String = "") = """
        {"fact_id":"$factId","subject":{"sha256":"${"e".repeat(64)}"},
         "governance":{"schema_ref":"urn:schema:$schemaName:v1"},"tool":{}$extra}
    """.trimIndent()

    private fun ingest(factId: UUID, bearer: String? = null, extra: String = "") =
        mockMvc.post("/api/v1/commands/facts") {
            contentType = MediaType.APPLICATION_JSON
            content = fact(factId, extra)
            bearer?.let { header("Authorization", "Bearer $it") }
        }

    @Test
    fun `#191 the verified GitHub run identity is bound to the fact`() {
        val factId = UUID.randomUUID()
        ingest(factId, token(github)).andExpect { status { isAccepted() } }

        SecurityContextHolder.clearContext()
        mockMvc.get("/api/v1/queries/facts/$factId") {
            with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("auditor").authorities(
                org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_trails:read")))
        }.andExpect {
            status { isOk() }
            jsonPath("$.fact_id") { value(factId.toString()) }
            jsonPath("$.identity.kind") { value("workload") }
            jsonPath("$.identity.issuer") { value("https://token.actions.githubusercontent.com") }
            jsonPath("$.identity.subject") { value("repo:org/service:ref:refs/heads/main") }
            jsonPath("$.identity.repository") { value("org/service") }
            jsonPath("$.identity.runId") { value("9876543210") }
            jsonPath("$.identity.runAttempt") { value("2") }
            jsonPath("$.identity.actor") { value("octocat") }
            jsonPath("$.identity.workflowRef") { value("org/service/.github/workflows/ci.yml@refs/heads/main") }
            jsonPath("$.identity.sha") { value("0123456789abcdef0123456789abcdef01234567") }
        }
    }

    @Test
    fun `#191 the identity travels in the FactIngested event`() {
        val factId = UUID.randomUUID()
        ingest(factId, token(github)).andExpect { status { isAccepted() } }

        val payload = jacksonObjectMapper().readTree(eventStore.findByAggregateId(factId).single().payload)
        assertThat(payload.at("/identity/runId").asText()).isEqualTo("9876543210")
        assertThat(payload.at("/identity/actor").asText()).isEqualTo("octocat")
    }

    @Test
    fun `#191 GitLab CI claims map to the same lineage fields`() {
        val factId = UUID.randomUUID()
        val gitlab = mapOf(
            "iss" to "https://gitlab.com",
            "sub" to "project_path:org/service:ref_type:branch:ref:main",
            "project_path" to "org/service",
            "pipeline_id" to "555",
            "user_login" to "gitlab-dev",
            "ci_config_ref_uri" to "gitlab.com/org/service//.gitlab-ci.yml@refs/heads/main",
            "ref" to "main",
            "sha" to "fedcba9876543210fedcba9876543210fedcba98"
        )
        ingest(factId, token(gitlab)).andExpect { status { isAccepted() } }

        val identity = facts.findById(factId)!!.identity
        assertThat(identity.repository).isEqualTo("org/service")
        assertThat(identity.runId).isEqualTo("555")
        assertThat(identity.actor).isEqualTo("gitlab-dev")
        assertThat(identity.workflowRef).isEqualTo("gitlab.com/org/service//.gitlab-ci.yml@refs/heads/main")
        assertThat(identity.sha).isEqualTo("fedcba9876543210fedcba9876543210fedcba98")
    }

    @Test
    fun `#191 a client cannot supply its own identity`() {
        val factId = UUID.randomUUID()
        ingest(factId, token(github), extra = ""","identity":{"actor":"someone-else"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("invalid_envelope") }
            jsonPath("$.violations[0].path") { value("/identity") }
        }
        assertThat(facts.findById(factId)).isNull()
    }

    @Test
    @WithMockUser(username = "key-owner", authorities = ["SCOPE_attestations:write", "ROLE_API_USER"])
    fun `#191 a fact ingested with a static credential records that credential as its identity`() {
        val factId = UUID.randomUUID()
        ingest(factId).andExpect { status { isAccepted() } }

        val identity = facts.findById(factId)!!.identity
        assertThat(identity.kind).isEqualTo("credential")
        assertThat(identity.subject).isEqualTo("api-key:key-owner")
        assertThat(identity.runId).isNull()
    }

    @Test
    fun `#191 reading a fact needs trails read`() {
        val factId = UUID.randomUUID()
        ingest(factId, token(github)).andExpect { status { isAccepted() } }

        mockMvc.get("/api/v1/queries/facts/$factId") { header("Authorization", "Bearer ${token(github)}") }
            .andExpect { status { isForbidden() } }
    }
}
