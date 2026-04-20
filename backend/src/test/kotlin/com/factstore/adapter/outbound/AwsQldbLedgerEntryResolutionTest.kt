package com.factstore.adapter.outbound

import com.amazon.ion.system.IonSystemBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Smoke-tests the IonStruct entry-resolution helpers in [AwsQldbLedger] without
 * requiring a live QLDB connection.
 */
class AwsQldbLedgerEntryResolutionTest {

    private val ionSystem = IonSystemBuilder.standard().build()

    // ── resolveEntryId ────────────────────────────────────────────────────────

    @Test
    fun `resolveEntryId returns explicit entryId when present`() {
        val struct = ionSystem.newEmptyStruct().apply {
            put("entryId", ionSystem.newString("explicit-entry-id"))
        }
        assertEquals("explicit-entry-id", AwsQldbLedger.resolveEntryId(struct))
    }

    @Test
    fun `resolveEntryId returns empty string when entryId is absent`() {
        val struct = ionSystem.newEmptyStruct()
        assertEquals("", AwsQldbLedger.resolveEntryId(struct))
    }

    @Test
    fun `resolveEntryId returns empty string when entryId is not an IonText`() {
        val struct = ionSystem.newEmptyStruct().apply {
            put("entryId", ionSystem.newInt(42))
        }
        assertEquals("", AwsQldbLedger.resolveEntryId(struct))
    }

    // ── resolveTimestamp ──────────────────────────────────────────────────────

    @Test
    fun `resolveTimestamp prefers txTime over createdAt`() {
        val txTime = "2024-01-15T10:00:00Z"
        val createdAt = "2024-01-15T09:00:00Z"
        assertEquals(Instant.parse(txTime), AwsQldbLedger.resolveTimestamp(txTimeStr = txTime, createdAtStr = createdAt))
    }

    @Test
    fun `resolveTimestamp falls back to createdAt when txTime is null`() {
        val createdAt = "2024-03-01T08:30:00Z"
        assertEquals(Instant.parse(createdAt), AwsQldbLedger.resolveTimestamp(txTimeStr = null, createdAtStr = createdAt))
    }

    @Test
    fun `resolveTimestamp falls back to createdAt when txTime is unparseable`() {
        val createdAt = "2024-03-01T08:30:00Z"
        assertEquals(Instant.parse(createdAt), AwsQldbLedger.resolveTimestamp(txTimeStr = "not-a-date", createdAtStr = createdAt))
    }

    @Test
    fun `resolveTimestamp returns a non-null Instant when both timestamps are null`() {
        val before = Instant.now()
        val result = AwsQldbLedger.resolveTimestamp(txTimeStr = null, createdAtStr = null)
        val after = Instant.now()
        assertNotNull(result)
        assertFalse(result.isBefore(before))
        assertFalse(result.isAfter(after))
    }

    @Test
    fun `resolveTimestamp returns a non-null Instant when both timestamps are unparseable`() {
        val result = AwsQldbLedger.resolveTimestamp(txTimeStr = "bad", createdAtStr = "also-bad")
        assertNotNull(result)
    }
}
