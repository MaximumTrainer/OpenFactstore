package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.ConfluenceConfig
import com.factstore.core.port.outbound.IConfluenceConfigRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ConfluenceConfigRepositoryJpa : JpaRepository<ConfluenceConfig, UUID> {
    fun findFirstByOrderByCreatedAtAsc(): ConfluenceConfig?
}

@Component
class ConfluenceConfigRepositoryAdapter(private val jpa: ConfluenceConfigRepositoryJpa) : IConfluenceConfigRepository {
    override fun save(config: ConfluenceConfig): ConfluenceConfig = jpa.save(config)
    override fun findFirst(): ConfluenceConfig? = jpa.findFirstByOrderByCreatedAtAsc()
    override fun findById(id: UUID): ConfluenceConfig? = jpa.findById(id).orElse(null)
    override fun deleteAll() = jpa.deleteAll()
}
