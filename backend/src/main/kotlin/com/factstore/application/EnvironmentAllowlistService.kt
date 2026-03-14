package com.factstore.application

import com.factstore.core.domain.AllowlistEntryStatus
import com.factstore.core.domain.EnvironmentAllowlistEntry
import com.factstore.core.port.inbound.IEnvironmentAllowlistService
import com.factstore.core.port.outbound.IEnvironmentAllowlistRepository
import com.factstore.core.port.outbound.IEnvironmentRepository
import com.factstore.dto.AllowlistEntryResponse
import com.factstore.dto.CreateAllowlistEntryRequest
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class EnvironmentAllowlistService(
    private val allowlistRepository: IEnvironmentAllowlistRepository,
    private val environmentRepository: IEnvironmentRepository
) : IEnvironmentAllowlistService {

    private val log = LoggerFactory.getLogger(EnvironmentAllowlistService::class.java)

    override fun addEntry(environmentId: UUID, request: CreateAllowlistEntryRequest): AllowlistEntryResponse {
        if (!environmentRepository.existsById(environmentId))
            throw NotFoundException("Environment not found: $environmentId")
        require(request.sha256 != null || request.namePattern != null) {
            "Either sha256 or namePattern must be provided"
        }
        val entry = EnvironmentAllowlistEntry(
            environmentId = environmentId,
            sha256 = request.sha256,
            namePattern = request.namePattern,
            reason = request.reason,
            approvedBy = request.approvedBy,
            expiresAt = request.expiresAt
        )
        val saved = allowlistRepository.save(entry)
        log.info("Added allowlist entry ${saved.id} to environment $environmentId")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun listEntries(environmentId: UUID): List<AllowlistEntryResponse> {
        if (!environmentRepository.existsById(environmentId))
            throw NotFoundException("Environment not found: $environmentId")
        return allowlistRepository.findAllByEnvironmentId(environmentId).map { it.toResponse() }
    }

    override fun removeEntry(environmentId: UUID, entryId: UUID): AllowlistEntryResponse {
        val entry = allowlistRepository.findById(entryId)
            ?: throw NotFoundException("Allowlist entry not found: $entryId")
        if (entry.environmentId != environmentId)
            throw NotFoundException("Entry not found in environment: $environmentId")
        if (entry.status == AllowlistEntryStatus.REMOVED)
            throw ConflictException("Entry already removed")
        entry.status = AllowlistEntryStatus.REMOVED
        val saved = allowlistRepository.save(entry)
        log.info("Removed allowlist entry $entryId from environment $environmentId")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    override fun isAllowlisted(environmentId: UUID, sha256: String?, artifactName: String?): Boolean {
        val activeEntries = allowlistRepository.findActiveByEnvironmentId(environmentId)
        return activeEntries.any { it.matches(sha256, artifactName) }
    }
}

fun EnvironmentAllowlistEntry.toResponse() = AllowlistEntryResponse(
    id = id,
    environmentId = environmentId,
    sha256 = sha256,
    namePattern = namePattern,
    reason = reason,
    approvedBy = approvedBy,
    createdAt = createdAt,
    expiresAt = expiresAt,
    status = status,
    isEffective = isEffective()
)
