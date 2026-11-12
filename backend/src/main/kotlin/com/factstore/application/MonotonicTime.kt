package com.factstore.application

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Creation times that are strictly increasing within this process, at the database's microsecond
 * precision (#220).
 *
 * Two records created in the same clock tick (common on a coarse Windows clock, and always under a
 * frozen test clock) would otherwise share a timestamp, and "the most recent" would be arbitrary.
 * Each call returns the clock's time, or one microsecond after the previous value if the clock has
 * not moved on.
 */
@Component
class MonotonicTime(private val clock: Clock) {
    private var last: Instant = Instant.EPOCH

    @Synchronized
    fun now(): Instant {
        val candidate = clock.instant().truncatedTo(ChronoUnit.MICROS)
        last = if (candidate.isAfter(last)) candidate else last.plus(1, ChronoUnit.MICROS)
        return last
    }
}
