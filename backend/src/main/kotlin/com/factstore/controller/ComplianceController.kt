package com.factstore.controller

import com.factstore.dto.ChainOfCustodyResponse
import com.factstore.service.ComplianceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/compliance")
@Tag(name = "Compliance", description = "Compliance and chain of custody")
class ComplianceController(private val complianceService: ComplianceService) {

    @GetMapping("/artifact/{sha256Digest}")
    @Operation(summary = "Get chain of custody for an artifact")
    fun getChainOfCustody(@PathVariable sha256Digest: String): ResponseEntity<ChainOfCustodyResponse> =
        ResponseEntity.ok(complianceService.getChainOfCustody(sha256Digest))
}
