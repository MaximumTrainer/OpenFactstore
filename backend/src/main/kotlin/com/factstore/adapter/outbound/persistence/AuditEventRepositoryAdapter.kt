package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.AuditEvent
import com.factstore.core.domain.AuditEventType
import com.factstore.core.port.outbound.IAuditEventRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface AuditEventRepositoryJpa : JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {
    fun findByTrailIdOrderByOccurredAtDesc(trailId: UUID): List<AuditEvent>
}

@Component
class AuditEventRepositoryAdapter(private val jpa: AuditEventRepositoryJpa) : IAuditEventRepository {

    override fun save(event: AuditEvent): AuditEvent = jpa.save(event)

    override fun findById(id: UUID): AuditEvent? = jpa.findById(id).orElse(null)

    override fun findByTrailId(trailId: UUID): List<AuditEvent> = jpa.findByTrailIdOrderByOccurredAtDesc(trailId)

    override fun findWithFilters(
        eventType: AuditEventType?,
        trailId: UUID?,
        actor: String?,
        from: Instant?,
        to: Instant?,
        page: Int,
        size: Int,
        sortDesc: Boolean
    ): List<AuditEvent> {
        val sort = if (sortDesc) Sort.by("occurredAt").descending() else Sort.by("occurredAt").ascending()
        val pageable = PageRequest.of(page, size, sort)
        return jpa.findAll(buildSpec(eventType, trailId, actor, from, to), pageable).content
    }

    override fun countWithFilters(
        eventType: AuditEventType?,
        trailId: UUID?,
        actor: String?,
        from: Instant?,
        to: Instant?
    ): Long = jpa.count(buildSpec(eventType, trailId, actor, from, to))

    private fun buildSpec(
        eventType: AuditEventType?,
        trailId: UUID?,
        actor: String?,
        from: Instant?,
        to: Instant?
    ): Specification<AuditEvent> = Specification { root, _, cb ->
        val predicates = mutableListOf<Predicate>()
        eventType?.let { predicates.add(cb.equal(root.get<AuditEventType>("eventType"), it)) }
        trailId?.let { predicates.add(cb.equal(root.get<UUID>("trailId"), it)) }
        actor?.let { predicates.add(cb.equal(root.get<String>("actor"), it)) }
        from?.let { predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), it)) }
        to?.let { predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), it)) }
        cb.and(*predicates.toTypedArray())
    }
}
