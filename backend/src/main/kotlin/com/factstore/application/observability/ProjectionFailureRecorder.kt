package com.factstore.application.observability

import com.factstore.core.domain.EventLogEntry
import com.factstore.core.domain.ProjectionFailure
import com.factstore.core.port.outbound.IProjectionFailureRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Keeps a domain event the projector could not apply, and counts it (#185).
 *
 * A projection failure means the read side no longer matches the event log, so it is counted
 * both as a projection failure and as an integrity failure from the projection.
 */
@Component
class ProjectionFailureRecorder(
    private val repository: IProjectionFailureRepository,
    private val failureMetrics: FailureMetrics
) {
    private val log = LoggerFactory.getLogger(ProjectionFailureRecorder::class.java)

    fun record(entry: EventLogEntry, reason: String) {
        repository.save(
            ProjectionFailure(
                sequenceNumber = entry.sequenceNumber,
                eventType = entry.eventType,
                aggregateId = entry.aggregateId,
                payload = entry.payload,
                reason = reason.take(1000)
            )
        )
        failureMetrics.projectionFailure()
        failureMetrics.integrityFailure(FailureMetrics.IntegritySource.PROJECTION)
        log.error("Projection failed for event type={} seq={}: {}; recorded in projection_failures for replay",
            entry.eventType, entry.sequenceNumber, reason)
    }
}
