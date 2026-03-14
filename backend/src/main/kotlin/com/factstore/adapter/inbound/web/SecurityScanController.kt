package com.factstore.adapter.inbound.web

import com.factstore.core.port.inbound.ISecurityScanService
import com.factstore.dto.*
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class SecurityScanController(private val securityScanService: ISecurityScanService) {

    @PostMapping("/trails/{trailId}/security-scans")
    @ResponseStatus(HttpStatus.CREATED)
    fun recordScan(@PathVariable trailId: UUID, @RequestBody request: RecordSecurityScanRequest): SecurityScanResponse =
        securityScanService.recordScan(trailId, request)

    @GetMapping("/trails/{trailId}/security-scans")
    fun listScans(@PathVariable trailId: UUID): List<SecurityScanResponse> =
        securityScanService.listScans(trailId)

    @GetMapping("/security-scans/{id}")
    fun getScan(@PathVariable id: UUID): SecurityScanResponse =
        securityScanService.getScan(id)

    @GetMapping("/security-scans/summary")
    fun getSummary(): SecurityScanSummaryResponse =
        securityScanService.getSummary()

    @PostMapping("/flows/{flowId}/security-thresholds")
    @ResponseStatus(HttpStatus.CREATED)
    fun setThresholds(@PathVariable flowId: UUID, @RequestBody request: SetSecurityThresholdRequest): SecurityThresholdResponse =
        securityScanService.setThresholds(flowId, request)

    @GetMapping("/flows/{flowId}/security-thresholds")
    fun getThresholds(@PathVariable flowId: UUID): SecurityThresholdResponse =
        securityScanService.getThresholds(flowId)
}
