package com.factstore.application.signing

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/** The fields of a stored event record that its signature covers (#184). */
data class CanonicalEventRecord(
    val sequenceNumber: Long,
    val eventId: UUID,
    val aggregateId: UUID,
    val aggregateType: String,
    val eventType: String,
    val occurredAt: Instant,
    val payload: String,
    /** The previous signed record's [EventCanonicalForm.hash], linking the records into a chain. */
    val prevHash: String
)

/**
 * The bytes a stored event's signature covers (#184). The encoding is versioned, so it can
 * change without invalidating records signed under an earlier version.
 *
 * **Version 1** is the RFC 8785 (JCS) encoding of one JSON object: `aggregate_id`,
 * `aggregate_type`, `canonical_version` (1), `event_id`, `event_type`, `occurred_at`, `payload`,
 * `prev_hash` and `sequence_number`. Keys are in code-unit order, strings use the ECMAScript
 * escaping JCS prescribes, and the output is UTF-8. Every value is a string except the two
 * integers, so no floating-point formatting is involved. `occurred_at` is UTC, truncated to
 * microseconds (what the database keeps), with exactly six fraction digits.
 */
object EventCanonicalForm {
    const val V1 = 1
    val GENESIS_HASH = "0".repeat(64)

    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC)

    fun v1(record: CanonicalEventRecord): ByteArray {
        val fields = sortedMapOf<String, Any>(
            "aggregate_id" to record.aggregateId.toString(),
            "aggregate_type" to record.aggregateType,
            "canonical_version" to V1.toLong(),
            "event_id" to record.eventId.toString(),
            "event_type" to record.eventType,
            "occurred_at" to TIMESTAMP.format(record.occurredAt.truncatedTo(ChronoUnit.MICROS)),
            "payload" to record.payload,
            "prev_hash" to record.prevHash,
            "sequence_number" to record.sequenceNumber
        )
        val out = StringBuilder("{")
        fields.entries.forEachIndexed { i, (key, value) ->
            if (i > 0) out.append(',')
            appendString(out, key)
            out.append(':')
            if (value is Long) out.append(value) else appendString(out, value as String)
        }
        return out.append('}').toString().toByteArray(Charsets.UTF_8)
    }

    /** Lower-case hex SHA-256 of [bytes]: a record's hash, which the next record links to. */
    fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** JSON string serialisation as RFC 8785 section 3.2.2.2 requires. */
    private fun appendString(out: StringBuilder, value: String) {
        out.append('"')
        for (c in value) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        out.append('"')
    }
}
