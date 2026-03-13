package com.factstore

import com.factstore.adapter.outbound.LocalHashChainLedger
import com.factstore.application.LedgerService
import com.factstore.config.LedgerProperties
import com.factstore.core.domain.LedgerFact
import com.factstore.dto.VerifyChainRequest
import com.factstore.exception.NotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class LedgerServiceTest {

    private lateinit var properties: LedgerProperties
    private lateinit var ledger: LocalHashChainLedger
    private lateinit var service: LedgerService

    @BeforeEach
    fun setUp() {
        properties = LedgerProperties(enabled = true, type = "local")
        ledger = LocalHashChainLedger(properties)
        service = LedgerService(ledger, properties)
    }

    @Test
    fun `record a fact and retrieve the ledger entry`() {
        val factId = UUID.randomUUID()
        ledger.recordFact(LedgerFact(factId, "ATTESTATION_RECORDED", """{"type":"junit","status":"PASSED"}"""))

        val entry = service.getEntry(factId)

        assertEquals(factId, entry.factId)
        assertEquals("ATTESTATION_RECORDED", entry.eventType)
        assertNotNull(entry.contentHash)
        assertTrue(entry.contentHash.length == 64) // SHA-256 hex
    }

    @Test
    fun `verify a recorded fact returns verified true`() {
        val factId = UUID.randomUUID()
        ledger.recordFact(LedgerFact(factId, "ATTESTATION_RECORDED", """{"type":"snyk","status":"PASSED"}"""))

        val result = service.verifyFact(factId)

        assertTrue(result.verified)
        assertEquals(factId, result.factId)
        assertNotNull(result.contentHash)
        assertEquals(0, result.chainPosition)
        assertNotNull(result.ledgerTimestamp)
    }

    @Test
    fun `verify an unknown fact returns verified false`() {
        val unknownId = UUID.randomUUID()

        val result = service.verifyFact(unknownId)

        assertFalse(result.verified)
        assertNull(result.contentHash)
        assertNull(result.chainPosition)
        assertTrue(result.message.contains("No ledger entry found"))
    }

    @Test
    fun `getEntry for unknown fact throws NotFoundException`() {
        assertThrows<NotFoundException> {
            service.getEntry(UUID.randomUUID())
        }
    }

    @Test
    fun `hash chain links entries correctly`() {
        val factId1 = UUID.randomUUID()
        val factId2 = UUID.randomUUID()
        ledger.recordFact(LedgerFact(factId1, "TRAIL_CREATED", """{"trailId":"abc"}"""))
        ledger.recordFact(LedgerFact(factId2, "ATTESTATION_RECORDED", """{"type":"junit","status":"PASSED"}"""))

        val entry1 = service.getEntry(factId1)
        val entry2 = service.getEntry(factId2)

        // The genesis entry always has the zero hash as previousHash
        assertEquals("0000000000000000000000000000000000000000000000000000000000000000", entry1.previousHash)
        // The second entry's previousHash must differ from the genesis hash
        assertTrue(entry2.previousHash != entry1.previousHash)
        // Both entries must have valid 64-char hex hashes
        assertTrue(entry2.previousHash.length == 64)
    }

    @Test
    fun `listEntries returns paginated results`() {
        repeat(5) {
            ledger.recordFact(LedgerFact(UUID.randomUUID(), "TRAIL_CREATED", """{"index":$it}"""))
        }

        val page0 = service.listEntries(0, 3)
        val page1 = service.listEntries(1, 3)

        assertEquals(3, page0.entries.size)
        assertEquals(2, page1.entries.size)
        assertEquals(5L, page0.totalElements)
        assertEquals(2, page0.totalPages)
    }

    @Test
    fun `verifyChain returns valid for all entries in range`() {
        val before = Instant.now().minusSeconds(1)
        val factId1 = UUID.randomUUID()
        val factId2 = UUID.randomUUID()
        ledger.recordFact(LedgerFact(factId1, "TRAIL_CREATED", """{"trailId":"x"}"""))
        ledger.recordFact(LedgerFact(factId2, "ATTESTATION_RECORDED", """{"type":"junit"}"""))
        val after = Instant.now().plusSeconds(1)

        val result = service.verifyChain(VerifyChainRequest(from = before, to = after))

        assertTrue(result.valid)
        assertEquals(2, result.entriesChecked)
        assertNull(result.brokenAt)
    }

    @Test
    fun `getStatus returns correct enabled state and entry count`() {
        ledger.recordFact(LedgerFact(UUID.randomUUID(), "ATTESTATION_RECORDED", """{"type":"junit"}"""))

        val status = service.getStatus()

        assertTrue(status.enabled)
        assertEquals("local", status.type)
        assertEquals(1L, status.totalEntries)
        assertTrue(status.healthy)
    }

    @Test
    fun `getHistory returns all entries for a factId`() {
        val factId = UUID.randomUUID()
        ledger.recordFact(LedgerFact(factId, "ATTESTATION_RECORDED", """{"type":"junit","status":"PASSED"}"""))
        ledger.recordFact(LedgerFact(factId, "ATTESTATION_UPDATED", """{"type":"junit","status":"FAILED"}"""))

        val entries = ledger.getHistory(factId)

        assertEquals(2, entries.size)
        assertEquals("ATTESTATION_RECORDED", entries[0].eventType)
        assertEquals("ATTESTATION_UPDATED", entries[1].eventType)
    }

    @Test
    fun `verifyChain with no entries in range reports zero checked`() {
        val distantPast = Instant.parse("2000-01-01T00:00:00Z")
        val stillDistantPast = Instant.parse("2000-01-02T00:00:00Z")

        val result = service.verifyChain(VerifyChainRequest(from = distantPast, to = stillDistantPast))

        assertTrue(result.valid)
        assertEquals(0, result.entriesChecked)
    }
}
