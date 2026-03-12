package com.factstore.application

import com.factstore.core.domain.EvidenceFile
import com.factstore.core.port.inbound.IEvidenceVaultService
import com.factstore.core.port.outbound.IEvidenceFileRepository
import com.factstore.dto.EvidenceFileResponse
import com.factstore.exception.IntegrityException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.UUID

@Service
@Transactional
class EvidenceVaultService(private val evidenceFileRepository: IEvidenceFileRepository) : IEvidenceVaultService {

    private val log = LoggerFactory.getLogger(EvidenceVaultService::class.java)

    override fun store(
        attestationId: UUID,
        fileName: String,
        contentType: String,
        content: ByteArray
    ): EvidenceFile {
        val hash = computeSha256(content)
        val evidenceFile = EvidenceFile(
            attestationId = attestationId,
            fileName = fileName,
            sha256Hash = hash,
            fileSizeBytes = content.size.toLong(),
            contentType = contentType,
            content = content
        )
        val saved = evidenceFileRepository.save(evidenceFile)
        log.info("Stored evidence file: ${saved.id} sha256=$hash size=${content.size}")
        return saved
    }

    override fun storeExternal(
        attestationId: UUID,
        fileName: String,
        contentType: String,
        externalUrl: String,
        sha256Hash: String,
        fileSizeBytes: Long
    ): EvidenceFile {
        val evidenceFile = EvidenceFile(
            attestationId = attestationId,
            fileName = fileName,
            sha256Hash = sha256Hash,
            fileSizeBytes = fileSizeBytes,
            contentType = contentType,
            content = null,
            externalUrl = externalUrl
        )
        val saved = evidenceFileRepository.save(evidenceFile)
        log.info("Stored external evidence reference: ${saved.id} url=$externalUrl sha256=$sha256Hash")
        return saved
    }

    @Transactional(readOnly = true)
    override fun findByAttestationId(attestationId: UUID): List<EvidenceFile> =
        evidenceFileRepository.findByAttestationId(attestationId)

    @Transactional(readOnly = true)
    override fun verifyIntegrity(id: UUID): Boolean {
        val file = evidenceFileRepository.findById(id) ?: return false
        if (file.content == null) {
            // External references cannot have their content verified server-side
            log.warn("Integrity check skipped for external evidence file: $id (url=${file.externalUrl})")
            return true
        }
        val recomputed = computeSha256(file.content)
        if (recomputed != file.sha256Hash) {
            throw IntegrityException("Evidence file $id integrity check failed: stored=${file.sha256Hash} computed=$recomputed")
        }
        return true
    }

    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

fun EvidenceFile.toResponse() = EvidenceFileResponse(
    id = id,
    attestationId = attestationId,
    fileName = fileName,
    sha256Hash = sha256Hash,
    fileSizeBytes = fileSizeBytes,
    contentType = contentType,
    storedAt = storedAt,
    externalUrl = externalUrl
)
