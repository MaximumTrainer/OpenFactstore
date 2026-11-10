package com.factstore.benchmark

import com.factstore.application.signing.EventRecordSealer
import com.factstore.core.domain.EventLogEntry
import com.factstore.core.port.outbound.IEventStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate
import java.io.File
import java.util.UUID

/**
 * #184 — what signing a stored event adds to ingest latency.
 *
 * Each sample appends one event in its own transaction, as a command does. The baseline is a plain
 * insert; the signed path locks the chain head, inserts, builds the canonical bytes, signs and
 * stores the signature. The two are interleaved so both see the same JVM conditions. The
 * difference is the cost of signing.
 *
 * The signer here is the in-memory development signer, so the figures exclude the network round
 * trip to Vault Transit, which dominates in production. Measure that against the target Vault
 * before agreeing the budget; see `docs/runbooks/event-signing-keys.md`.
 *
 * Not part of `./gradlew test`: run `./gradlew eventSigningBenchmark`, which writes
 * `build/reports/event-signing-benchmark.json`.
 */
@Tag("benchmark")
@SpringBootTest
class EventSigningBenchmark {

    @Autowired lateinit var eventStore: IEventStore
    @Autowired lateinit var sealer: EventRecordSealer
    @Autowired lateinit var tx: TransactionTemplate

    private fun entry() = EventLogEntry(
        aggregateId = UUID.randomUUID(),
        aggregateType = "Fact",
        eventType = "FactIngested",
        payload = """{"tool":{"passed":120,"failed":0},"subject":{"sha256":"${"a".repeat(64)}"}}"""
    )

    private fun timed(block: () -> Unit): Long {
        val start = System.nanoTime()
        tx.executeWithoutResult { block() }
        return System.nanoTime() - start
    }

    private fun percentile(sortedNanos: LongArray, p: Double): Double =
        sortedNanos[((sortedNanos.size - 1) * p).toInt()] / 1_000_000.0

    @Test
    fun `measure the latency signing adds to an event append`() {
        repeat(WARMUP) {
            timed { eventStore.append(entry()) }
            timed { sealer.appendSealed(entry()) }
        }
        val plain = LongArray(SAMPLES)
        val signed = LongArray(SAMPLES)
        for (i in 0 until SAMPLES) {
            plain[i] = timed { eventStore.append(entry()) }
            signed[i] = timed { sealer.appendSealed(entry()) }
        }
        plain.sort()
        signed.sort()

        val report = """
            {
              "samples": $SAMPLES,
              "signer": "local (in-memory Ed25519; excludes the Vault round trip)",
              "unsignedMs": {"p50": ${"%.3f".format(percentile(plain, 0.50))}, "p99": ${"%.3f".format(percentile(plain, 0.99))}},
              "signedMs": {"p50": ${"%.3f".format(percentile(signed, 0.50))}, "p99": ${"%.3f".format(percentile(signed, 0.99))}},
              "addedMs": {"p50": ${"%.3f".format(percentile(signed, 0.50) - percentile(plain, 0.50))}, "p99": ${"%.3f".format(percentile(signed, 0.99) - percentile(plain, 0.99))}}
            }
        """.trimIndent()
        val out = File(System.getProperty("factstore.benchmark.out") ?: "build/reports/event-signing-benchmark.json")
        out.parentFile.mkdirs()
        out.writeText(report)
        println(report)
        assertThat(signed.first()).isPositive()
    }

    companion object {
        private const val WARMUP = 200
        private const val SAMPLES = 1000
    }
}
