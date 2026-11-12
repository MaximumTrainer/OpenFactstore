package com.factstore.application

import com.factstore.core.domain.AuditEvent
import com.factstore.core.domain.AuditEventType
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.outbound.IAuditEventRepository
import com.factstore.core.port.outbound.IEnvironmentRepository
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
    private val environmentRepository: IEnvironmentRepository,
    private val objectMapper: ObjectMapper,
    private val tenantScope: TenantScope = TenantScope.UNSCOPED,
    private val ownership: OrgOwnership? = null,
    private val siemStream: com.factstore.application.siem.SiemStream? = null
) : IAuditService {

    private val log = LoggerFactory.getLogger(AuditEventService::class.java)

    /**
     * An audit event belongs to its trail's organisation, else its environment's. One tied to
     * neither (a key created, a failed sign-in) is visible only to unbound callers (#207).
     */
    private fun orgOf(event: com.factstore.core.domain.AuditEvent): String? =
        ownership?.ofTrail(event.trailId) ?: ownership?.ofEnvironment(event.environmentId)

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
        // Streamed to the SIEM once committed (#199).
        siemStream?.auditRecorded(saved)
        log.info("Recorded audit event: ${saved.id} type=${saved.eventType} actor=${saved.actor}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun getEvent(id: UUID): AuditEventResponse =
        tenantScope.require(auditEventRepository.findById(id), ::orgOf) { "AuditEvent not found: $id" }.toResponse()

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
        if (tenantScope.orgSlug() != null) {
            // Filtered before paging so a bound caller's page counts are its own. Bounded window.
            val visible = auditEventRepository
                .findWithFilters(eventType, trailId, actor, from, to, 0, SCOPED_WINDOW, sortDesc)
                .filter { tenantScope.visible(orgOf(it)) }
            val totalPages = if (size > 0) (visible.size + size - 1) / size else 0
            return AuditEventPage(
                events = visible.drop(page * size).take(size).map { it.toResponse() },
                page = page,
                size = size,
                totalElements = visible.size.toLong(),
                totalPages = totalPages
            )
        }
        val events = auditEventRepository.findWithFilters(eventType, trailId, actor, from, to, page, size, sortDesc)
        tenantScope.observe(events.map(::orgOf))
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
        auditEventRepository.findByTrailId(trailId).filter { tenantScope.visible(orgOf(it)) }.map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun exportEnvironmentAuditLogCsv(environmentId: UUID): String {
        tenantScope.require(environmentRepository.findById(environmentId), { it.orgSlug }) {
            "Environment not found: $environmentId"
        }
        val events = auditEventRepository.findByEnvironmentId(environmentId)
        val sb = StringBuilder()
        sb.appendLine("timestamp,eventType,actor,details,environmentId,resourceId")
        for (event in events) {
            val timestamp = event.occurredAt.toString()
            val eventType = event.eventType.name
            val actor = escapeCsvField(event.actor)
            val details = escapeCsvField(event.payload)
            val envId = event.environmentId?.toString() ?: ""
            val resourceId = event.trailId?.toString() ?: event.artifactSha256 ?: ""
            sb.appendLine("$timestamp,$eventType,$actor,$details,$envId,$resourceId")
        }
        return sb.toString()
    }

    private fun escapeCsvField(value: String): String {
        val needsQuoting = value.contains(',') || value.contains('"') || value.contains('\n')
        return if (needsQuoting) "\"${value.replace("\"", "\"\"")}\"" else value
    }

    private companion object {
        /** How many matching events a bound caller's query considers. */
        const val SCOPED_WINDOW = 10_000
    }
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
