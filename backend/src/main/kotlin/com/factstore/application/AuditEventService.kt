package com.factstore.application

import com.factstore.core.domain.AuditEvent
import com.factstore.core.domain.AuditEventType
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.outbound.IAuditEventRepository
import com.factstore.config.RegionContextHolder
import com.factstore.dto.AuditEventPage
import com.factstore.dto.AuditEventResponse
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class AuditEventService(
    private val auditEventRepository: IAuditEventRepository,
    private val objectMapper: ObjectMapper
) : IAuditService {

    private val log = LoggerFactory.getLogger(AuditEventService::class.java)

    override fun record(
        eventType: AuditEventType,
        actor: String,
        payload: Map<String, Any?>,
        trailId: UUID?,
        artifactSha256: String?,
        environmentId: UUID?
    ): AuditEventResponse {
        val event = AuditEvent(
            eventType = eventType,
            actor = actor,
            payload = objectMapper.writeValueAsString(payload),
            trailId = trailId,
            artifactSha256 = artifactSha256,
            environmentId = environmentId,
            region = RegionContextHolder.get()
        )
        val saved = auditEventRepository.save(event)
        log.info("Recorded audit event: ${saved.id} type=${saved.eventType} actor=${saved.actor}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun getEvent(id: UUID): AuditEventResponse =
        (auditEventRepository.findById(id) ?: throw NotFoundException("AuditEvent not found: $id")).toResponse()

    @Transactional(readOnly = true)
    override fun queryEvents(
        eventType: AuditEventType?,
        trailId: UUID?,
        actor: String?,
        from: Instant?,
        to: Instant?,
        page: Int,
        size: Int,
        sortDesc: Boolean
    ): AuditEventPage {
        val events = auditEventRepository.findWithFilters(eventType, trailId, actor, from, to, page, size, sortDesc)
        val total = auditEventRepository.countWithFilters(eventType, trailId, actor, from, to)
        val totalPages = if (size > 0) ((total + size - 1) / size).toInt() else 0
        return AuditEventPage(
            events = events.map { it.toResponse() },
            page = page,
            size = size,
            totalElements = total,
            totalPages = totalPages
        )
    }

    @Transactional(readOnly = true)
    override fun getEventsForTrail(trailId: UUID): List<AuditEventResponse> =
        auditEventRepository.findByTrailId(trailId).map { it.toResponse() }
}

fun AuditEvent.toResponse() = AuditEventResponse(
    id = id,
    eventType = eventType,
    environmentId = environmentId,
    trailId = trailId,
    artifactSha256 = artifactSha256,
    actor = actor,
    payload = payload,
    occurredAt = occurredAt,
    region = region
)
