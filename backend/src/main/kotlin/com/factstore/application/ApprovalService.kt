package com.factstore.application

import com.factstore.core.domain.Approval
import com.factstore.core.domain.ApprovalDecision
import com.factstore.core.domain.ApprovalDecisionType
import com.factstore.core.domain.ApprovalStatus
import com.factstore.core.domain.AuditEventType
import com.factstore.core.port.inbound.IApprovalService
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.outbound.IApprovalDecisionRepository
import com.factstore.core.port.outbound.IApprovalRepository
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.ApprovalDecisionResponse
import com.factstore.dto.ApprovalResponse
import com.factstore.dto.ApproveRequest
import com.factstore.dto.CreateApprovalRequest
import com.factstore.dto.RejectRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class ApprovalService(
    private val approvalRepository: IApprovalRepository,
    private val approvalDecisionRepository: IApprovalDecisionRepository,
    private val trailRepository: ITrailRepository,
    private val flowRepository: IFlowRepository,
    private val auditService: IAuditService,
    private val tenantScope: TenantScope = TenantScope.UNSCOPED,
    private val ownership: OrgOwnership? = null
) : IApprovalService {

    private val log = LoggerFactory.getLogger(ApprovalService::class.java)

    // An approval belongs to its trail's organisation (#207).
    private fun visible(approval: com.factstore.core.domain.Approval): Boolean =
        tenantScope.visible(ownership?.ofTrail(approval.trailId))

    override fun requestApproval(request: CreateApprovalRequest): ApprovalResponse {
        val trail = trailRepository.findById(request.trailId)
            ?: throw NotFoundException("Trail not found: ${request.trailId}")
        flowRepository.findById(trail.flowId)
            ?: throw NotFoundException("Flow not found: ${trail.flowId}")
        val approval = Approval(
            trailId = request.trailId,
            flowId = trail.flowId,
            comments = request.comments,
            deadline = request.deadline
        ).also { it.requiredApprovers = request.requiredApprovers }
        val saved = approvalRepository.save(approval)
        log.info("Approval requested: ${saved.id} for trail ${request.trailId}")
        return saved.toResponse(emptyList())
    }

    @Transactional(readOnly = true)
    override fun getApproval(approvalId: UUID): ApprovalResponse {
        val approval = tenantScope.require(approvalRepository.findById(approvalId), { ownership?.ofTrail(it.trailId) }) {
            "Approval not found: $approvalId"
        }
        val decisions = approvalDecisionRepository.findByApprovalId(approvalId)
        return approval.toResponse(decisions)
    }

    @Transactional(readOnly = true)
    override fun listApprovalsByTrail(trailId: UUID): List<ApprovalResponse> =
        approvalRepository.findByTrailId(trailId).filter(::visible).map { approval ->
            approval.toResponse(approvalDecisionRepository.findByApprovalId(approval.id))
        }

    @Transactional(readOnly = true)
    override fun listApprovalsByStatus(status: ApprovalStatus): List<ApprovalResponse> =
        approvalRepository.findByStatus(status).filter(::visible).map { approval ->
            approval.toResponse(approvalDecisionRepository.findByApprovalId(approval.id))
        }

    @Transactional(readOnly = true)
    override fun listAll(): List<ApprovalResponse> =
        approvalRepository.findAll().filter(::visible).map { approval ->
            approval.toResponse(approvalDecisionRepository.findByApprovalId(approval.id))
        }

    override fun approve(approvalId: UUID, request: ApproveRequest): ApprovalResponse {
        val approval = approvalRepository.findById(approvalId)
            ?: throw NotFoundException("Approval not found: $approvalId")
        if (approval.status != ApprovalStatus.PENDING_APPROVAL)
            throw ConflictException("Approval is not in PENDING_APPROVAL state")
        val decision = ApprovalDecision(
            approvalId = approvalId,
            approverIdentity = request.approverIdentity,
            decision = ApprovalDecisionType.APPROVED,
            comments = request.comments
        )
        approvalDecisionRepository.save(decision)
        val allDecisions = approvalDecisionRepository.findByApprovalId(approvalId)
        val approvedBy = allDecisions.filter { it.decision == ApprovalDecisionType.APPROVED }.map { it.approverIdentity }.toSet()
        val allApproved = approval.requiredApprovers.isEmpty() || approval.requiredApprovers.all { it in approvedBy }
        if (allApproved) {
            approval.status = ApprovalStatus.APPROVED
            approval.resolvedAt = Instant.now()
            approvalRepository.save(approval)
        }
        auditService.record(
            eventType = AuditEventType.APPROVAL_GRANTED,
            actor = request.approverIdentity,
            payload = mapOf("approvalId" to approvalId.toString()),
            trailId = approval.trailId
        )
        val finalDecisions = approvalDecisionRepository.findByApprovalId(approvalId)
        return (approvalRepository.findById(approvalId) ?: approval).toResponse(finalDecisions)
    }

    override fun reject(approvalId: UUID, request: RejectRequest): ApprovalResponse {
        val approval = approvalRepository.findById(approvalId)
            ?: throw NotFoundException("Approval not found: $approvalId")
        if (approval.status != ApprovalStatus.PENDING_APPROVAL)
            throw ConflictException("Approval is not in PENDING_APPROVAL state")
        val decision = ApprovalDecision(
            approvalId = approvalId,
            approverIdentity = request.approverIdentity,
            decision = ApprovalDecisionType.REJECTED,
            comments = request.comments
        )
        approvalDecisionRepository.save(decision)
        approval.status = ApprovalStatus.REJECTED
        approval.resolvedAt = Instant.now()
        approvalRepository.save(approval)
        auditService.record(
            eventType = AuditEventType.APPROVAL_REJECTED,
            actor = request.approverIdentity,
            payload = mapOf("approvalId" to approvalId.toString()),
            trailId = approval.trailId
        )
        val finalDecisions = approvalDecisionRepository.findByApprovalId(approvalId)
        return approval.toResponse(finalDecisions)
    }
}

fun Approval.toResponse(decisions: List<ApprovalDecision>) = ApprovalResponse(
    id = id,
    trailId = trailId,
    flowId = flowId,
    status = status,
    requiredApprovers = requiredApprovers,
    comments = comments,
    requestedAt = requestedAt,
    deadline = deadline,
    resolvedAt = resolvedAt,
    decisions = decisions.map { it.toDecisionResponse() }
)

fun ApprovalDecision.toDecisionResponse() = ApprovalDecisionResponse(
    id = id,
    approvalId = approvalId,
    approverIdentity = approverIdentity,
    decision = decision,
    comments = comments,
    decidedAt = decidedAt
)
