package com.factstore.core.port.inbound

import com.factstore.core.domain.AuditEventType
import com.factstore.dto.AuditEventPage
import com.factstore.dto.AuditEventResponse
import java.time.Instant
import java.util.UUID

interface IAuditService {
    fun record(
        eventType: AuditEventType,
        actor: String,
        payload: Map<String, Any?>,
        trailId: UUID? = null,
        artifactSha256: String? = null,
        environmentId: UUID? = null
    ): AuditEventResponse

    fun getEvent(id: UUID): AuditEventResponse

    fun queryEvents(
        eventType: AuditEventType? = null,
        trailId: UUID? = null,
        actor: String? = null,
        from: Instant? = null,
        to: Instant? = null,
        page: Int = 0,
        size: Int = 20,
        sortDesc: Boolean = true
    ): AuditEventPage

    fun getEventsForTrail(trailId: UUID): List<AuditEventResponse>
}
