package com.factstore.fact

import com.factstore.application.schema.SchemaRegistryService
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
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * #200 — cryptographic shredding. A fact's payload is stored encrypted under a data key for its
 * tenant and month. Destroying that key, with a second administrator's approval, makes those
 * facts unreadable for good, while the signed, hash-chained ledger (#184, #188) still verifies,
 * because it only ever held ciphertext. The destruction certificate is itself a ledger record.
 */
@SpringBootTest(properties = ["security.enforce-auth=true", "factstore.ingest.require-signature=false"])
@AutoConfigureMockMvc
@Transactional
@Import(CryptoShreddingTest.Time::class)
class CryptoShreddingTest {

    /** A clock the test can move between months. */
    class MovableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    @TestConfiguration
    class Time {
        @Bean
        @Primary
        fun movableClock() = MovableClock(Instant.parse("2026-03-15T12:00:00Z"))
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var schemas: SchemaRegistryService
    @Autowired lateinit var facts: IFactRepository
    @Autowired lateinit var eventStore: IEventStore
    @Autowired lateinit var audit: IAuditService
    @Autowired lateinit var clock: MovableClock

    private val mapper = jacksonObjectMapper()
    private val schemaName = "shred-${System.nanoTime()}"
    private val pipeline = user("pipeline").authorities(SimpleGrantedAuthority("SCOPE_attestations:write"), SimpleGrantedAuthority("SCOPE_trails:read"))
    private val alice = user("alice@example.com").authorities(SimpleGrantedAuthority("SCOPE_admin"))
    private val bob = user("bob@example.com").authorities(SimpleGrantedAuthority("SCOPE_admin"))

    @BeforeEach
    fun setUp() {
        clock.now = Instant.parse("2026-03-15T12:00:00Z")
        SecurityContextHolder.clearContext()
        schemas.publish(schemaName, 1, mapper.readTree("""{"type":"object"}"""))
        SecurityContextHolder.clearContext()
    }

    private fun ingest(marker: String): UUID {
        val factId = UUID.randomUUID()
        mockMvc.post("/api/v1/commands/facts") {
            with(pipeline)
            contentType = MediaType.APPLICATION_JSON
            content = """{"fact_id":"$factId","subject":{"sha256":"${"4".repeat(64)}"},"governance":{"schema_ref":"urn:schema:$schemaName:v1"},"tool":{"note":"$marker"}}"""
        }.andExpect { status { isAccepted() } }
        return factId
    }

    private fun requestShred(period: String) =
        mockMvc.post("/api/v1/commands/data-keys/default/$period/shred-requests") { with(alice) }

    private fun shred(period: String): String {
        val request = mapper.readTree(requestShred(period).andExpect { status { isAccepted() } }.andReturn().response.contentAsString)
        return mockMvc.post("/api/v1/commands/data-keys/shred-requests/${request["request_id"].asText()}/approve") { with(bob) }
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
    }

    @Test
    fun `#200 a fact's payload is stored encrypted and read back decrypted`() {
        val factId = ingest("plaintext-marker-1")

        val stored = facts.findById(factId)!!
        assertThat(stored.payloadJson).doesNotContain("plaintext-marker-1").startsWith("enc:v1:")
        assertThat(eventStore.findByAggregateId(factId).single().payload).doesNotContain("plaintext-marker-1")
        mockMvc.get("/api/v1/queries/facts/$factId") { with(pipeline) }.andExpect {
            status { isOk() }
            jsonPath("$.tool.note") { value("plaintext-marker-1") }
        }
    }

    @Test
    fun `#200 shredding needs a second administrator`() {
        ingest("x")
        val request = mapper.readTree(requestShred("2026-03").andExpect {
            status { isAccepted() }
            jsonPath("$.status") { value("PENDING_APPROVAL") }
        }.andReturn().response.contentAsString)

        mockMvc.post("/api/v1/commands/data-keys/shred-requests/${request["request_id"].asText()}/approve") { with(alice) }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `#200 after shredding, the facts are gone but the ledger still verifies`() {
        val factId = ingest("plaintext-marker-2")
        mockMvc.post("/api/v1/admin/ledger/checkpoints") { with(alice) }

        val certificate = mapper.readTree(shred("2026-03"))

        mockMvc.get("/api/v1/queries/facts/$factId") { with(pipeline) }.andExpect {
            status { isGone() }
            jsonPath("$.error") { value("shredded") }
        }
        assertThat(certificate["certificate_hash"].asText()).matches("^[0-9a-f]{64}$")
        val seq = eventStore.findByAggregateId(factId).single().sequenceNumber
        mockMvc.post("/api/v1/admin/events/verify") {
            with(alice)
            contentType = MediaType.APPLICATION_JSON
            content = """{"from":$seq,"to":${certificate["ledger_index"].asLong()}}"""
        }.andExpect { jsonPath("$.valid") { value(true) } }
        mockMvc.get("/api/v1/queries/ledger/proof/$factId") { with(pipeline) }.andExpect {
            status { isOk() }
            jsonPath("$.raw_signature_verified") { value(true) }
        }
    }

    @Test
    fun `#200 other months are untouched`() {
        val march = ingest("march")
        clock.now = Instant.parse("2026-04-02T09:00:00Z")
        val april = ingest("april")

        shred("2026-03")

        mockMvc.get("/api/v1/queries/facts/$march") { with(pipeline) }.andExpect { status { isGone() } }
        mockMvc.get("/api/v1/queries/facts/$april") { with(pipeline) }.andExpect {
            status { isOk() }
            jsonPath("$.tool.note") { value("april") }
        }
    }

    @Test
    fun `#200 the destruction is audited and certified in the ledger`() {
        ingest("y")
        val certificate = mapper.readTree(shred("2026-03"))

        assertThat(audit.queryEvents(eventType = AuditEventType.DATA_KEY_SHRED_REQUESTED).events).anySatisfy {
            assertThat(it.actor).isEqualTo("alice@example.com")
        }
        assertThat(audit.queryEvents(eventType = AuditEventType.DATA_KEY_SHREDDED).events).anySatisfy {
            assertThat(it.actor).isEqualTo("bob@example.com")
            assertThat(it.payload).contains("default").contains("2026-03")
        }
        val record = eventStore.findBySequenceNumbers(listOf(certificate["ledger_index"].asLong())).single()
        assertThat(record.eventType).isEqualTo("DataKeyShredded")
        assertThat(record.recordHash).isEqualTo(certificate["certificate_hash"].asText())
    }

    @Test
    fun `#200 shredding a key that does not exist is not found`() {
        requestShred("1999-01").andExpect { status { isNotFound() } }
    }

    @Test
    fun `#200 shredding is for administrators only`() {
        ingest("z")
        mockMvc.post("/api/v1/commands/data-keys/default/2026-03/shred-requests") { with(pipeline) }
            .andExpect { status { isForbidden() } }
    }
}
