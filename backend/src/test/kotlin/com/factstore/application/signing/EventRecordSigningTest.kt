package com.factstore.application.signing

import com.factstore.application.command.EventAppender
import com.factstore.application.schema.SchemaRegistryService
import com.factstore.core.domain.event.DomainEvent
import com.factstore.core.port.outbound.IEventStore
import com.factstore.core.port.outbound.IEventRecordSigner
import com.factstore.core.port.outbound.IFactRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * #184 — every stored event record is signed, in the same transaction as its insert, by a key
 * the application cannot read, over a versioned canonical encoding that also links it to the
 * record before it. Verification recomputes each record and reports the first one that fails.
 */
@SpringBootTest(properties = ["security.enforce-auth=true", "factstore.ingest.require-signature=false"])
@AutoConfigureMockMvc
@Transactional
@Import(EventRecordSigningTest.Signer::class)
class EventRecordSigningTest {

    @TestConfiguration
    class Signer {
        @Bean
        @Primary
        fun fakeEventSigner(): FakeEventSigner = FakeEventSigner()
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var appender: EventAppender
    @Autowired lateinit var eventStore: IEventStore
    @Autowired lateinit var signer: IEventRecordSigner
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var schemas: SchemaRegistryService
    @Autowired lateinit var facts: IFactRepository
    @Autowired lateinit var meters: MeterRegistry
    @Autowired lateinit var em: jakarta.persistence.EntityManager

    private val fake get() = signer as FakeEventSigner
    private val admin = user("security@example.com").authorities(SimpleGrantedAuthority("SCOPE_admin"))
    private val pipeline = user("pipeline").authorities(SimpleGrantedAuthority("SCOPE_attestations:write"))

    @BeforeEach
    fun clear() = SecurityContextHolder.clearContext()

    private fun appendEvents(count: Int): List<Long> = (1..count).map {
        val id = UUID.randomUUID()
        appender.append(DomainEvent.FlowCreated(aggregateId = id, name = "flow-$id", description = "d $it"))
        eventStore.findByAggregateId(id).single().sequenceNumber
    }

    private fun verify(from: Long, to: Long): ResultActionsDsl = mockMvc.post("/api/v1/admin/events/verify") {
        with(admin)
        contentType = MediaType.APPLICATION_JSON
        content = """{"from":$from,"to":$to}"""
    }

    /**
     * Changes the database behind the application's back, as someone with database access could,
     * and drops the persistence context so the application reads what is now stored.
     */
    private fun tamper(sql: String, vararg args: Any?) {
        em.flush()
        jdbc.update(sql, *args)
        em.clear()
    }

    private fun integrityFailures() = meters.find("factstore.integrity.failures").counters().sumOf { it.count() }

    @Test
    fun `#184 a stored event carries its signature, key id, algorithm and canonical version`() {
        val seq = appendEvents(1).single()

        val row = jdbc.queryForMap(
            "SELECT signature, signature_algorithm, signing_key_id, canonical_version, prev_hash, record_hash, signature_status " +
                "FROM domain_events WHERE sequence_number = ?", seq
        )
        assertThat(row["SIGNATURE"] as String).isNotBlank()
        assertThat(row["SIGNATURE_ALGORITHM"]).isEqualTo("Ed25519")
        assertThat(row["SIGNING_KEY_ID"]).isEqualTo(fake.publicKeys().last().keyId)
        assertThat(row["CANONICAL_VERSION"]).isEqualTo(1)
        assertThat(row["PREV_HASH"] as String).matches("^[0-9a-f]{64}$")
        assertThat(row["RECORD_HASH"] as String).matches("^[0-9a-f]{64}$")
        assertThat(row["SIGNATURE_STATUS"]).isEqualTo("SIGNED")
    }

    @Test
    fun `#184 each record is linked to the one before it`() {
        val (first, second) = appendEvents(2)
        val firstHash = jdbc.queryForObject("SELECT record_hash FROM domain_events WHERE sequence_number = ?", String::class.java, first)
        val secondPrev = jdbc.queryForObject("SELECT prev_hash FROM domain_events WHERE sequence_number = ?", String::class.java, second)
        assertThat(secondPrev).isEqualTo(firstHash)
    }

    @Test
    fun `#184 verification passes over untouched records and counts them`() {
        val seqs = appendEvents(3)
        verify(seqs.first(), seqs.last()).andExpect {
            status { isOk() }
            jsonPath("$.valid") { value(true) }
            jsonPath("$.checked") { value(3) }
            jsonPath("$.firstFailure") { doesNotExist() }
        }
    }

    @Test
    fun `#184 a changed payload is detected at its sequence number`() {
        val seqs = appendEvents(3)
        tamper("UPDATE domain_events SET payload = ? WHERE sequence_number = ?", "{\"forged\":true}", seqs[1])
        val before = integrityFailures()

        verify(seqs.first(), seqs.last()).andExpect {
            jsonPath("$.valid") { value(false) }
            jsonPath("$.firstFailure.sequenceNumber") { value(seqs[1]) }
        }
        assertThat(integrityFailures()).isGreaterThan(before)
    }

    @Test
    fun `#184 a changed occurred_at is detected`() {
        val seqs = appendEvents(2)
        tamper("UPDATE domain_events SET occurred_at = occurred_at + INTERVAL '1' SECOND WHERE sequence_number = ?", seqs[0])
        verify(seqs.first(), seqs.last()).andExpect {
            jsonPath("$.valid") { value(false) }
            jsonPath("$.firstFailure.sequenceNumber") { value(seqs[0]) }
        }
    }

    @Test
    fun `#184 a changed sequence number is detected`() {
        val seqs = appendEvents(3)
        val moved = seqs[1] + 1_000_000
        moveRow(seqs[1], moved)
        verify(seqs.first(), moved).andExpect {
            jsonPath("$.valid") { value(false) }
            jsonPath("$.firstFailure.sequenceNumber") { value(seqs[2]) }
        }
    }

    @Test
    fun `#184 a deleted record is detected at the record after it`() {
        val seqs = appendEvents(3)
        tamper("DELETE FROM domain_events WHERE sequence_number = ?", seqs[1])
        verify(seqs.first(), seqs.last()).andExpect {
            jsonPath("$.valid") { value(false) }
            jsonPath("$.firstFailure.sequenceNumber") { value(seqs[2]) }
        }
    }

    @Test
    fun `#184 deleting the newest record is detected against the chain head`() {
        val seqs = appendEvents(2)
        tamper("DELETE FROM domain_events WHERE sequence_number = ?", seqs[1])
        verify(seqs.first(), seqs.last()).andExpect {
            jsonPath("$.valid") { value(false) }
        }
    }

    @Test
    fun `#184 records signed before a key rotation still verify, and are not re-signed`() {
        val before = appendEvents(2)
        val oldSignature = jdbc.queryForObject("SELECT signature FROM domain_events WHERE sequence_number = ?", String::class.java, before[0])

        val newKey = fake.rotate()
        val after = appendEvents(2)

        assertThat(jdbc.queryForObject("SELECT signing_key_id FROM domain_events WHERE sequence_number = ?", String::class.java, after[0]))
            .isEqualTo(newKey)
        assertThat(jdbc.queryForObject("SELECT signature FROM domain_events WHERE sequence_number = ?", String::class.java, before[0]))
            .isEqualTo(oldSignature)
        verify(before.first(), after.last()).andExpect { jsonPath("$.valid") { value(true) } }
    }

    @Test
    // Outside the test transaction, so the command's own rollback is what is observed.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    fun `#184 if signing fails the command fails and nothing is written`() {
        SecurityContextHolder.clearContext()
        schemas.publish("signing-fail", 1, jacksonObjectMapper().readTree("""{"type":"object"}"""))
        SecurityContextHolder.clearContext()
        val factId = UUID.randomUUID()
        fake.failNextSign = true

        mockMvc.post("/api/v1/commands/facts") {
            with(pipeline)
            contentType = MediaType.APPLICATION_JSON
            content = """{"fact_id":"$factId","subject":{"sha256":"${"1".repeat(64)}"},"governance":{"schema_ref":"urn:schema:signing-fail:v1"},"tool":{}}"""
        }.andExpect { status { is5xxServerError() } }

        assertThat(facts.findById(factId)).isNull()
        assertThat(eventStore.findByAggregateId(factId)).isEmpty()
    }

    @Test
    fun `#184 public keys are published by key id with validity windows`() {
        val newKey = fake.rotate()
        mockMvc.get("/api/v1/queries/event-signing-keys") { with(pipeline) }.andExpect {
            status { isOk() }
            jsonPath("$[0].keyId") { value("fake-events:v1") }
            jsonPath("$[0].algorithm") { value("Ed25519") }
            jsonPath("$[0].validTo") { exists() }
            jsonPath("$[?(@.keyId == '$newKey')].publicKey") { exists() }
        }
    }

    @Test
    fun `#184 records stored before signing are flagged unsigned and counted, not failed`() {
        val seq = appendEvents(1).single()
        jdbc.update(
            "INSERT INTO domain_events (event_id, aggregate_id, aggregate_type, event_type, payload, occurred_at) " +
                "VALUES (?, ?, 'Flow', 'FlowCreated', '{}', CURRENT_TIMESTAMP)",
            UUID.randomUUID(), UUID.randomUUID()
        )
        val unsignedSeq = jdbc.queryForObject("SELECT MAX(sequence_number) FROM domain_events", Long::class.java)!!
        assertThat(jdbc.queryForObject("SELECT signature_status FROM domain_events WHERE sequence_number = ?", String::class.java, unsignedSeq))
            .isEqualTo("UNSIGNED")

        verify(seq, seq).andExpect { jsonPath("$.valid") { value(true) } }
    }

    @Test
    fun `#184 verification is restricted to administrators`() {
        mockMvc.post("/api/v1/admin/events/verify") {
            with(pipeline)
            contentType = MediaType.APPLICATION_JSON
            content = """{"from":1,"to":2}"""
        }.andExpect { status { isForbidden() } }
    }

    /** Re-inserts a row under another sequence number, as someone with database access could. */
    private fun moveRow(from: Long, to: Long) {
        em.flush()
        val row = jdbc.queryForMap("SELECT * FROM domain_events WHERE sequence_number = ?", from)
        val columns = row.keys.filter { it != "SEQUENCE_NUMBER" }
        jdbc.update("DELETE FROM domain_events WHERE sequence_number = ?", from)
        tamper(
            "INSERT INTO domain_events (sequence_number, ${columns.joinToString()}) OVERRIDING SYSTEM VALUE " +
                "VALUES (?, ${columns.joinToString { "?" }})",
            to, *columns.map { row[it] as Any? }.toTypedArray<Any?>()
        )
    }
}
