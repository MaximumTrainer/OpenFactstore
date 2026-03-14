package com.factstore.core.port.outbound

import com.factstore.core.domain.SecurityScanResult
import java.util.UUID

interface ISecurityScanRepository {
    fun save(scan: SecurityScanResult): SecurityScanResult
    fun findById(id: UUID): SecurityScanResult?
    fun findByTrailId(trailId: UUID): List<SecurityScanResult>
    fun findAll(): List<SecurityScanResult>
}
