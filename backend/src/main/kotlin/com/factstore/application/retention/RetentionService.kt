package com.factstore.application.retention

import com.factstore.application.ActorResolver
import com.factstore.application.shredding.ShreddingService
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.DataKeyStatus
import com.factstore.core.domain.LegalHold
import com.factstore.core.domain.LegalHoldScope
import com.factstore.core.domain.RetentionAction
import com.factstore.core.domain.RetentionDataClass
import com.factstore.core.domain.RetentionPolicy
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.inbound.IRetentionService
import com.factstore.core.port.outbound.IDataKeyRepository
import com.factstore.core.port.outbound.ILegalHoldRepository
import com.factstore.core.port.outbound.IOrganisationRepository
import com.factstore.core.port.outbound.IRetentionArchive
import com.factstore.core.port.outbound.IRetentionPolicyRepository
import com.factstore.core.port.outbound.IRetentionStore
import com.factstore.core.port.outbound.RetainedSnapshot
import com.factstore.dto.LegalHoldResponse
import com.factstore.dto.PlaceLegalHoldRequest
import com.factstore.dto.RetentionBatch
import com.factstore.dto.RetentionDataClassResponse
import com.factstore.dto.RetentionPolicyResponse
import com.factstore.dto.RetentionRunReport
import com.factstore.exception.BadRequestException
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

/**
 * Data retention (#251). Each organisation sets, per data class, how long data is kept; the job
 * then shreds, archives or prunes what is past its period. It never touches a ledger record or an
 * audit event, skips anything connected to a trail or flow under legal hold, audits each batch it
 * applies, and counts what it did in `factstore.retention.items`.
 */
@Service
@Transactional
class RetentionService(
    private val policies: IRetentionPolicyRepository,
    private val holds: ILegalHoldRepository,
    private val store: IRetentionStore,
    private val archive: IRetentionArchive,
    private val dataKeys: IDataKeyRepository,
    private val organisations: IOrganisationRepository,
    private val shredding: ShreddingService,
    private val auditService: IAuditService,
    private val actorResolver: ActorResolver,
    private val objectMapper: ObjectMapper,
    private val meters: MeterRegistry,
    private val clock: Clock
) : IRetentionService {

    private val log = LoggerFactory.getLogger(RetentionService::class.java)

    // --- policies ---------------------------------------------------------------------

    override fun dataClasses(): List<RetentionDataClassResponse> =
        RetentionDataClass.entries.map { RetentionDataClassResponse(it, it.action, it.minimumDays) }

    @Transactional(readOnly = true)
    override fun listPolicies(): List<RetentionPolicyResponse> =
        policies.findAll().sortedWith(compareBy({ it.tenant }, { it.dataClass })).map { it.toResponse() }

    override fun setPolicy(tenant: String, dataClass: RetentionDataClass, retainDays: Int): RetentionPolicyResponse {
        if (tenant != DEFAULT_TENANT && !organisations.existsBySlug(tenant)) {
            throw NotFoundException("Organisation not found: $tenant")
        }
        if (retainDays < dataClass.minimumDays) {
            throw BadRequestException("$dataClass must be kept for at least ${dataClass.minimumDays} days; $retainDays is too short")
        }
        if (retainDays > MAXIMUM_DAYS) throw BadRequestException("retainDays must be at most $MAXIMUM_DAYS")
        val actor = actorResolver.current()
        val now = clock.instant()
        val existing = policies.find(tenant, dataClass)
        val before = existing?.retainDays
        val saved = policies.save(
            existing?.also { it.retainDays = retainDays; it.updatedBy = actor; it.updatedAt = now }
                ?: RetentionPolicy(tenant = tenant, dataClass = dataClass, retainDays = retainDays, updatedBy = actor, updatedAt = now)
        )
        auditService.record(
            AuditEventType.RETENTION_POLICY_SET, actor,
            mapOf("tenant" to tenant, "dataClass" to dataClass.name, "retainDays" to mapOf("before" to before, "after" to retainDays))
        )
        return saved.toResponse()
    }

    override fun removePolicy(tenant: String, dataClass: RetentionDataClass) {
        val policy = policies.find(tenant, dataClass)
            ?: throw NotFoundException("No $dataClass retention policy for $tenant")
        policies.delete(policy)
        auditService.record(
            AuditEventType.RETENTION_POLICY_REMOVED, actorResolver.current(),
            mapOf("tenant" to tenant, "dataClass" to dataClass.name, "retainDays" to policy.retainDays)
        )
    }

    // --- legal holds ------------------------------------------------------------------

    override fun placeHold(request: PlaceLegalHoldRequest): LegalHoldResponse {
        if (request.reason.isBlank()) throw BadRequestException("A legal hold needs a reason")
        val actor = actorResolver.current()
        val hold = holds.save(
            LegalHold(scopeType = request.scopeType, scopeId = request.scopeId, reason = request.reason, placedBy = actor, placedAt = clock.instant())
        )
        auditService.record(
            AuditEventType.LEGAL_HOLD_PLACED, actor,
            mapOf("holdId" to hold.id.toString(), "scopeType" to hold.scopeType.name, "scopeId" to hold.scopeId.toString(), "reason" to hold.reason),
            trailId = hold.scopeId.takeIf { hold.scopeType == LegalHoldScope.TRAIL }
        )
        return hold.toResponse()
    }

    override fun releaseHold(id: UUID): LegalHoldResponse {
        val hold = holds.findById(id) ?: throw NotFoundException("Legal hold not found: $id")
        if (!hold.active) throw ConflictException("Legal hold $id was already released")
        val actor = actorResolver.current()
        hold.releasedBy = actor
        hold.releasedAt = clock.instant()
        holds.save(hold)
        auditService.record(
            AuditEventType.LEGAL_HOLD_RELEASED, actor,
            mapOf("holdId" to hold.id.toString(), "scopeType" to hold.scopeType.name, "scopeId" to hold.scopeId.toString()),
            trailId = hold.scopeId.takeIf { hold.scopeType == LegalHoldScope.TRAIL }
        )
        return hold.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listHolds(includeReleased: Boolean): List<LegalHoldResponse> =
        (if (includeReleased) holds.findAll() else holds.findActive()).sortedBy { it.placedAt }.map { it.toResponse() }

    // --- the job ----------------------------------------------------------------------

    /** What legal holds protect, resolved once per run. */
    private class Held(val trailIds: Set<UUID>, val flowIds: Set<UUID>, val digests: Set<String>) {
        fun coversEntity(id: UUID?) = id != null && (id in trailIds || id in flowIds)
        fun coversDigest(digest: String) = normalise(digest) in digests
    }

    private fun held(): Held {
        val active = holds.findActive()
        val flowIds = active.filter { it.scopeType == LegalHoldScope.FLOW }.map { it.scopeId }.toSet()
        val trailIds = active.filter { it.scopeType == LegalHoldScope.TRAIL }.map { it.scopeId }.toSet() +
            store.trailIdsForFlows(flowIds)
        return Held(trailIds, flowIds, store.artifactDigestsForTrails(trailIds).map(::normalise).toSet())
    }

    override fun run(dryRun: Boolean): RetentionRunReport {
        val now = clock.instant()
        val held = held()
        val batches = policies.findAll()
            .sortedWith(compareBy({ it.tenant }, { it.dataClass }))
            .mapNotNull { policy ->
                val cutoff = now.minus(Duration.ofDays(policy.retainDays.toLong()))
                when (policy.dataClass) {
                    RetentionDataClass.FACT_CONTENT -> shredFactContent(policy, cutoff, held, dryRun)
                    RetentionDataClass.ENVIRONMENT_SNAPSHOTS -> archiveSnapshots(policy, cutoff, held, dryRun)
                    RetentionDataClass.NOTIFICATIONS -> pruneNotifications(policy, cutoff, held, dryRun)
                }
            }
        batches.forEach { record(it, dryRun) }
        return RetentionRunReport(dryRun = dryRun, ranAt = now, batches = batches)
    }

    private fun shredFactContent(policy: RetentionPolicy, cutoff: Instant, held: Held, dryRun: Boolean): RetentionBatch {
        // A tenant-month key is past its period once the whole month is older than the cutoff.
        val due = dataKeys.findAll().filter {
            it.tenant == policy.tenant && it.status == DataKeyStatus.ACTIVE && monthEnd(it.period)?.let { end -> !end.isAfter(cutoff) } == true
        }
        val (onHold, eligible) = due.partition { key -> store.factSubjectsUnderKey(key.tenant, key.period).any(held::coversDigest) }
        val applied = if (dryRun) 0 else eligible.count { shredding.shredForRetention(it.tenant, it.period, policy.updatedBy) != null }
        return RetentionBatch(policy.tenant, policy.dataClass, RetentionAction.SHRED, cutoff, eligible.size, applied, onHold.size)
    }

    private fun archiveSnapshots(policy: RetentionPolicy, cutoff: Instant, held: Held, dryRun: Boolean): RetentionBatch {
        val due = store.snapshotsRecordedBefore(cutoff).filter { it.tenant == policy.tenant && !it.latest && !it.baselined }
        val (onHold, eligible) = due.partition { snapshot -> snapshot.artifacts.any { held.coversDigest(it.artifactSha256) } }
        if (!dryRun) {
            eligible.forEach { snapshot ->
                archive.store(archiveKey(snapshot), objectMapper.writeValueAsString(snapshot.toArchiveDocument()))
                store.removeSnapshot(snapshot.id)
            }
        }
        return RetentionBatch(policy.tenant, policy.dataClass, RetentionAction.ARCHIVE, cutoff, eligible.size, if (dryRun) 0 else eligible.size, onHold.size)
    }

    private fun pruneNotifications(policy: RetentionPolicy, cutoff: Instant, held: Held, dryRun: Boolean): RetentionBatch? {
        // Notifications belong to the instance, not to an organisation.
        if (policy.tenant != DEFAULT_TENANT) return null
        val (onHold, eligible) = store.notificationsCreatedBefore(cutoff).partition { held.coversEntity(it.entityId) }
        val deliveries = store.deliveriesSentBefore(cutoff)
        if (!dryRun) {
            store.removeNotifications(eligible.map { it.id })
            store.removeDeliveries(deliveries)
        }
        val total = eligible.size + deliveries.size
        return RetentionBatch(policy.tenant, policy.dataClass, RetentionAction.PRUNE, cutoff, total, if (dryRun) 0 else total, onHold.size)
    }

    private fun record(batch: RetentionBatch, dryRun: Boolean) {
        val tags = arrayOf("data_class", batch.dataClass.name, "action", batch.action.name)
        if (batch.skippedForLegalHold > 0) {
            meters.counter("factstore.retention.skipped", "data_class", batch.dataClass.name, "reason", "legal_hold")
                .increment(batch.skippedForLegalHold.toDouble())
        }
        if (dryRun || batch.applied == 0) return
        meters.counter("factstore.retention.items", *tags).increment(batch.applied.toDouble())
        auditService.record(
            AuditEventType.RETENTION_BATCH_APPLIED, SYSTEM_ACTOR,
            mapOf(
                "tenant" to batch.tenant, "dataClass" to batch.dataClass.name, "action" to batch.action.name,
                "cutoff" to batch.cutoff.toString(), "applied" to batch.applied,
                "skippedForLegalHold" to batch.skippedForLegalHold
            )
        )
        log.info("Retention {} {} for {}: {} item(s) before {}", batch.action, batch.dataClass, batch.tenant, batch.applied, batch.cutoff)
    }

    private fun archiveKey(s: RetainedSnapshot) = "environment-snapshots/${s.tenant}/${s.environmentId}/${s.snapshotIndex}-${s.id}.json"

    private fun RetainedSnapshot.toArchiveDocument() = mapOf(
        "id" to id.toString(),
        "environmentId" to environmentId.toString(),
        "environmentName" to environmentName,
        "tenant" to tenant,
        "snapshotIndex" to snapshotIndex,
        "recordedAt" to recordedAt.toString(),
        "recordedBy" to recordedBy,
        "artifacts" to artifacts.map {
            mapOf(
                "artifactSha256" to it.artifactSha256, "artifactName" to it.artifactName, "artifactTag" to it.artifactTag,
                "instanceCount" to it.instanceCount, "complianceState" to it.complianceState
            )
        }
    )

    private fun RetentionPolicy.toResponse() =
        RetentionPolicyResponse(tenant, dataClass, dataClass.action, retainDays, dataClass.minimumDays, updatedBy, updatedAt)

    private fun LegalHold.toResponse() =
        LegalHoldResponse(id, scopeType, scopeId, reason, placedBy, placedAt, releasedBy, releasedAt, active)

    companion object {
        const val DEFAULT_TENANT = "default"
        const val MAXIMUM_DAYS = 36500
        const val SYSTEM_ACTOR = "retention-job"

        private fun normalise(digest: String) = digest.removePrefix("sha256:").lowercase()

        private fun monthEnd(period: String): Instant? =
            runCatching { YearMonth.parse(period).plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC) }.getOrNull()
    }
}
