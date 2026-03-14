package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.RegulatoryControl
import com.factstore.core.port.outbound.IRegulatoryControlRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RegulatoryControlRepositoryJpa : JpaRepository<RegulatoryControl, UUID> {
    fun findByFrameworkId(frameworkId: UUID): List<RegulatoryControl>
}

@Component
class RegulatoryControlRepositoryAdapter(private val jpa: RegulatoryControlRepositoryJpa) : IRegulatoryControlRepository {
    override fun save(control: RegulatoryControl): RegulatoryControl = jpa.save(control)
    override fun findById(id: UUID): RegulatoryControl? = jpa.findById(id).orElse(null)
    override fun findByFrameworkId(frameworkId: UUID): List<RegulatoryControl> = jpa.findByFrameworkId(frameworkId)
}
