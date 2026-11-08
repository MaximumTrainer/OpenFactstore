package com.factstore.adapter.inbound.web

import org.springframework.security.access.prepost.PreAuthorize
import com.factstore.core.port.inbound.IComplianceService
import com.factstore.dto.ChainOfCustodyResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/compliance")
@Tag(name = "Compliance", description = "Compliance and chain of custody")
class ComplianceController(private val complianceService: IComplianceService) {

    @PreAuthorize("@authz.has('trails:read')")
    @GetMapping("/artifact/{sha256Digest}")
    @Operation(summary = "Get chain of custody for an artifact")
    fun getChainOfCustody(@PathVariable sha256Digest: String): ResponseEntity<ChainOfCustodyResponse> =
        ResponseEntity.ok(complianceService.getChainOfCustody(sha256Digest))
}
