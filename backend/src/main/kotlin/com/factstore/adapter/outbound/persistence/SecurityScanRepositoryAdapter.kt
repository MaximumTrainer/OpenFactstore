package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.SecurityScanResult
import com.factstore.core.port.outbound.ISecurityScanRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SecurityScanRepositoryJpa : JpaRepository<SecurityScanResult, UUID> {
    fun findByTrailId(trailId: UUID): List<SecurityScanResult>
}

@Component
class SecurityScanRepositoryAdapter(private val jpa: SecurityScanRepositoryJpa) : ISecurityScanRepository {
    override fun save(scan: SecurityScanResult): SecurityScanResult = jpa.save(scan)
    override fun findById(id: UUID): SecurityScanResult? = jpa.findById(id).orElse(null)
    override fun findByTrailId(trailId: UUID): List<SecurityScanResult> = jpa.findByTrailId(trailId)
    override fun findAll(): List<SecurityScanResult> = jpa.findAll()
}
