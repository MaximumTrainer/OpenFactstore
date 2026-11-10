package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.Fact
import com.factstore.core.port.outbound.IFactRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface FactJpa : JpaRepository<Fact, UUID>

@Component
class FactRepositoryAdapter(private val jpa: FactJpa) : IFactRepository {
    override fun save(fact: Fact): Fact = jpa.save(fact)
    override fun findById(factId: UUID): Fact? = jpa.findById(factId).orElse(null)
    override fun existsById(factId: UUID): Boolean = jpa.existsById(factId)
}
