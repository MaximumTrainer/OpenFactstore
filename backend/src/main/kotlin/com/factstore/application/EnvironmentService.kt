package com.factstore.application

import com.factstore.core.domain.Environment
import com.factstore.core.domain.EnvironmentSnapshot
import com.factstore.core.domain.SnapshotArtifact
import com.factstore.core.port.inbound.IEnvironmentService
import com.factstore.core.port.outbound.IEnvironmentRepository
import com.factstore.core.port.outbound.IEnvironmentSnapshotRepository
import com.factstore.core.port.outbound.ISnapshotArtifactRepository
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.dto.EnvironmentResponse
import com.factstore.dto.EnvironmentSnapshotResponse
import com.factstore.dto.RecordSnapshotRequest
import com.factstore.dto.SnapshotArtifactResponse
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
    private val snapshotArtifactRepository: ISnapshotArtifactRepository
) : IEnvironmentService {

    private val log = LoggerFactory.getLogger(EnvironmentService::class.java)

    override fun createEnvironment(request: CreateEnvironmentRequest): EnvironmentResponse {
        if (environmentRepository.existsByName(request.name)) {
            throw ConflictException("Environment with name '${request.name}' already exists")
        }
        val environment = Environment(
            name = request.name,
            type = request.type,
            description = request.description
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
        val nextIndex = snapshotRepository.countByEnvironmentId(environmentId) + 1
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
        return snapshotRepository.findAllByEnvironmentId(environmentId).map { snapshot ->
            snapshot.toResponse(snapshotArtifactRepository.findAllBySnapshotId(snapshot.id))
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
}

fun Environment.toResponse() = EnvironmentResponse(
    id = id,
    name = name,
    type = type,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt
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
