package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IEvidenceVaultService
import com.factstore.dto.EvidenceFileResponse
import com.factstore.exception.BadRequestException
import com.factstore.exception.NotFoundException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.util.UUID

@RestController
@Tag(name = "Evidence Vault", description = "Evidence file storage and retrieval")
class EvidenceVaultController(private val evidenceVaultService: IEvidenceVaultService) {

    private val log = LoggerFactory.getLogger(EvidenceVaultController::class.java)

    @GetMapping("/api/v1/evidence/{sha256Hash}")
    @Operation(summary = "Download an evidence file by its SHA-256 hash")
    fun getByHash(@PathVariable sha256Hash: String): ResponseEntity<*> {
        val file = evidenceVaultService.findBySha256Hash(sha256Hash)
            ?: throw NotFoundException("Evidence file not found for hash: $sha256Hash")

        return if (file.content != null) {
            val mediaType = try {
                MediaType.parseMediaType(file.contentType)
            } catch (e: IllegalArgumentException) {
                log.debug("Unrecognised content-type '${file.contentType}' for evidence $sha256Hash; falling back to octet-stream", e)
                MediaType.APPLICATION_OCTET_STREAM
            }
            ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"${file.fileName}\"")
                .contentType(mediaType)
                .body(file.content)
        } else {
            val location = try {
                URI.create(file.externalUrl!!)
            } catch (_: IllegalArgumentException) {
                throw BadRequestException("Stored external URL for evidence hash $sha256Hash is not a valid URI")
            }
            ResponseEntity.status(HttpStatus.FOUND)
                .location(location)
                .build<Void>()
        }
    }

    @GetMapping("/api/v1/trails/{trailId}/evidence")
    @Operation(summary = "List all evidence files for a trail")
    fun listByTrail(@PathVariable trailId: UUID): ResponseEntity<List<EvidenceFileResponse>> =
        ResponseEntity.ok(evidenceVaultService.findByTrailId(trailId))

    @GetMapping("/api/v1/attestations/{attestationId}/evidence")
    @Operation(summary = "List evidence files for an attestation")
    fun listByAttestation(@PathVariable attestationId: UUID): ResponseEntity<List<EvidenceFileResponse>> =
        ResponseEntity.ok(evidenceVaultService.findByAttestationId(attestationId))
}
