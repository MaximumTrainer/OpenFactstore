package com.factstore.application.signing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * #184 — the bytes a stored event's signature covers. They must be identical on every JVM and
 * every run, whatever map order or timestamp precision or zone the values arrive with, or a
 * record signed today could fail verification tomorrow.
 */
class EventCanonicalFormTest {

    private val record = CanonicalEventRecord(
        sequenceNumber = 42,
        eventId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        aggregateId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        aggregateType = "Fact",
        eventType = "FactIngested",
        occurredAt = Instant.parse("2026-11-10T16:37:33.123456Z"),
        payload = "{\"b\":1,\"a\":\"é\\n\"}\u0001\"/</",
        prevHash = "0".repeat(64)
    )

    @Test
    fun `#184 v1 canonical form is RFC 8785 JSON over the record's fields`() {
        assertThat(String(EventCanonicalForm.v1(record), Charsets.UTF_8)).isEqualTo(
            "{\"aggregate_id\":\"00000000-0000-0000-0000-000000000002\"," +
                "\"aggregate_type\":\"Fact\"," +
                "\"canonical_version\":1," +
                "\"event_id\":\"00000000-0000-0000-0000-000000000001\"," +
                "\"event_type\":\"FactIngested\"," +
                "\"occurred_at\":\"2026-11-10T16:37:33.123456Z\"," +
                "\"payload\":\"{\\\"b\\\":1,\\\"a\\\":\\\"é\\\\n\\\"}\\u0001\\\"/</\"," +
                "\"prev_hash\":\"${"0".repeat(64)}\"," +
                "\"sequence_number\":42}"
        )
    }

    @Test
    fun `#184 the timestamp is canonical whatever its precision or zone`() {
        val nanos = record.copy(occurredAt = Instant.parse("2026-11-10T16:37:33.123456789Z"))
        val offset = record.copy(
            occurredAt = OffsetDateTime.of(2026, 11, 10, 17, 37, 33, 123_456_000, ZoneOffset.ofHours(1)).toInstant()
        )
        assertThat(EventCanonicalForm.v1(nanos)).isEqualTo(EventCanonicalForm.v1(record))
        assertThat(EventCanonicalForm.v1(offset)).isEqualTo(EventCanonicalForm.v1(record))
    }

    @Test
    fun `#184 a whole-second timestamp still carries six fraction digits`() {
        val whole = record.copy(occurredAt = Instant.parse("2026-11-10T16:37:33Z"))
        assertThat(String(EventCanonicalForm.v1(whole))).contains("\"occurred_at\":\"2026-11-10T16:37:33.000000Z\"")
    }

    @Test
    fun `#184 every field is covered`() {
        val base = EventCanonicalForm.v1(record)
        listOf(
            record.copy(sequenceNumber = 43),
            record.copy(eventId = UUID.randomUUID()),
            record.copy(aggregateId = UUID.randomUUID()),
            record.copy(aggregateType = "Flow"),
            record.copy(eventType = "FlowCreated"),
            record.copy(occurredAt = record.occurredAt.plusNanos(1_000)),
            record.copy(payload = record.payload + " "),
            record.copy(prevHash = "1".repeat(64))
        ).forEach { assertThat(EventCanonicalForm.v1(it)).isNotEqualTo(base) }
    }

    @Test
    fun `#184 the record hash is the SHA-256 of the canonical bytes`() {
        assertThat(EventCanonicalForm.hash(EventCanonicalForm.v1(record))).matches("^[0-9a-f]{64}$")
        assertThat(EventCanonicalForm.hash(EventCanonicalForm.v1(record)))
            .isEqualTo(EventCanonicalForm.hash(EventCanonicalForm.v1(record.copy())))
    }
}
