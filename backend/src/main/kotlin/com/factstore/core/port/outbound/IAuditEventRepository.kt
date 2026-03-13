package com.factstore.core.port.outbound

import com.factstore.core.domain.AuditEvent
import com.factstore.core.domain.AuditEventType
import java.time.Instant
import java.util.UUID

interface IAuditEventRepository {
    fun save(event: AuditEvent): AuditEvent
    fun findById(id: UUID): AuditEvent?
    fun findByTrailId(trailId: UUID): List<AuditEvent>
    fun findWithFilters(
        eventType: AuditEventType? = null,
        trailId: UUID? = null,
        actor: String? = null,
        from: Instant? = null,
        to: Instant? = null,
        page: Int = 0,
        size: Int = 20,
        sortDesc: Boolean = true
    ): List<AuditEvent>
    fun countWithFilters(
        eventType: AuditEventType? = null,
        trailId: UUID? = null,
        actor: String? = null,
        from: Instant? = null,
        to: Instant? = null
    ): Long
}
