package com.factstore.application.observability

import com.factstore.adapter.mock.InMemoryArtifactRepository
import com.factstore.adapter.mock.InMemoryAttestationRepository
import com.factstore.adapter.mock.InMemoryFactRepository
import com.factstore.adapter.mock.InMemoryFlowRepository
import com.factstore.adapter.mock.InMemoryTrailRepository
import com.factstore.application.ReadModelProjector
import com.factstore.core.domain.event.DomainEvent
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * #192 — in an active-active deployment each region reads from its own projection, which trails
 * the writer. How far it trails is a metric, so read staleness can be alerted on and kept within
 * what "eventually consistent" is allowed to mean.
 */
class ProjectionLagTest {

    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val registry = SimpleMeterRegistry()
    private val lag = ProjectionLag(registry, Clock.fixed(now, ZoneOffset.UTC), "query")
    private val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val projector = ReadModelProjector(
        InMemoryFlowRepository(), InMemoryTrailRepository(), InMemoryArtifactRepository(),
        InMemoryAttestationRepository(), InMemoryFactRepository(), mapper, lag
    )

    private fun flowCreated(occurredAt: Instant) = mapper.writeValueAsString(
        DomainEvent.FlowCreated(aggregateId = UUID.randomUUID(), occurredAt = occurredAt, name = "f", description = "d")
    )

    @Test
    fun `#192 the delay of the last projected event is published`() {
        projector.project("FlowCreated", flowCreated(now.minusSeconds(42)))

        assertThat(registry.get("factstore.projection.delay").tag("service", "query").gauge().value()).isEqualTo(42.0)
        assertThat(registry.get("factstore.projection.last.applied").tag("service", "query").gauge().value())
            .isEqualTo(now.epochSecond.toDouble())
    }

    @Test
    fun `#192 the delay follows the most recent event`() {
        projector.project("FlowCreated", flowCreated(now.minusSeconds(300)))
        projector.project("FlowCreated", flowCreated(now.minusSeconds(2)))

        assertThat(registry.get("factstore.projection.delay").gauge().value()).isEqualTo(2.0)
    }

    @Test
    fun `#192 an event that fails to project does not move the metric`() {
        projector.project("FlowCreated", flowCreated(now.minusSeconds(5)))
        projector.project("FlowCreated", "not json")

        assertThat(registry.get("factstore.projection.delay").gauge().value()).isEqualTo(5.0)
    }
}
