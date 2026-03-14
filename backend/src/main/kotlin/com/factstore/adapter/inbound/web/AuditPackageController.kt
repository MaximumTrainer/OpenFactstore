package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.IAuditPackageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@Tag(name = "Audit Package", description = "Downloadable tamper-evident audit bundles")
class AuditPackageController(private val auditPackageService: IAuditPackageService) {

    @GetMapping("/api/v1/trails/{trailId}/audit-package")
    @Operation(summary = "Download a tar.gz audit package for a trail")
    fun downloadForTrail(@PathVariable trailId: UUID): ResponseEntity<ByteArray> =
        archiveResponse("trail-$trailId-audit-package.tar.gz", auditPackageService.buildForTrail(trailId))

    @GetMapping("/api/v1/artifacts/{artifactId}/audit-package")
    @Operation(summary = "Download a tar.gz audit package for an artifact")
    fun downloadForArtifact(@PathVariable artifactId: UUID): ResponseEntity<ByteArray> =
        archiveResponse("artifact-$artifactId-audit-package.tar.gz", auditPackageService.buildForArtifact(artifactId))

    @GetMapping("/api/v1/attestations/{attestationId}/audit-package")
    @Operation(summary = "Download a tar.gz audit package for an attestation")
    fun downloadForAttestation(@PathVariable attestationId: UUID): ResponseEntity<ByteArray> =
        archiveResponse(
            "attestation-$attestationId-audit-package.tar.gz",
            auditPackageService.buildForAttestation(attestationId)
        )

    private fun archiveResponse(filename: String, bytes: ByteArray): ResponseEntity<ByteArray> =
        ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.parseMediaType("application/gzip"))
            .body(bytes)
}
