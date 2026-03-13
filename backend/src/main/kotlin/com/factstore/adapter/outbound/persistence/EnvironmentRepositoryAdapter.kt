package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.Environment
import com.factstore.core.port.outbound.IEnvironmentRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EnvironmentRepositoryJpa : JpaRepository<Environment, UUID> {
    fun existsByName(name: String): Boolean
}

@Component
class EnvironmentRepositoryAdapter(private val jpa: EnvironmentRepositoryJpa) : IEnvironmentRepository {
    override fun save(environment: Environment): Environment = jpa.save(environment)
    override fun findById(id: UUID): Environment? = jpa.findById(id).orElse(null)
    override fun findAll(): List<Environment> = jpa.findAll()
    override fun existsById(id: UUID): Boolean = jpa.existsById(id)
    override fun existsByName(name: String): Boolean = jpa.existsByName(name)
    override fun deleteById(id: UUID) = jpa.deleteById(id)
}
