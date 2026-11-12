package com.factstore.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** #220 — creation times never repeat or go backwards, and follow the clock when it moves on. */
class MonotonicTimeTest {

    @Test
    fun `#220 a frozen clock still yields strictly increasing microsecond times`() {
        val time = MonotonicTime(Clock.fixed(Instant.parse("2026-11-12T09:00:00Z"), ZoneOffset.UTC))
        val values = (1..3).map { time.now() }
        assertThat(values).containsExactly(
            Instant.parse("2026-11-12T09:00:00Z"),
            Instant.parse("2026-11-12T09:00:00.000001Z"),
            Instant.parse("2026-11-12T09:00:00.000002Z")
        )
    }

    @Test
    fun `#220 the clock's own time is used once it has moved on`() {
        var now = Instant.parse("2026-11-12T09:00:00.123456789Z")
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant() = now
        }
        val time = MonotonicTime(clock)
        assertThat(time.now()).isEqualTo(Instant.parse("2026-11-12T09:00:00.123456Z"))
        now = Instant.parse("2026-11-12T09:00:05Z")
        assertThat(time.now()).isEqualTo(Instant.parse("2026-11-12T09:00:05Z"))
    }
}
