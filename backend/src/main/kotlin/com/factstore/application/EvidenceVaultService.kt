package com.factstore.application

import com.factstore.core.domain.EvidenceFile
import com.factstore.core.port.inbound.IEvidenceVaultService
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IEvidenceFileRepository
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.EvidenceFileResponse
import com.factstore.exception.IntegrityException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.UUID

@Service
@Transactional
class EvidenceVaultService(
    private val evidenceFileRepository: IEvidenceFileRepository,
    private val attestationRepository: IAttestationRepository? = null,
    private val trailRepository: ITrailRepository? = null,
    private val flowRepository: IFlowRepository? = null,
    private val tenantScope: TenantScope = TenantScope.UNSCOPED
) : IEvidenceVaultService {

    /**
     * Evidence belongs to the organisation of its attestation, falling back to the trail's and
     * then the flow's. Another organisation's evidence is "not found" (#155 FR-5.2).
     */
    private fun orgOfAttestation(attestationId: UUID): String? {
        val attestation = attestationRepository?.findById(attestationId) ?: return null
        attestation.orgSlug?.let { return it }
        val trail = trailRepository?.findById(attestation.trailId) ?: return null
        return trail.orgSlug ?: flowRepository?.findById(trail.flowId)?.orgSlug
    }

    private fun visible(file: EvidenceFile): Boolean =
        tenantScope.orgSlug() == null || tenantScope.visible(orgOfAttestation(file.attestationId))

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
        log.info("Stored external evidence reference: ${saved.id} host=${redactUrl(externalUrl)} sha256=$sha256Hash")
        return saved
    }

    @Transactional(readOnly = true)
    override fun findByAttestationId(attestationId: UUID): List<EvidenceFileResponse> =
        evidenceFileRepository.findByAttestationId(attestationId).filter(::visible).map { it.toResponse() }

    @Transactional(readOnly = true)
    override fun verifyIntegrity(id: UUID): Boolean {
        val file = evidenceFileRepository.findById(id) ?: return false
        if (file.content == null) {
            // External-URL references store only metadata (hash, size) here; the binary
            // lives on the customer's own infrastructure and cannot be re-fetched by the
            // server.  We return true to indicate that the record itself is consistent,
            // but callers should be aware that the remote file has NOT been re-hashed.
            log.warn("Integrity check skipped for external evidence file: $id (host=${redactUrl(file.externalUrl)})")
            return true
        }
        val recomputed = computeSha256(file.content)
        if (recomputed != file.sha256Hash) {
            throw IntegrityException("Evidence file $id integrity check failed: stored=${file.sha256Hash} computed=$recomputed")
        }
        return true
    }

    @Transactional(readOnly = true)
    override fun findBySha256Hash(sha256Hash: String): EvidenceFile? =
        evidenceFileRepository.findTopBySha256HashOrderedByStoredAt(sha256Hash)?.takeIf(::visible)

    @Transactional(readOnly = true)
    override fun findByTrailId(trailId: UUID): List<EvidenceFileResponse> =
        evidenceFileRepository.findByTrailId(trailId).filter(::visible).map { it.toResponse() }

    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Strips query parameters and fragments from a URL before logging to prevent leaking
     * pre-signed credentials or other sensitive query parameters (e.g., AWS S3 X-Amz-Signature).
     * Falls back to scheme + authority when path parsing fails, and "<redacted>" only when
     * the URL cannot be parsed at all.
     */
    private fun redactUrl(url: String?): String? {
        if (url == null) return null
        return try {
            val uri = java.net.URI(url)
            val safe = java.net.URI(uri.scheme, uri.authority, uri.path, null, null)
            safe.toString()
        } catch (_: Exception) {
            // URL is malformed; log only scheme+authority if we can extract them cheaply,
            // otherwise fall back to a generic placeholder.
            val schemeEnd = url.indexOf("://")
            val hostEnd = if (schemeEnd >= 0) url.indexOf('/', schemeEnd + 3).takeIf { it >= 0 } ?: url.length else -1
            if (hostEnd > 0) url.substring(0, hostEnd) + "/<path-redacted>" else "<redacted>"
        }
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
