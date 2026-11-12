package com.factstore.fact

import com.factstore.application.schema.SchemaRegistryService
import com.factstore.application.signing.SigningKeyRegistryService
import com.factstore.core.port.outbound.IEventStore
import com.factstore.core.port.outbound.IFactRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * #198 — secrets and PII never reach immutable storage.
 *
 * At the ingestion boundary every string in a fact's `tool` payload is checked against the masking
 * ruleset, and fields its schema marks `"x-sensitive": true` must be masked. Two modes:
 *
 * - **reject** (the default): an unmasked value is refused with `400 unmasked_secret_detected`.
 *   The runner masks before it signs, so what is stored is exactly what was signed (#187).
 * - **redact**: Factstore masks the value itself and stores the masked payload. The runner's
 *   signature was verified on arrival, but the signed bytes held the secret, so they are not
 *   stored; their SHA-256 is kept instead.
 */
abstract class FactMaskingTestBase {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var schemas: SchemaRegistryService
    @Autowired lateinit var signingKeys: SigningKeyRegistryService
    @Autowired lateinit var facts: IFactRepository
    @Autowired lateinit var eventStore: IEventStore
    /** Stored content is encrypted (#200); read it back through the service. */
    @Autowired lateinit var ingestion: com.factstore.application.fact.FactIngestionService

    protected val mapper = jacksonObjectMapper()
    protected val signer = TestSigner()
    protected val schemaName = "masked-${System.nanoTime()}"
    private val pipeline = user("pipeline").authorities(SimpleGrantedAuthority("SCOPE_attestations:write"))

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        schemas.publish(
            schemaName, 1,
            mapper.readTree(
                """{"type":"object","properties":{"log":{"type":"string"},"db":{"type":"object","properties":{"password":{"type":"string","x-sensitive":true}}}}}"""
            )
        )
        signingKeys.register(signer.keyId, signer.publicKeyPem)
        SecurityContextHolder.clearContext()
    }

    protected fun body(factId: UUID, tool: String) =
        """{"fact_id":"$factId","subject":{"sha256":"${"3".repeat(64)}"},"governance":{"schema_ref":"urn:schema:$schemaName:v1"},"tool":$tool}"""

    protected fun ingest(factId: UUID, tool: String) = mockMvc.post("/api/v1/commands/facts") {
        with(pipeline)
        contentType = MediaType.APPLICATION_JSON
        signer.signed(this, body(factId, tool))
    }

    protected fun everythingStoredFor(factId: UUID): String =
        listOfNotNull(facts.findById(factId)?.payloadJson, facts.findById(factId)?.signedBody, ingestion.payload(factId), ingestion.signedBody(factId)).joinToString() +
            eventStore.findByAggregateId(factId).joinToString { it.payload }
}

@SpringBootTest(properties = ["security.enforce-auth=true"])
@AutoConfigureMockMvc
@Transactional
class FactMaskingRejectModeTest : FactMaskingTestBase() {

    @Test
    fun `#198 a fact carrying a secret is refused, and the secret is not echoed or stored`() {
        val factId = UUID.randomUUID()
        ingest(factId, """{"log":"deploying with AKIAIOSFODNN7EXAMPLE"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("unmasked_secret_detected") }
            jsonPath("$.maskingRuleset") { value("builtin-v1") }
            jsonPath("$.violations[0].path") { value("/tool/log") }
            jsonPath("$.violations[0].message") { value(org.hamcrest.Matchers.containsString("aws-access-key-id")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("AKIAIOSFODNN7EXAMPLE"))) }
        }
        assertThat(facts.findById(factId)).isNull()
        assertThat(eventStore.findByAggregateId(factId)).isEmpty()
    }

    @Test
    fun `#198 a sensitive field must arrive masked`() {
        ingest(UUID.randomUUID(), """{"db":{"password":"correct horse"}}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.violations[0].path") { value("/tool/db/password") }
        }
    }

    @Test
    fun `#198 a fact masked by the runner before signing is accepted unchanged`() {
        val factId = UUID.randomUUID()
        val tool = """{"log":"deploying with [MASKED:aws-access-key-id]","db":{"password":"[MASKED:sensitive-field]"}}"""

        ingest(factId, tool).andExpect { status { isAccepted() } }

        val stored = facts.findById(factId)!!
        assertThat(mapper.readTree(ingestion.payload(factId))).isEqualTo(mapper.readTree(tool))
        assertThat(ingestion.signedBody(factId)).isEqualTo(body(factId, tool))
        assertThat(stored.maskingRuleset).isEqualTo("builtin-v1")
    }
}

@SpringBootTest(properties = ["security.enforce-auth=true", "factstore.masking.mode=redact"])
@AutoConfigureMockMvc
@Transactional
class FactMaskingRedactModeTest : FactMaskingTestBase() {

    @Test
    fun `#198 secrets and sensitive fields are masked before anything is stored`() {
        val factId = UUID.randomUUID()

        ingest(factId, """{"log":"user bob@example.org deployed with AKIAIOSFODNN7EXAMPLE","db":{"password":"correct horse"}}""")
            .andExpect { status { isAccepted() } }

        val stored = facts.findById(factId)!!
        val payload = mapper.readTree(ingestion.payload(factId))
        assertThat(payload["log"].asText()).isEqualTo("user [MASKED:email] deployed with [MASKED:aws-access-key-id]")
        assertThat(payload.at("/db/password").asText()).isEqualTo("[MASKED:sensitive-field]")
        assertThat(stored.maskedPaths).containsExactlyInAnyOrder("/tool/log", "/tool/db/password")
        assertThat(everythingStoredFor(factId)).doesNotContain("AKIAIOSFODNN7EXAMPLE", "bob@example.org", "correct horse")
    }

    @Test
    fun `#198 the runner's signature is kept, but not the unmasked bytes it covered`() {
        val factId = UUID.randomUUID()
        val tool = """{"log":"AKIAIOSFODNN7EXAMPLE"}"""

        ingest(factId, tool).andExpect { status { isAccepted() } }

        val stored = facts.findById(factId)!!
        assertThat(stored.signature).isEqualTo(signer.sign(body(factId, tool)))
        assertThat(stored.signedBody).isNull()
        assertThat(stored.signedBodySha256).matches("^[0-9a-f]{64}$")
    }

    @Test
    fun `#198 a clean fact is stored exactly as signed`() {
        val factId = UUID.randomUUID()
        ingest(factId, """{"log":"all 120 tests passed"}""").andExpect { status { isAccepted() } }
        val stored = facts.findById(factId)!!
        assertThat(stored.signedBody).isNotNull()
        assertThat(stored.maskedPaths).isEmpty()
    }
}
