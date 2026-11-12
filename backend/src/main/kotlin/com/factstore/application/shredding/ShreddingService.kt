package com.factstore.application.shredding

import com.factstore.application.ActorResolver
import com.factstore.application.command.EventAppender
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.DataKeyStatus
import com.factstore.core.domain.ShredRequest
import com.factstore.core.domain.ShredRequestStatus
import com.factstore.core.domain.event.DomainEvent
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.outbound.IDataKeyRepository
import com.factstore.core.port.outbound.IShredRequestRepository
import com.factstore.exception.ConflictException
import com.factstore.exception.ForbiddenException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class ShredRequestView(
    @get:JsonProperty("request_id") val requestId: UUID,
    val tenant: String,
    val period: String,
    val status: ShredRequestStatus,
    @get:JsonProperty("requested_by") val requestedBy: String
)

data class ShredCertificate(
    val tenant: String,
    val period: String,
    @get:JsonProperty("shredded_at") val shreddedAt: Instant,
    @get:JsonProperty("requested_by") val requestedBy: String,
    @get:JsonProperty("approved_by") val approvedBy: String,
    /** The `DataKeyShredded` ledger record: the destruction certificate is itself a signed fact. */
    @get:JsonProperty("certificate_hash") val certificateHash: String?,
    @get:JsonProperty("ledger_index") val ledgerIndex: Long
)

data class DataKeyView(
    val tenant: String,
    val period: String,
    val status: DataKeyStatus,
    @get:JsonProperty("kek_id") val kekId: String,
    @get:JsonProperty("created_at") val createdAt: Instant,
    @get:JsonProperty("shredded_at") val shreddedAt: Instant?
)

/**
 * Cryptographic shredding (#200), under a two-person rule.
 *
 * One administrator requests that a tenant-month data key be shredded, and a different
 * administrator approves it. On approval the wrapped key is deleted, so every payload encrypted
 * under it becomes unreadable on every instance. A `DataKeyShredded` record is then appended to the
 * signed ledger as the destruction certificate. The ledger itself only ever held ciphertext, so
 * its hashes, signatures and proofs (#184, #188) still verify.
 */
@Service
@Transactional
class ShreddingService(
    private val keys: IDataKeyRepository,
    private val requests: IShredRequestRepository,
    private val encryption: PayloadEncryption,
    private val eventAppender: EventAppender,
    private val auditService: IAuditService,
    private val actorResolver: ActorResolver,
    private val clock: Clock
) {

    fun request(tenant: String, period: String): ShredRequestView {
        val key = keys.find(tenant, period) ?: throw NotFoundException("No data key for $tenant/$period")
        if (key.status == DataKeyStatus.SHREDDED) throw ConflictException("The data key for $tenant/$period is already shredded")
        val actor = actorResolver.current()
        val saved = requests.save(ShredRequest(tenant = tenant, period = period, requestedBy = actor, requestedAt = clock.instant()))
        auditService.record(
            AuditEventType.DATA_KEY_SHRED_REQUESTED, actor,
            mapOf("requestId" to saved.id.toString(), "tenant" to tenant, "period" to period)
        )
        return saved.toView()
    }

    fun approve(requestId: UUID): ShredCertificate {
        val request = requests.findById(requestId) ?: throw NotFoundException("No shred request $requestId")
        if (request.status != ShredRequestStatus.PENDING_APPROVAL) throw ConflictException("Shred request $requestId was already approved")
        val approver = actorResolver.current()
        if (approver == request.requestedBy) {
            throw ForbiddenException("Shredding needs a second administrator: ${request.requestedBy} requested it and cannot approve it")
        }
        val key = keys.find(request.tenant, request.period) ?: throw NotFoundException("No data key for ${request.tenant}/${request.period}")
        if (key.status == DataKeyStatus.SHREDDED) throw ConflictException("The data key for ${request.tenant}/${request.period} is already shredded")

        val now = clock.instant()
        key.wrappedKey = null
        key.status = DataKeyStatus.SHREDDED
        key.shreddedAt = now
        key.shreddedBy = approver
        keys.save(key)
        request.status = ShredRequestStatus.APPROVED
        request.approvedBy = approver
        request.approvedAt = now
        requests.save(request)
        encryption.forget(request.tenant, request.period)

        val record = eventAppender.append(
            DomainEvent.DataKeyShredded(
                aggregateId = key.id,
                occurredAt = now,
                tenant = request.tenant,
                period = request.period,
                requestedBy = request.requestedBy,
                approvedBy = approver
            )
        )
        auditService.record(
            AuditEventType.DATA_KEY_SHREDDED, approver,
            mapOf(
                "requestId" to request.id.toString(), "tenant" to request.tenant, "period" to request.period,
                "requestedBy" to request.requestedBy, "certificateHash" to record.recordHash
            )
        )
        return ShredCertificate(request.tenant, request.period, now, request.requestedBy, approver, record.recordHash, record.sequenceNumber)
    }

    @Transactional(readOnly = true)
    fun list(): List<DataKeyView> = keys.findAll().map {
        DataKeyView(it.tenant, it.period, it.status, it.kekId, it.createdAt, it.shreddedAt)
    }

    private fun ShredRequest.toView() = ShredRequestView(id, tenant, period, status, requestedBy)
}
