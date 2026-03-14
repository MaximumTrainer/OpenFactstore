package com.factstore.application

import com.factstore.core.domain.EvidenceFile
import com.factstore.core.port.inbound.IAuditPackageService
import com.factstore.core.port.outbound.IArtifactRepository
import com.factstore.core.port.outbound.IAttestationRepository
import com.factstore.core.port.outbound.IEvidenceFileRepository
import com.factstore.core.port.outbound.IFlowRepository
import com.factstore.core.port.outbound.ITrailRepository
import com.factstore.dto.AuditManifest
import com.factstore.dto.AuditManifestEntry
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.GZIPOutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
@Transactional(readOnly = true)
class AuditPackageService(
    private val trailRepository: ITrailRepository,
    private val flowRepository: IFlowRepository,
    private val artifactRepository: IArtifactRepository,
    private val attestationRepository: IAttestationRepository,
    private val evidenceFileRepository: IEvidenceFileRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${audit.hmac-secret}") private val hmacSecret: String
) : IAuditPackageService {

    private val log = LoggerFactory.getLogger(AuditPackageService::class.java)

    init {
        if (hmacSecret == "change-me-in-production") {
            log.warn(
                "SECURITY WARNING: audit.hmac-secret is set to the insecure default value. " +
                "Set the AUDIT_HMAC_SECRET environment variable to a strong random secret before deploying to production."
            )
        }
    }

    override fun buildForTrail(trailId: UUID): ByteArray {
        val trail = trailRepository.findById(trailId) ?: throw NotFoundException("Trail not found: $trailId")
        val flow = flowRepository.findById(trail.flowId) ?: throw NotFoundException("Flow not found: ${trail.flowId}")
        val artifacts = artifactRepository.findByTrailId(trailId)
        val attestations = attestationRepository.findByTrailId(trailId)
        // Use the trail-scoped repository method to avoid N+1 queries
        val evidenceFiles = evidenceFileRepository.findByTrailId(trailId)

        log.info("Building audit package for trail=$trailId")
        return buildArchive(
            trailId = trailId,
            trailJson = objectMapper.writeValueAsBytes(trail.toResponse()),
            flowJson = objectMapper.writeValueAsBytes(flow.toResponse()),
            artifactEntries = artifacts.map { it.id.toString() to objectMapper.writeValueAsBytes(it.toResponse()) },
            attestationEntries = attestations.map { it.id.toString() to objectMapper.writeValueAsBytes(it.toResponse()) },
            evidenceFiles = evidenceFiles
        )
    }

    override fun buildForArtifact(artifactId: UUID): ByteArray {
        val artifact = artifactRepository.findById(artifactId)
            ?: throw NotFoundException("Artifact not found: $artifactId")
        val trail = trailRepository.findById(artifact.trailId)
            ?: throw NotFoundException("Trail not found: ${artifact.trailId}")
        val flow = flowRepository.findById(trail.flowId)
            ?: throw NotFoundException("Flow not found: ${trail.flowId}")
        val attestations = attestationRepository.findByTrailId(artifact.trailId)
        // Use the trail-scoped repository method to avoid N+1 queries
        val evidenceFiles = evidenceFileRepository.findByTrailId(artifact.trailId)

        log.info("Building audit package for artifact=$artifactId")
        return buildArchive(
            trailId = trail.id,
            trailJson = objectMapper.writeValueAsBytes(trail.toResponse()),
            flowJson = objectMapper.writeValueAsBytes(flow.toResponse()),
            artifactEntries = listOf(artifact.id.toString() to objectMapper.writeValueAsBytes(artifact.toResponse())),
            attestationEntries = attestations.map { it.id.toString() to objectMapper.writeValueAsBytes(it.toResponse()) },
            evidenceFiles = evidenceFiles
        )
    }

    override fun buildForAttestation(attestationId: UUID): ByteArray {
        val attestation = attestationRepository.findById(attestationId)
            ?: throw NotFoundException("Attestation not found: $attestationId")
        val trail = trailRepository.findById(attestation.trailId)
            ?: throw NotFoundException("Trail not found: ${attestation.trailId}")
        val flow = flowRepository.findById(trail.flowId)
            ?: throw NotFoundException("Flow not found: ${trail.flowId}")
        val artifacts = artifactRepository.findByTrailId(attestation.trailId)
        val evidenceFiles = evidenceFileRepository.findByAttestationId(attestationId)

        log.info("Building audit package for attestation=$attestationId")
        return buildArchive(
            trailId = trail.id,
            trailJson = objectMapper.writeValueAsBytes(trail.toResponse()),
            flowJson = objectMapper.writeValueAsBytes(flow.toResponse()),
            artifactEntries = artifacts.map { it.id.toString() to objectMapper.writeValueAsBytes(it.toResponse()) },
            attestationEntries = listOf(attestation.id.toString() to objectMapper.writeValueAsBytes(attestation.toResponse())),
            evidenceFiles = evidenceFiles
        )
    }

    private fun buildArchive(
        trailId: UUID,
        trailJson: ByteArray,
        flowJson: ByteArray,
        artifactEntries: List<Pair<String, ByteArray>>,
        attestationEntries: List<Pair<String, ByteArray>>,
        evidenceFiles: List<EvidenceFile>
    ): ByteArray {
        // Collect (path -> bytes) for all content files (excluding manifest.json itself).
        // Use a LinkedHashMap so that if two entries map to the same archive path (e.g.
        // two attestations attach content with the same sha256), the first one wins and
        // the path is included exactly once, keeping the archive and manifest unambiguous.
        val contentEntriesMap = linkedMapOf<String, ByteArray>()
        contentEntriesMap["trail.json"] = trailJson
        contentEntriesMap["flow.json"] = flowJson
        for ((id, bytes) in artifactEntries) {
            contentEntriesMap["artifacts/$id.json"] = bytes
        }
        for ((id, bytes) in attestationEntries) {
            contentEntriesMap["attestations/$id.json"] = bytes
        }
        for (ev in evidenceFiles) {
            if (ev.content != null) {
                val ext = ev.fileName.substringAfterLast('.', "")
                val name = if (ext.isNotEmpty()) "${ev.sha256Hash}.$ext" else ev.sha256Hash
                contentEntriesMap.putIfAbsent("evidence/$name", ev.content)
            } else {
                // External reference: include a JSON descriptor with all available metadata
                // so auditors can locate and verify the file from outside the vault.
                val refJson = objectMapper.writeValueAsBytes(
                    mapOf(
                        "type" to "external-reference",
                        "fileName" to ev.fileName,
                        "sha256Hash" to ev.sha256Hash,
                        "fileSizeBytes" to ev.fileSizeBytes,
                        "contentType" to ev.contentType,
                        "externalUrl" to ev.externalUrl
                    )
                )
                contentEntriesMap.putIfAbsent("evidence/${ev.sha256Hash}.ref.json", refJson)
            }
        }
        val contentEntries = contentEntriesMap.entries.map { it.key to it.value }

        // Build manifest entries (sha256 of each content file)
        val manifestEntries = contentEntries.map { (path, bytes) ->
            AuditManifestEntry(path = path, sha256 = sha256Hex(bytes), sizeBytes = bytes.size.toLong())
        }

        // Sign the manifest entries list
        val manifestEntriesJson = objectMapper.writeValueAsBytes(manifestEntries)
        val hmacSignature = computeHmac(manifestEntriesJson)

        val manifest = AuditManifest(
            generatedAt = Instant.now().toString(),
            trailId = trailId.toString(),
            files = manifestEntries,
            hmacSha256 = hmacSignature
        )
        val manifestBytes = objectMapper.writeValueAsBytes(manifest)

        // Assemble tar.gz — TarArchiveOutputStream must be closed (not just finish()-ed) to
        // flush internal buffers and avoid a potentially truncated/corrupted archive.
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gzip ->
            TarArchiveOutputStream(gzip).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
                addTarEntry(tar, "manifest.json", manifestBytes)
                for ((path, bytes) in contentEntries) {
                    addTarEntry(tar, path, bytes)
                }
            }
        }
        return baos.toByteArray()
    }

    private fun addTarEntry(tar: TarArchiveOutputStream, name: String, content: ByteArray) {
        val entry = TarArchiveEntry(name)
        entry.size = content.size.toLong()
        tar.putArchiveEntry(entry)
        tar.write(content)
        tar.closeArchiveEntry()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun computeHmac(data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(hmacSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }
}
