package com.factstore.application

import com.factstore.core.domain.Environment
import com.factstore.core.domain.EnvironmentBaseline
import com.factstore.core.domain.DriftReport
import com.factstore.core.domain.EnvironmentSnapshot
import com.factstore.core.domain.SnapshotArtifact
import com.factstore.core.port.inbound.IEnvironmentService
import com.factstore.core.port.outbound.IEnvironmentBaselineRepository
import com.factstore.core.port.outbound.IDriftReportRepository
import com.factstore.core.port.outbound.IEnvironmentRepository
import com.factstore.core.port.outbound.IEnvironmentSnapshotRepository
import com.factstore.core.port.outbound.ISnapshotArtifactRepository
import com.factstore.dto.BaselineResponse
import com.factstore.dto.CreateBaselineRequest
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.dto.DriftReportResponse
import com.factstore.dto.EnvironmentResponse
import com.factstore.dto.EnvironmentSnapshotResponse
import com.factstore.dto.RecordSnapshotRequest
import com.factstore.dto.SnapshotArtifactResponse
import com.factstore.dto.SnapshotDiffEntry
import com.factstore.dto.SnapshotDiffResponse
import com.factstore.dto.UpdateEnvironmentRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class EnvironmentService(
    private val environmentRepository: IEnvironmentRepository,
    private val snapshotRepository: IEnvironmentSnapshotRepository,
    private val snapshotArtifactRepository: ISnapshotArtifactRepository,
    private val baselineRepository: IEnvironmentBaselineRepository,
    private val driftReportRepository: IDriftReportRepository
) : IEnvironmentService {

    private val log = LoggerFactory.getLogger(EnvironmentService::class.java)

    override fun createEnvironment(request: CreateEnvironmentRequest): EnvironmentResponse {
        if (environmentRepository.existsByName(request.name)) {
            throw ConflictException("Environment with name '${request.name}' already exists")
        }
        val environment = Environment(
            name = request.name,
            type = request.type,
            description = request.description,
            orgSlug = request.orgSlug,
            driftPolicy = request.driftPolicy
        )
        val saved = environmentRepository.save(environment)
        log.info("Created environment: ${saved.id} - ${saved.name}")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listEnvironments(): List<EnvironmentResponse> =
        environmentRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun getEnvironment(id: UUID): EnvironmentResponse =
        (environmentRepository.findById(id) ?: throw NotFoundException("Environment not found: $id")).toResponse()

    override fun updateEnvironment(id: UUID, request: UpdateEnvironmentRequest): EnvironmentResponse {
        val environment = environmentRepository.findById(id) ?: throw NotFoundException("Environment not found: $id")
        request.name?.let {
            if (it != environment.name && environmentRepository.existsByName(it)) {
                throw ConflictException("Environment with name '$it' already exists")
            }
            environment.name = it
        }
        request.type?.let { environment.type = it }
        request.description?.let { environment.description = it }
        request.driftPolicy?.let { environment.driftPolicy = it }
        environment.updatedAt = Instant.now()
        return environmentRepository.save(environment).toResponse()
    }

    override fun deleteEnvironment(id: UUID) {
        if (!environmentRepository.existsById(id)) throw NotFoundException("Environment not found: $id")
        environmentRepository.deleteById(id)
        log.info("Deleted environment: $id")
    }

    override fun recordSnapshot(environmentId: UUID, request: RecordSnapshotRequest): EnvironmentSnapshotResponse {
        if (!environmentRepository.existsById(environmentId)) {
            throw NotFoundException("Environment not found: $environmentId")
        }
        val nextIndex = (snapshotRepository.findMaxSnapshotIndexByEnvironmentId(environmentId) ?: 0L) + 1
        val snapshot = snapshotRepository.save(
            EnvironmentSnapshot(
                environmentId = environmentId,
                snapshotIndex = nextIndex,
                recordedBy = request.recordedBy
            )
        )
        val artifacts = snapshotArtifactRepository.saveAll(
            request.artifacts.map { a ->
                SnapshotArtifact(
                    snapshotId = snapshot.id,
                    artifactSha256 = a.artifactSha256,
                    artifactName = a.artifactName,
                    artifactTag = a.artifactTag,
                    instanceCount = a.instanceCount
                )
            }
        )
        log.info("Recorded snapshot #$nextIndex for environment: $environmentId")
        return snapshot.toResponse(artifacts)
    }

    @Transactional(readOnly = true)
    override fun listSnapshots(environmentId: UUID): List<EnvironmentSnapshotResponse> {
        if (!environmentRepository.existsById(environmentId)) {
            throw NotFoundException("Environment not found: $environmentId")
        }
        val snapshots = snapshotRepository.findAllByEnvironmentId(environmentId)
        val snapshotIds = snapshots.map { it.id }
        val artifactsBySnapshot = snapshotArtifactRepository.findAllBySnapshotIdIn(snapshotIds)
            .groupBy { it.snapshotId }
        return snapshots.map { snapshot ->
            snapshot.toResponse(artifactsBySnapshot[snapshot.id] ?: emptyList())
        }
    }

    @Transactional(readOnly = true)
    override fun getLatestSnapshot(environmentId: UUID): EnvironmentSnapshotResponse {
        if (!environmentRepository.existsById(environmentId)) {
            throw NotFoundException("Environment not found: $environmentId")
        }
        val snapshot = snapshotRepository.findLatestByEnvironmentId(environmentId)
            ?: throw NotFoundException("No snapshots found for environment: $environmentId")
        return snapshot.toResponse(snapshotArtifactRepository.findAllBySnapshotId(snapshot.id))
    }

    @Transactional(readOnly = true)
    override fun getSnapshot(environmentId: UUID, snapshotIndex: Long): EnvironmentSnapshotResponse {
        if (!environmentRepository.existsById(environmentId)) {
            throw NotFoundException("Environment not found: $environmentId")
        }
        val snapshot = snapshotRepository.findByEnvironmentIdAndSnapshotIndex(environmentId, snapshotIndex)
            ?: throw NotFoundException("Snapshot #$snapshotIndex not found for environment: $environmentId")
        return snapshot.toResponse(snapshotArtifactRepository.findAllBySnapshotId(snapshot.id))
    }

    @Transactional(readOnly = true)
    override fun diffSnapshots(environmentId: UUID, fromIndex: Long, toIndex: Long): SnapshotDiffResponse {
        val fromSnapshot = snapshotRepository.findByEnvironmentIdAndSnapshotIndex(environmentId, fromIndex)
            ?: throw NotFoundException("Snapshot #$fromIndex not found for environment: $environmentId")
        val toSnapshot = snapshotRepository.findByEnvironmentIdAndSnapshotIndex(environmentId, toIndex)
            ?: throw NotFoundException("Snapshot #$toIndex not found for environment: $environmentId")

        val fromArtifacts = snapshotArtifactRepository.findAllBySnapshotId(fromSnapshot.id)
        val toArtifacts = snapshotArtifactRepository.findAllBySnapshotId(toSnapshot.id)

        val fromMap = fromArtifacts.associateBy { it.artifactName }
        val toMap = toArtifacts.associateBy { it.artifactName }

        val added = toMap.values.filter { it.artifactName !in fromMap }
            .map { SnapshotDiffEntry(it.artifactName, it.artifactTag, null, it.artifactSha256) }
        val removed = fromMap.values.filter { it.artifactName !in toMap }
            .map { SnapshotDiffEntry(it.artifactName, it.artifactTag, it.artifactSha256, null) }
        val updated = toMap.values.filter { art ->
            art.artifactName in fromMap && fromMap[art.artifactName]!!.artifactSha256 != art.artifactSha256
        }.map { art ->
            SnapshotDiffEntry(art.artifactName, art.artifactTag, fromMap[art.artifactName]!!.artifactSha256, art.artifactSha256)
        }
        val unchanged = toMap.values.filter { art ->
            art.artifactName in fromMap && fromMap[art.artifactName]!!.artifactSha256 == art.artifactSha256
        }.map { SnapshotDiffEntry(it.artifactName, it.artifactTag, it.artifactSha256, it.artifactSha256) }

        return SnapshotDiffResponse(environmentId, fromIndex, toIndex, added, removed, updated, unchanged)
    }

    override fun createBaseline(environmentId: UUID, request: CreateBaselineRequest): BaselineResponse {
        if (!environmentRepository.existsById(environmentId)) {
            throw NotFoundException("Environment not found: $environmentId")
        }
        baselineRepository.findActiveByEnvironmentId(environmentId)?.let {
            it.isActive = false
            baselineRepository.save(it)
        }
        val snapshotId = request.snapshotId ?: snapshotRepository.findLatestByEnvironmentId(environmentId)?.id
        val baseline = EnvironmentBaseline(
            environmentId = environmentId,
            snapshotId = snapshotId,
            approvedBy = request.approvedBy,
            description = request.description
        )
        return baselineRepository.save(baseline).toResponse()
    }

    @Transactional(readOnly = true)
    override fun getCurrentBaseline(environmentId: UUID): BaselineResponse {
        if (!environmentRepository.existsById(environmentId)) {
            throw NotFoundException("Environment not found: $environmentId")
        }
        return (baselineRepository.findActiveByEnvironmentId(environmentId)
            ?: throw NotFoundException("No active baseline for environment: $environmentId")).toResponse()
    }

    override fun checkDrift(environmentId: UUID): DriftReportResponse {
        if (!environmentRepository.existsById(environmentId)) {
            throw NotFoundException("Environment not found: $environmentId")
        }
        val baseline = baselineRepository.findActiveByEnvironmentId(environmentId)
            ?: throw NotFoundException("No active baseline for environment: $environmentId")
        val baselineSnapshotId = baseline.snapshotId
            ?: throw NotFoundException("Baseline has no associated snapshot")
        val latestSnapshot = snapshotRepository.findLatestByEnvironmentId(environmentId)
            ?: throw NotFoundException("No snapshots found for environment: $environmentId")

        val baselineArtifacts = snapshotArtifactRepository.findAllBySnapshotId(baselineSnapshotId)
        val currentArtifacts = snapshotArtifactRepository.findAllBySnapshotId(latestSnapshot.id)

        val baselineMap = baselineArtifacts.associateBy { it.artifactName }
        val currentMap = currentArtifacts.associateBy { it.artifactName }

        val added = currentMap.values.filter { it.artifactName !in baselineMap }
            .map { SnapshotDiffEntry(it.artifactName, it.artifactTag, null, it.artifactSha256) }
        val removed = baselineMap.values.filter { it.artifactName !in currentMap }
            .map { SnapshotDiffEntry(it.artifactName, it.artifactTag, it.artifactSha256, null) }
        val updated = currentMap.values.filter { art ->
            art.artifactName in baselineMap && baselineMap[art.artifactName]!!.artifactSha256 != art.artifactSha256
        }.map { art ->
            SnapshotDiffEntry(art.artifactName, art.artifactTag, baselineMap[art.artifactName]!!.artifactSha256, art.artifactSha256)
        }

        val hasDrift = added.isNotEmpty() || removed.isNotEmpty() || updated.isNotEmpty()

        val report = DriftReport(
            environmentId = environmentId,
            baselineId = baseline.id,
            snapshotId = latestSnapshot.id,
            hasDrift = hasDrift
        ).also {
            it.addedArtifactsRaw = added.joinToString("||") { e -> "${e.artifactName}:${e.sha256To}" }
            it.removedArtifactsRaw = removed.joinToString("||") { e -> "${e.artifactName}:${e.sha256From}" }
            it.updatedArtifactsRaw = updated.joinToString("||") { e -> "${e.artifactName}:${e.sha256From}->${e.sha256To}" }
        }

        return driftReportRepository.save(report).toResponse(added, removed, updated)
    }

    @Transactional(readOnly = true)
    override fun listDriftHistory(environmentId: UUID): List<DriftReportResponse> {
        if (!environmentRepository.existsById(environmentId)) {
            throw NotFoundException("Environment not found: $environmentId")
        }
        return driftReportRepository.findAllByEnvironmentId(environmentId).map { report ->
            report.toResponse(
                parseRawArtifacts(report.addedArtifactsRaw, isAdded = true),
                parseRawArtifacts(report.removedArtifactsRaw, isAdded = false),
                parseUpdatedArtifacts(report.updatedArtifactsRaw)
            )
        }
    }

    private fun parseRawArtifacts(raw: String, isAdded: Boolean): List<SnapshotDiffEntry> {
        if (raw.isBlank()) return emptyList()
        return raw.split("||").mapNotNull { token ->
            val colonIdx = token.lastIndexOf(':')
            if (colonIdx < 0) return@mapNotNull null
            val name = token.substring(0, colonIdx)
            val sha = token.substring(colonIdx + 1)
            if (isAdded) SnapshotDiffEntry(name, "", null, sha)
            else SnapshotDiffEntry(name, "", sha, null)
        }
    }

    private fun parseUpdatedArtifacts(raw: String): List<SnapshotDiffEntry> {
        if (raw.isBlank()) return emptyList()
        return raw.split("||").mapNotNull { token ->
            val arrowIdx = token.lastIndexOf("->")
            if (arrowIdx < 0) return@mapNotNull null
            val colonIdx = token.lastIndexOf(':', arrowIdx)
            if (colonIdx < 0) return@mapNotNull null
            val name = token.substring(0, colonIdx)
            val shaFrom = token.substring(colonIdx + 1, arrowIdx)
            val shaTo = token.substring(arrowIdx + 2)
            SnapshotDiffEntry(name, "", shaFrom, shaTo)
        }
    }
}

fun Environment.toResponse() = EnvironmentResponse(
    id = id,
    name = name,
    type = type,
    description = description,
    orgSlug = orgSlug,
    driftPolicy = driftPolicy,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun EnvironmentBaseline.toResponse() = BaselineResponse(
    id = id,
    environmentId = environmentId,
    snapshotId = snapshotId,
    approvedBy = approvedBy,
    approvedAt = approvedAt,
    description = description,
    isActive = isActive
)

fun DriftReport.toResponse(
    added: List<SnapshotDiffEntry>,
    removed: List<SnapshotDiffEntry>,
    updated: List<SnapshotDiffEntry>
) = DriftReportResponse(
    id = id,
    environmentId = environmentId,
    baselineId = baselineId,
    snapshotId = snapshotId,
    generatedAt = generatedAt,
    hasDrift = hasDrift,
    added = added,
    removed = removed,
    updated = updated
)

fun EnvironmentSnapshot.toResponse(artifacts: List<SnapshotArtifact>) = EnvironmentSnapshotResponse(
    id = id,
    environmentId = environmentId,
    snapshotIndex = snapshotIndex,
    recordedAt = recordedAt,
    recordedBy = recordedBy,
    artifacts = artifacts.map { it.toResponse() }
)

fun SnapshotArtifact.toResponse() = SnapshotArtifactResponse(
    artifactSha256 = artifactSha256,
    artifactName = artifactName,
    artifactTag = artifactTag,
    instanceCount = instanceCount
)

