package com.factstore.fact

import com.factstore.application.schema.SchemaRegistryService
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
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * #196 — strict ingress validation. `POST /api/v1/commands/facts` refuses, with `400`, any fact
 * whose envelope is malformed or whose payload does not strictly conform to the schema it names
 * in `governance.schema_ref`, so a poison-pill message never reaches the event stream. A
 * conforming fact is accepted with `202` and appended to the event log.
 */
@SpringBootTest(properties = ["security.enforce-auth=true"])
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities = ["SCOPE_attestations:write"])
class FactIngestionTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var registry: SchemaRegistryService
    @Autowired lateinit var signingKeys: com.factstore.application.signing.SigningKeyRegistryService

    /** Facts are signed at the source (#187). */
    private val signer = TestSigner()
    @Autowired lateinit var facts: IFactRepository
    @Autowired lateinit var eventStore: IEventStore

    private val mapper = jacksonObjectMapper()
    private val schemaName = "junit-summary-${System.nanoTime()}"
    private val ref get() = "urn:schema:$schemaName:v1"
    private val sha = "a".repeat(64)

    @BeforeEach
    fun publishSchema() {
        registry.publish(
            schemaName, 1,
            mapper.readTree(
                """
                {
                  "type": "object",
                  "required": ["tool", "passed"],
                  "additionalProperties": false,
                  "properties": {
                    "tool": {"type": "string"},
                    "passed": {"type": "integer", "minimum": 0},
                    "failed": {"type": "integer", "minimum": 0}
                  }
                }
                """
            )
        )
        signingKeys.register(signer.keyId, signer.publicKeyPem)
    }

    private fun fact(
        factId: String = UUID.randomUUID().toString(),
        sha256: String = sha,
        schemaRef: String = ref,
        tool: String = """{"tool":"junit","passed":10}"""
    ) = """
        {
          "fact_id": "$factId",
          "subject": {"sha256": "$sha256", "repository": "org/service"},
          "governance": {"schema_ref": "$schemaRef"},
          "tool": $tool
        }
    """.trimIndent()

    private fun ingest(body: String) = mockMvc.post("/api/v1/commands/facts") {
        contentType = MediaType.APPLICATION_JSON
        signer.signed(this, body)
    }

    @Test
    fun `a conforming fact is accepted and appended to the event stream`() {
        val factId = UUID.randomUUID()

        ingest(fact(factId = factId.toString())).andExpect {
            status { isAccepted() }
            jsonPath("$.fact_id") { value(factId.toString()) }
            jsonPath("$.schema_ref") { value(ref) }
        }

        assertThat(facts.findById(factId)).isNotNull
        assertThat(eventStore.findByAggregateId(factId).map { it.eventType }).containsExactly("FactIngested")
    }

    @Test
    fun `a payload missing a required field is refused as schema_nonconformant`() {
        val factId = UUID.randomUUID()

        ingest(fact(factId = factId.toString(), tool = """{"tool":"junit"}""")).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("schema_nonconformant") }
            jsonPath("$.schemaRef") { value(ref) }
            jsonPath("$.violations[0].message") { value(org.hamcrest.Matchers.containsString("passed")) }
        }
        assertThat(facts.findById(factId)).isNull()
        assertThat(eventStore.findByAggregateId(factId)).isEmpty()
    }

    @Test
    fun `a payload with a field of the wrong type is refused`() {
        ingest(fact(tool = """{"tool":"junit","passed":"ten"}""")).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("schema_nonconformant") }
            jsonPath("$.violations[0].path") { value(org.hamcrest.Matchers.containsString("passed")) }
        }
    }

    @Test
    fun `a payload with a field the schema does not allow is refused`() {
        ingest(fact(tool = """{"tool":"junit","passed":1,"secret":"x"}""")).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("schema_nonconformant") }
        }
    }

    @Test
    fun `a fact naming an unregistered schema is refused`() {
        ingest(fact(schemaRef = "urn:schema:nobody-published-this:v1")).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("unknown_schema") }
        }
    }

    @Test
    fun `a malformed schema reference is refused as an invalid envelope`() {
        ingest(fact(schemaRef = "junit-v1")).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("invalid_envelope") }
            jsonPath("$.violations[0].path") { value("/governance/schema_ref") }
        }
    }

    @Test
    fun `a subject digest that is not 64 hex characters is refused`() {
        ingest(fact(sha256 = "not-a-digest")).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("invalid_envelope") }
            jsonPath("$.violations[0].path") { value("/subject/sha256") }
        }
    }

    @Test
    fun `a fact id that is not a UUID is refused`() {
        ingest(fact(factId = "fact-1")).andExpect {
            status { isBadRequest() }
            jsonPath("$.violations[0].path") { value("/fact_id") }
        }
    }

    @Test
    fun `an envelope field the contract does not define is refused`() {
        val body = fact().replaceFirst("{", "{\"priority\": \"high\",")
        ingest(body).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("invalid_envelope") }
            jsonPath("$.violations[0].path") { value("/priority") }
        }
    }

    @Test
    fun `a body that is not JSON is refused`() {
        ingest("{not json").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `the same fact id cannot be ingested twice`() {
        val body = fact()
        ingest(body).andExpect { status { isAccepted() } }
        ingest(body).andExpect { status { isConflict() } }
    }

    @Test
    @WithMockUser(authorities = ["SCOPE_trails:read"])
    fun `ingesting a fact needs attestations write`() {
        ingest(fact()).andExpect { status { isForbidden() } }
    }
}
