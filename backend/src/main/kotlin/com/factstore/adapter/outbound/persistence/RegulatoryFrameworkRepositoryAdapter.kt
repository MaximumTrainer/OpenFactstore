package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.RegulatoryFramework
import com.factstore.core.port.outbound.IRegulatoryFrameworkRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RegulatoryFrameworkRepositoryJpa : JpaRepository<RegulatoryFramework, UUID> {
    fun findByIsActiveTrue(): List<RegulatoryFramework>
    fun existsByName(name: String): Boolean
}

@Component
class RegulatoryFrameworkRepositoryAdapter(private val jpa: RegulatoryFrameworkRepositoryJpa) : IRegulatoryFrameworkRepository {
    override fun save(framework: RegulatoryFramework): RegulatoryFramework = jpa.save(framework)
    override fun findById(id: UUID): RegulatoryFramework? = jpa.findById(id).orElse(null)
    override fun findAll(): List<RegulatoryFramework> = jpa.findAll()
    override fun findActive(): List<RegulatoryFramework> = jpa.findByIsActiveTrue()
    override fun existsByName(name: String): Boolean = jpa.existsByName(name)
}
