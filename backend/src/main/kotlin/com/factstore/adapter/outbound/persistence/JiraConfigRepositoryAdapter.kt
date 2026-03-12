package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.JiraConfig
import com.factstore.core.port.outbound.IJiraConfigRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface JiraConfigRepositoryJpa : JpaRepository<JiraConfig, UUID> {
    fun findFirstByOrderByCreatedAtAsc(): JiraConfig?
}

@Component
class JiraConfigRepositoryAdapter(private val jpa: JiraConfigRepositoryJpa) : IJiraConfigRepository {
    override fun save(config: JiraConfig): JiraConfig = jpa.save(config)
    override fun findFirst(): JiraConfig? = jpa.findFirstByOrderByCreatedAtAsc()
    override fun findById(id: UUID): JiraConfig? = jpa.findById(id).orElse(null)
    override fun deleteAll() = jpa.deleteAll()
}
