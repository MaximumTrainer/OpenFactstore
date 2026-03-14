package com.factstore.adapter.mock

import com.factstore.core.domain.SecurityScanResult
import com.factstore.core.port.outbound.ISecurityScanRepository
import java.util.UUID

class InMemorySecurityScanRepository : ISecurityScanRepository {
    private val store = mutableMapOf<UUID, SecurityScanResult>()
    override fun save(scan: SecurityScanResult): SecurityScanResult { store[scan.id] = scan; return scan }
    override fun findById(id: UUID): SecurityScanResult? = store[id]
    override fun findByTrailId(trailId: UUID): List<SecurityScanResult> = store.values.filter { it.trailId == trailId }
    override fun findAll(): List<SecurityScanResult> = store.values.toList()
}
