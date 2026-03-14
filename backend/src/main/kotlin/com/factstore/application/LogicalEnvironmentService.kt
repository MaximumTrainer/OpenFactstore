package com.factstore.application

import com.factstore.core.domain.LogicalEnvironment
import com.factstore.core.domain.LogicalEnvironmentMember
import com.factstore.core.port.inbound.ILogicalEnvironmentService
import com.factstore.core.port.outbound.IEnvironmentRepository
import com.factstore.core.port.outbound.IEnvironmentSnapshotRepository
import com.factstore.core.port.outbound.ILogicalEnvironmentMemberRepository
import com.factstore.core.port.outbound.ILogicalEnvironmentRepository
import com.factstore.core.port.outbound.ISnapshotArtifactRepository
import com.factstore.dto.ComplianceStatus
import com.factstore.dto.CreateLogicalEnvironmentRequest
import com.factstore.dto.LogicalEnvironmentMemberResponse
import com.factstore.dto.LogicalEnvironmentResponse
import com.factstore.dto.MemberSnapshotSummary
import com.factstore.dto.MergedSnapshotArtifact
import com.factstore.dto.MergedSnapshotResponse
import com.factstore.core.port.inbound.ILogicalEnvironmentService
import com.factstore.core.port.outbound.ILogicalEnvironmentRepository
import com.factstore.dto.CreateLogicalEnvironmentRequest
import com.factstore.dto.LogicalEnvironmentResponse
import com.factstore.dto.UpdateLogicalEnvironmentRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class LogicalEnvironmentService(
    private val logicalEnvRepository: ILogicalEnvironmentRepository,
    private val memberRepository: ILogicalEnvironmentMemberRepository,
    private val environmentRepository: IEnvironmentRepository,
    private val snapshotRepository: IEnvironmentSnapshotRepository,
    private val snapshotArtifactRepository: ISnapshotArtifactRepository
) : ILogicalEnvironmentService {
class LogicalEnvironmentService(private val logicalEnvironmentRepository: ILogicalEnvironmentRepository) : ILogicalEnvironmentService {

    private val log = LoggerFactory.getLogger(LogicalEnvironmentService::class.java)

    override fun createLogicalEnvironment(request: CreateLogicalEnvironmentRequest): LogicalEnvironmentResponse {
        if (logicalEnvRepository.existsByName(request.name)) {
            throw ConflictException("Logical environment with name '${request.name}' already exists")
        }
        val logicalEnv = LogicalEnvironment(name = request.name, description = request.description)
        val saved = logicalEnvRepository.save(logicalEnv)
        log.info("Created logical environment: ${saved.id} - ${saved.name}")
        return saved.toResponse(emptyList())
    }

    @Transactional(readOnly = true)
    override fun listLogicalEnvironments(): List<LogicalEnvironmentResponse> {
        val envs = logicalEnvRepository.findAll()
        return envs.map { env ->
            val members = memberRepository.findAllByLogicalEnvId(env.id)
            env.toResponse(buildMemberResponses(members))
        }
    }

    @Transactional(readOnly = true)
    override fun getLogicalEnvironment(id: UUID): LogicalEnvironmentResponse {
        val env = logicalEnvRepository.findById(id) ?: throw NotFoundException("Logical environment not found: $id")
        val members = memberRepository.findAllByLogicalEnvId(id)
        return env.toResponse(buildMemberResponses(members))
    }

    override fun updateLogicalEnvironment(id: UUID, request: UpdateLogicalEnvironmentRequest): LogicalEnvironmentResponse {
        val env = logicalEnvRepository.findById(id) ?: throw NotFoundException("Logical environment not found: $id")
        request.name?.let {
            if (it != env.name && logicalEnvRepository.existsByName(it)) {
                throw ConflictException("Logical environment with name '$it' already exists")
        if (logicalEnvironmentRepository.existsByName(request.name)) {
            throw ConflictException("LogicalEnvironment with name '${request.name}' already exists")
        }
        val env = LogicalEnvironment(
            name = request.name,
            description = request.description
        )
        val saved = logicalEnvironmentRepository.save(env)
        log.info("Created logical environment: ${saved.id} - ${saved.name}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listLogicalEnvironments(): List<LogicalEnvironmentResponse> =
        logicalEnvironmentRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getLogicalEnvironment(id: UUID): LogicalEnvironmentResponse =
        (logicalEnvironmentRepository.findById(id) ?: throw NotFoundException("LogicalEnvironment not found: $id")).toResponse()

    override fun updateLogicalEnvironment(id: UUID, request: UpdateLogicalEnvironmentRequest): LogicalEnvironmentResponse {
        val env = logicalEnvironmentRepository.findById(id)
            ?: throw NotFoundException("LogicalEnvironment not found: $id")
        request.name?.let {
            if (it != env.name && logicalEnvironmentRepository.existsByName(it)) {
                throw ConflictException("LogicalEnvironment with name '$it' already exists")
            }
            env.name = it
        }
        request.description?.let { env.description = it }
        env.updatedAt = Instant.now()
        val saved = logicalEnvRepository.save(env)
        val members = memberRepository.findAllByLogicalEnvId(id)
        return saved.toResponse(buildMemberResponses(members))
    }

    override fun deleteLogicalEnvironment(id: UUID) {
        if (!logicalEnvRepository.existsById(id)) throw NotFoundException("Logical environment not found: $id")
        memberRepository.deleteAllByLogicalEnvId(id)
        logicalEnvRepository.deleteById(id)
        log.info("Deleted logical environment: $id")
    }

    override fun addMember(logicalEnvId: UUID, physicalEnvId: UUID): LogicalEnvironmentResponse {
        val env = logicalEnvRepository.findById(logicalEnvId)
            ?: throw NotFoundException("Logical environment not found: $logicalEnvId")
        if (!environmentRepository.existsById(physicalEnvId)) {
            throw NotFoundException("Physical environment not found: $physicalEnvId")
        }
        if (memberRepository.findByLogicalEnvIdAndPhysicalEnvId(logicalEnvId, physicalEnvId) != null) {
            throw ConflictException("Physical environment $physicalEnvId is already a member of logical environment $logicalEnvId")
        }
        memberRepository.save(LogicalEnvironmentMember(logicalEnvId = logicalEnvId, physicalEnvId = physicalEnvId))
        log.info("Added physical env $physicalEnvId to logical env $logicalEnvId")
        val members = memberRepository.findAllByLogicalEnvId(logicalEnvId)
        return env.toResponse(buildMemberResponses(members))
    }

    override fun removeMember(logicalEnvId: UUID, physicalEnvId: UUID) {
        if (!logicalEnvRepository.existsById(logicalEnvId)) {
            throw NotFoundException("Logical environment not found: $logicalEnvId")
        }
        if (memberRepository.findByLogicalEnvIdAndPhysicalEnvId(logicalEnvId, physicalEnvId) == null) {
            throw NotFoundException("Physical environment $physicalEnvId is not a member of logical environment $logicalEnvId")
        }
        memberRepository.deleteByLogicalEnvIdAndPhysicalEnvId(logicalEnvId, physicalEnvId)
        log.info("Removed physical env $physicalEnvId from logical env $logicalEnvId")
    }

    @Transactional(readOnly = true)
    override fun getMergedSnapshot(logicalEnvId: UUID): MergedSnapshotResponse {
        val env = logicalEnvRepository.findById(logicalEnvId)
            ?: throw NotFoundException("Logical environment not found: $logicalEnvId")
        val members = memberRepository.findAllByLogicalEnvId(logicalEnvId)
        val memberResponses = buildMemberResponses(members)

        val memberSummaries = mutableListOf<MemberSnapshotSummary>()
        var allHaveSnapshots = true

        // Collect latest snapshots for all members in a single pass, then batch-fetch artifacts
        val latestSnapshots = memberResponses.associateWith { member ->
            snapshotRepository.findLatestByEnvironmentId(member.physicalEnvId)
        }

        val snapshotIds = latestSnapshots.values.filterNotNull().map { it.id }
        val artifactsBySnapshotId = if (snapshotIds.isNotEmpty()) {
            snapshotArtifactRepository.findAllBySnapshotIdIn(snapshotIds).groupBy { it.snapshotId }
        } else {
            emptyMap()
        }

        val mergedArtifacts = mutableListOf<MergedSnapshotArtifact>()

        for (member in memberResponses) {
            val latestSnapshot = latestSnapshots[member]
            if (latestSnapshot == null) {
                allHaveSnapshots = false
                memberSummaries.add(
                    MemberSnapshotSummary(
                        physicalEnvId = member.physicalEnvId,
                        physicalEnvName = member.physicalEnvName,
                        snapshotIndex = null,
                        recordedAt = null,
                        artifactCount = 0
                    )
                )
            } else {
                val artifacts = artifactsBySnapshotId[latestSnapshot.id] ?: emptyList()
                memberSummaries.add(
                    MemberSnapshotSummary(
                        physicalEnvId = member.physicalEnvId,
                        physicalEnvName = member.physicalEnvName,
                        snapshotIndex = latestSnapshot.snapshotIndex,
                        recordedAt = latestSnapshot.recordedAt,
                        artifactCount = artifacts.size
                    )
                )
                artifacts.forEach { artifact ->
                    mergedArtifacts.add(
                        MergedSnapshotArtifact(
                            artifactSha256 = artifact.artifactSha256,
                            artifactName = artifact.artifactName,
                            artifactTag = artifact.artifactTag,
                            instanceCount = artifact.instanceCount,
                            physicalEnvId = member.physicalEnvId,
                            physicalEnvName = member.physicalEnvName
                        )
                    )
                }
            }
        }

        val complianceStatus = if (allHaveSnapshots && members.isNotEmpty()) {
            ComplianceStatus.COMPLIANT
        } else {
            ComplianceStatus.NON_COMPLIANT
        }

        return MergedSnapshotResponse(
            logicalEnvId = env.id,
            logicalEnvName = env.name,
            complianceStatus = complianceStatus,
            memberSnapshots = memberSummaries,
            mergedArtifacts = mergedArtifacts
        )
    }

    private fun buildMemberResponses(members: List<LogicalEnvironmentMember>): List<LogicalEnvironmentMemberResponse> {
        return members.map { member ->
            val physicalEnv = environmentRepository.findById(member.physicalEnvId)
                ?: throw NotFoundException("Physical environment not found: ${member.physicalEnvId}")
            LogicalEnvironmentMemberResponse(
                physicalEnvId = physicalEnv.id,
                physicalEnvName = physicalEnv.name,
                physicalEnvType = physicalEnv.type,
                addedAt = member.addedAt
            )
        }
    }
}

fun LogicalEnvironment.toResponse(members: List<LogicalEnvironmentMemberResponse>) = LogicalEnvironmentResponse(
    id = id,
    name = name,
    description = description,
    members = members,
        return logicalEnvironmentRepository.save(env).toResponse()
    }

    override fun deleteLogicalEnvironment(id: UUID) {
        if (!logicalEnvironmentRepository.existsById(id)) throw NotFoundException("LogicalEnvironment not found: $id")
        logicalEnvironmentRepository.deleteById(id)
        log.info("Deleted logical environment: $id")
    }
}

fun LogicalEnvironment.toResponse() = LogicalEnvironmentResponse(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt
)
