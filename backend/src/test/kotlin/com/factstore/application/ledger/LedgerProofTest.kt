package com.factstore.application.ledger

import com.factstore.application.schema.SchemaRegistryService
import com.factstore.core.port.outbound.IEventStore
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * #188 — tamper-evident hashing an auditor can check without trusting Factstore.
 *
 * Signed event records (#184) are checkpointed into RFC 9162 Merkle trees, and each checkpoint's
 * root is signed with the event-signing key and published outside the application. The proof
 * endpoint returns everything needed to re-check a fact: its record's canonical bytes and
 * signature, its inclusion path, and the signed checkpoint. The standalone verifier in
 * `tools/ledger-verifier`, which shares no code with the server, must reach the same answer.
 */
@SpringBootTest(properties = ["security.enforce-auth=true", "factstore.ingest.require-signature=false"])
@AutoConfigureMockMvc
@Transactional
class LedgerProofTest {

    companion object {
        @TempDir
        @JvmStatic
        lateinit var published: Path

        @DynamicPropertySource
        @JvmStatic
        fun checkpointFile(registry: DynamicPropertyRegistry) {
            registry.add("factstore.ledger.checkpoint-file") { published.resolve("checkpoints.jsonl").toString() }
        }
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var schemas: SchemaRegistryService
    @Autowired lateinit var eventStore: IEventStore
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var em: jakarta.persistence.EntityManager

    private val mapper = jacksonObjectMapper()
    private val pipeline = user("pipeline").authorities(SimpleGrantedAuthority("SCOPE_attestations:write"))
    private val auditor = user("auditor").authorities(SimpleGrantedAuthority("SCOPE_trails:read"))
    private val admin = user("admin@example.com").authorities(SimpleGrantedAuthority("SCOPE_admin"))

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        schemas.publish("ledger-proof", 1, mapper.readTree("""{"type":"object"}"""))
        SecurityContextHolder.clearContext()
    }

    private fun ingest(): UUID {
        val factId = UUID.randomUUID()
        mockMvc.post("/api/v1/commands/facts") {
            with(pipeline)
            contentType = MediaType.APPLICATION_JSON
            content = """{"fact_id":"$factId","subject":{"sha256":"${"2".repeat(64)}"},"governance":{"schema_ref":"urn:schema:ledger-proof:v1"},"tool":{"n":1}}"""
        }.andExpect { status { isAccepted() } }
        return factId
    }

    private fun checkpoint() = mockMvc.post("/api/v1/admin/ledger/checkpoints") { with(admin) }

    private fun proof(factId: UUID): JsonNode =
        mapper.readTree(mockMvc.get("/api/v1/queries/ledger/proof/$factId") { with(auditor) }.andReturn().response.contentAsString)

    private fun keys(): JsonNode =
        mapper.readTree(mockMvc.get("/api/v1/queries/event-signing-keys") { with(auditor) }.andReturn().response.contentAsString)

    private fun hex(s: String) = HexFormat.of().parseHex(s)

    /** Runs the standalone verifier as an auditor would: a separate JVM, no Factstore code. */
    private fun standaloneVerifier(proof: JsonNode, keys: JsonNode): Pair<Int, String> {
        val dir = Files.createTempDirectory("ledger-proof")
        val proofFile = dir.resolve("proof.json").toFile().apply { writeText(mapper.writeValueAsString(proof)) }
        val keysFile = dir.resolve("keys.json").toFile().apply { writeText(mapper.writeValueAsString(keys)) }
        val source = listOf("../tools/ledger-verifier/VerifyLedgerProof.java", "tools/ledger-verifier/VerifyLedgerProof.java")
            .map(::File).first { it.exists() }
        val java = ProcessHandle.current().info().command().orElse("java")
        val process = ProcessBuilder(java, source.path, proofFile.path, keysFile.path).redirectErrorStream(true).start()
        check(process.waitFor(60, TimeUnit.SECONDS)) { "verifier did not finish" }
        return process.exitValue() to process.inputStream.bufferedReader().readText()
    }

    @Test
    fun `#188 a checkpointed fact has an inclusion proof against a signed root`() {
        val factId = ingest()
        checkpoint().andExpect { status { isCreated() } }
        val seq = eventStore.findByAggregateId(factId).single().sequenceNumber

        val proof = proof(factId)

        assertThat(proof["fact_id"].asText()).isEqualTo(factId.toString())
        assertThat(proof["ledger_index"].asLong()).isEqualTo(seq)
        assertThat(proof["raw_signature_verified"].asBoolean()).isTrue()
        val merkle = proof["merkle_proof"]
        val leaf = hex(proof["record"]["record_hash"].asText())
        assertThat(
            MerkleTree.verify(
                leaf, merkle["leaf_index"].asInt(), merkle["tree_size"].asInt(),
                merkle["siblings"].map { hex(it.asText()) }, hex(merkle["root_hash"].asText())
            )
        ).isTrue()
        assertThat(proof["checkpoint"]["statement"].asText()).contains(merkle["root_hash"].asText())
    }

    @Test
    fun `#188 the standalone verifier agrees with the server`() {
        val factId = ingest()
        checkpoint()

        val (exit, output) = standaloneVerifier(proof(factId), keys())

        assertThat(output).contains("VERIFIED")
        assertThat(exit).isZero()
    }

    @Test
    fun `#188 the standalone verifier rejects an altered sibling`() {
        val factId = ingest()
        ingest()
        checkpoint()
        val proof = proof(factId) as ObjectNode
        val siblings = proof["merkle_proof"]["siblings"] as com.fasterxml.jackson.databind.node.ArrayNode
        siblings.set(0, mapper.nodeFactory.textNode("f".repeat(64)))

        val (exit, output) = standaloneVerifier(proof, keys())

        assertThat(exit).isNotZero()
        assertThat(output).contains("FAILED")
    }

    @Test
    fun `#188 the standalone verifier rejects altered record bytes`() {
        val factId = ingest()
        checkpoint()
        val proof = proof(factId) as ObjectNode
        val record = proof["record"] as ObjectNode
        val canonical = String(Base64.getDecoder().decode(record["canonical"].asText()))
        record.put("canonical", Base64.getEncoder().encodeToString(canonical.replace("\"event_type\":\"FactIngested\"", "\"event_type\":\"FactRetracted\"")
            .also { check(it != canonical) }.toByteArray()))

        val (exit, _) = standaloneVerifier(proof, keys())

        assertThat(exit).isNotZero()
    }

    @Test
    fun `#188 a record altered in the database no longer verifies`() {
        val factId = ingest()
        checkpoint()
        val seq = eventStore.findByAggregateId(factId).single().sequenceNumber
        em.flush()
        jdbc.update("UPDATE domain_events SET payload = REPLACE(payload, ?, ?) WHERE sequence_number = ?", "\"n\\\":1", "\"n\\\":9", seq)
        em.clear()

        val proof = proof(factId)

        assertThat(proof["raw_signature_verified"].asBoolean()).isFalse()
        assertThat(standaloneVerifier(proof, keys()).first).isNotZero()
    }

    @Test
    fun `#188 a fact not yet in a checkpoint has no proof yet`() {
        checkpoint()
        val factId = ingest()
        mockMvc.get("/api/v1/queries/ledger/proof/$factId") { with(auditor) }.andExpect {
            status { isNotFound() }
            jsonPath("$.error") { value("proof_pending") }
        }
    }

    @Test
    fun `#188 an unknown fact has no proof`() {
        mockMvc.get("/api/v1/queries/ledger/proof/${UUID.randomUUID()}") { with(auditor) }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `#188 with nothing new to cover, no checkpoint is made`() {
        ingest()
        checkpoint().andExpect { status { isCreated() } }
        checkpoint().andExpect { status { isNoContent() } }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `#188 each signed root is published outside the application`() {
        ingest()
        val root = mapper.readTree(checkpoint().andExpect { status { isCreated() } }.andReturn().response.contentAsString)

        val lines = published.resolve("checkpoints.jsonl").toFile().readLines().map { mapper.readTree(it) }
        assertThat(lines.map { it["root_hash"].asText() }).contains(root["root_hash"].asText())
        assertThat(lines.last()["signature"].asText()).isNotBlank()
    }

    @Test
    fun `#188 proofs need trails read and checkpointing needs admin`() {
        val factId = ingest()
        mockMvc.post("/api/v1/admin/ledger/checkpoints") { with(auditor) }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/queries/ledger/proof/$factId") { with(pipeline) }.andExpect { status { isForbidden() } }
    }
}
