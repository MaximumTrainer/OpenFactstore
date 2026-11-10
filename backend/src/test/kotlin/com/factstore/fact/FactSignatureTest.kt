package com.factstore.fact

import com.factstore.application.schema.SchemaRegistryService
import com.factstore.application.signing.SigningKeyRegistryService
import com.factstore.core.domain.AuditEventType
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.outbound.IEventStore
import com.factstore.core.port.outbound.IFactRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.UUID

/**
 * #187 — every fact is signed at the source. The CI runner signs the exact request body with an
 * Ed25519 key and sends `X-Signature` and `X-Signature-Key-Id`; the signature is verified
 * against the trusted signing-key registry before anything is stored. A missing or bad signature
 * never reaches the ledger.
 */
@SpringBootTest(properties = ["security.enforce-auth=true"])
@AutoConfigureMockMvc
@Transactional
class FactSignatureTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var schemas: SchemaRegistryService
    @Autowired lateinit var signingKeys: SigningKeyRegistryService
    @Autowired lateinit var facts: IFactRepository
    @Autowired lateinit var eventStore: IEventStore
    @Autowired lateinit var audit: IAuditService

    private val schemaName = "signed-${System.nanoTime()}"
    private val runner = TestSigner()
    private val pipeline = user("pipeline").authorities(SimpleGrantedAuthority("SCOPE_attestations:write"))
    private val admin = user("admin@example.com").authorities(SimpleGrantedAuthority("SCOPE_admin"))

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        schemas.publish(
            schemaName, 1,
            jacksonObjectMapper().readTree("""{"type":"object","required":["passed"],"properties":{"passed":{"type":"integer"}}}""")
        )
        signingKeys.register(runner.keyId, runner.publicKeyPem)
        SecurityContextHolder.clearContext()
    }

    private fun fact(factId: UUID = UUID.randomUUID(), tool: String = """{"passed":3}""") =
        """{"fact_id":"$factId","subject":{"sha256":"${"9".repeat(64)}"},"governance":{"schema_ref":"urn:schema:$schemaName:v1"},"tool":$tool}"""

    private fun post(body: String, signature: String?, keyId: String? = runner.keyId) =
        mockMvc.post("/api/v1/commands/facts") {
            with(pipeline)
            contentType = MediaType.APPLICATION_JSON
            content = body
            signature?.let { header("X-Signature", it) }
            keyId?.let { header("X-Signature-Key-Id", it) }
        }

    private fun assertNothingStored(factId: UUID) {
        assertThat(facts.findById(factId)).isNull()
        assertThat(eventStore.findByAggregateId(factId)).isEmpty()
    }

    @Test
    fun `#187 a fact signed by a registered key is accepted and keeps its signature`() {
        val factId = UUID.randomUUID()
        val body = fact(factId)

        post(body, runner.sign(body)).andExpect { status { isAccepted() } }

        val stored = facts.findById(factId)!!
        assertThat(stored.signatureKeyId).isEqualTo(runner.keyId)
        assertThat(stored.signature).isEqualTo(runner.sign(body))
        assertThat(stored.signedBody).isEqualTo(body)
    }

    @Test
    fun `#187 an unsigned fact never reaches the ledger`() {
        val factId = UUID.randomUUID()
        post(fact(factId), signature = null).andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("signature_missing") }
        }
        assertNothingStored(factId)
    }

    @Test
    fun `#187 a signature without a key id is refused`() {
        val body = fact()
        post(body, runner.sign(body), keyId = null).andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("signature_missing") }
        }
    }

    @Test
    fun `#187 a malformed signature is refused`() {
        val factId = UUID.randomUUID()
        post(fact(factId), signature = "not base64 !!").andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("signature_malformed") }
        }
        assertNothingStored(factId)
    }

    @Test
    fun `#187 a body altered after signing is refused`() {
        val factId = UUID.randomUUID()
        val signedBody = fact(factId, tool = """{"passed":3}""")
        val tampered = fact(factId, tool = """{"passed":4}""")

        post(tampered, runner.sign(signedBody)).andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("signature_invalid") }
        }
        assertNothingStored(factId)
    }

    @Test
    fun `#187 a signature from a key the registry does not trust is refused`() {
        val stranger = TestSigner()
        val factId = UUID.randomUUID()
        val body = fact(factId)

        post(body, stranger.sign(body), keyId = stranger.keyId).andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("unknown_signing_key") }
        }
        assertNothingStored(factId)
    }

    @Test
    fun `#187 a signature by another key under a trusted key id is refused`() {
        val impostor = TestSigner(keyId = runner.keyId)
        val body = fact()
        post(body, impostor.sign(body)).andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("signature_invalid") }
        }
    }

    @Test
    fun `#187 a revoked key no longer verifies`() {
        mockMvc.post("/api/v1/commands/signing-keys/${runner.keyId}/revoke") { with(admin) }
            .andExpect { status { isOk() } }

        val factId = UUID.randomUUID()
        val body = fact(factId)
        post(body, runner.sign(body)).andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("signing_key_revoked") }
        }
        assertNothingStored(factId)
        assertThat(audit.queryEvents(eventType = AuditEventType.SIGNING_KEY_REVOKED).events)
            .anySatisfy { assertThat(it.payload).contains(runner.keyId) }
    }

    @Test
    fun `#187 after rotation the old and new keys both verify until the old one is revoked`() {
        val next = TestSigner()
        mockMvc.post("/api/v1/commands/signing-keys") {
            with(admin)
            contentType = MediaType.APPLICATION_JSON
            content = jacksonObjectMapper().writeValueAsString(mapOf("keyId" to next.keyId, "publicKey" to next.publicKeyPem))
        }.andExpect { status { isCreated() } }

        val old = fact()
        val new = fact()
        post(old, runner.sign(old)).andExpect { status { isAccepted() } }
        post(new, next.sign(new), keyId = next.keyId).andExpect { status { isAccepted() } }
    }

    @Test
    fun `#187 schema validation runs before signature verification`() {
        post(fact(tool = """{"passed":"three"}"""), signature = null).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("schema_nonconformant") }
        }
    }

    @Test
    fun `#187 only Ed25519 public keys can be registered`() {
        val rsa = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem = "-----BEGIN PUBLIC KEY-----\n${Base64.getMimeEncoder().encodeToString(rsa.public.encoded)}\n-----END PUBLIC KEY-----\n"
        mockMvc.post("/api/v1/commands/signing-keys") {
            with(admin)
            contentType = MediaType.APPLICATION_JSON
            content = jacksonObjectMapper().writeValueAsString(mapOf("keyId" to "rsa-key", "publicKey" to pem))
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `#187 a key id cannot be registered twice`() {
        val again = TestSigner(keyId = runner.keyId)
        mockMvc.post("/api/v1/commands/signing-keys") {
            with(admin)
            contentType = MediaType.APPLICATION_JSON
            content = jacksonObjectMapper().writeValueAsString(mapOf("keyId" to again.keyId, "publicKey" to again.publicKeyPem))
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `#187 managing signing keys needs admin`() {
        val next = TestSigner()
        mockMvc.post("/api/v1/commands/signing-keys") {
            with(pipeline)
            contentType = MediaType.APPLICATION_JSON
            content = jacksonObjectMapper().writeValueAsString(mapOf("keyId" to next.keyId, "publicKey" to next.publicKeyPem))
        }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/queries/signing-keys") { with(pipeline) }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/queries/signing-keys") { with(admin) }.andExpect {
            status { isOk() }
            jsonPath("$[?(@.keyId == '${runner.keyId}')].status") { value("ACTIVE") }
        }
    }
}
