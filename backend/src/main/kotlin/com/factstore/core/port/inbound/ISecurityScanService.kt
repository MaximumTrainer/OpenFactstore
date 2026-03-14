package com.factstore.core.port.inbound

import com.factstore.core.domain.SecurityScanResult
import com.factstore.core.domain.SecurityThreshold
import com.factstore.dto.*
import java.util.UUID

interface ISecurityScanService {
    fun recordScan(trailId: UUID, request: RecordSecurityScanRequest): SecurityScanResponse
    fun listScans(trailId: UUID): List<SecurityScanResponse>
    fun getScan(id: UUID): SecurityScanResponse
    fun getSummary(): SecurityScanSummaryResponse
    fun setThresholds(flowId: UUID, request: SetSecurityThresholdRequest): SecurityThresholdResponse
    fun getThresholds(flowId: UUID): SecurityThresholdResponse
    fun evaluateThresholds(scanResult: SecurityScanResult, threshold: SecurityThreshold): ThresholdEvaluationResult
}
