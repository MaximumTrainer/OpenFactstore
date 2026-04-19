package com.factstore.core.domain.event

/**
 * Single source of truth for the mapping between domain event type names
 * (as stored in [com.factstore.core.domain.EventLogEntry.eventType]) and
 * their concrete [DomainEvent] classes.
 *
 * Both [com.factstore.application.EventProjector] (replay) and
 * [com.factstore.application.ReadModelProjector] (live projection)
 * reference this registry so that new event types only need to be
 * registered in one place.
 */
object DomainEventRegistry {

    val eventTypeMap: Map<String, Class<out DomainEvent>> = mapOf(
        "FlowCreated" to DomainEvent.FlowCreated::class.java,
        "FlowUpdated" to DomainEvent.FlowUpdated::class.java,
        "FlowDeleted" to DomainEvent.FlowDeleted::class.java,
        "TrailCreated" to DomainEvent.TrailCreated::class.java,
        "ArtifactReported" to DomainEvent.ArtifactReported::class.java,
        "AttestationRecorded" to DomainEvent.AttestationRecorded::class.java,
        "EvidenceUploaded" to DomainEvent.EvidenceUploaded::class.java
    )
}
