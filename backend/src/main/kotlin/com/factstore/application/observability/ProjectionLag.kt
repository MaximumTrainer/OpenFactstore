package com.factstore.application.observability

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * How far this read model trails the writer (#192).
 *
 * In an active-active deployment every region answers reads from its own projection, which the
 * writer region feeds asynchronously. "Eventually consistent" is only acceptable when the gap is
 * known and bounded, so two gauges are published:
 *
 * - `factstore_projection_delay_seconds`: for the most recently projected event, the time from it
 *   occurring to it being readable here;
 * - `factstore_projection_last_applied_seconds`: when this projection last applied an event, so a
 *   region that has stopped receiving events can be told apart from a quiet one.
 *
 * The alert rules in `monitoring/alerts/factstore.yml` fire on both.
 */
@Component
class ProjectionLag(
    registry: MeterRegistry,
    private val clock: Clock,
    @Value("\${factstore.cqrs.role:all}") role: String
) {
    private data class Last(val delaySeconds: Double, val appliedAt: Instant)

    private val last = AtomicReference<Last?>(null)
    private val service = role.takeIf { it in setOf("command", "query") } ?: "all"

    init {
        Gauge.builder("factstore.projection.delay") { last.get()?.delaySeconds ?: Double.NaN }
            .description("Seconds from the most recently projected event occurring to it being applied to this read model")
            .baseUnit("seconds")
            .tag("service", service)
            .register(registry)
        Gauge.builder("factstore.projection.last.applied") { last.get()?.appliedAt?.epochSecond?.toDouble() ?: Double.NaN }
            .description("When this read model last applied an event, as a Unix timestamp")
            .baseUnit("seconds")
            .tag("service", service)
            .register(registry)
    }

    /**
     * How far behind the writer this read model was when it last caught up (#194): the delay of the
     * last projected event, or 0 where the read model is written directly (an unsplit deployment).
     */
    fun stalenessSeconds(): Double = last.get()?.delaySeconds ?: 0.0

    fun applied(occurredAt: Instant) {
        val now = clock.instant()
        last.set(Last(Duration.between(occurredAt, now).toMillis().coerceAtLeast(0) / 1000.0, now))
    }
}
