package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.SlackConfig
import com.factstore.core.port.outbound.ISlackConfigRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SlackConfigRepositoryJpa : JpaRepository<SlackConfig, UUID> {
    fun findByOrgSlug(orgSlug: String): SlackConfig?
    fun deleteByOrgSlug(orgSlug: String)
    fun existsByOrgSlug(orgSlug: String): Boolean
}

@Component
class SlackConfigRepositoryAdapter(private val jpa: SlackConfigRepositoryJpa) : ISlackConfigRepository {
    override fun save(config: SlackConfig): SlackConfig = jpa.save(config)
    override fun findByOrgSlug(orgSlug: String): SlackConfig? = jpa.findByOrgSlug(orgSlug)
    override fun deleteByOrgSlug(orgSlug: String) = jpa.deleteByOrgSlug(orgSlug)
    override fun existsByOrgSlug(orgSlug: String): Boolean = jpa.existsByOrgSlug(orgSlug)
}
