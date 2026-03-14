package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.SsoConfig
import com.factstore.core.port.outbound.ISsoConfigRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SsoConfigRepositoryJpa : JpaRepository<SsoConfig, UUID> {
    fun findByOrgSlug(orgSlug: String): SsoConfig?
    fun existsByOrgSlug(orgSlug: String): Boolean
}

@Component
class SsoConfigRepositoryAdapter(private val jpa: SsoConfigRepositoryJpa) : ISsoConfigRepository {
    override fun save(ssoConfig: SsoConfig): SsoConfig = jpa.save(ssoConfig)
    override fun findByOrgSlug(orgSlug: String): SsoConfig? = jpa.findByOrgSlug(orgSlug)
    override fun existsByOrgSlug(orgSlug: String): Boolean = jpa.existsByOrgSlug(orgSlug)
    override fun delete(ssoConfig: SsoConfig) = jpa.delete(ssoConfig)
}
