package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.ScmIntegration
import com.factstore.core.domain.ScmProvider
import com.factstore.core.port.outbound.IScmIntegrationRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ScmIntegrationRepositoryJpa : JpaRepository<ScmIntegration, UUID> {
    fun findByOrgSlug(orgSlug: String): List<ScmIntegration>
    fun findByOrgSlugAndProvider(orgSlug: String, provider: ScmProvider): ScmIntegration?
    fun deleteByOrgSlugAndProvider(orgSlug: String, provider: ScmProvider)
    fun existsByOrgSlugAndProvider(orgSlug: String, provider: ScmProvider): Boolean
}

@Component
class ScmIntegrationRepositoryAdapter(private val jpa: ScmIntegrationRepositoryJpa) : IScmIntegrationRepository {
    override fun save(integration: ScmIntegration): ScmIntegration = jpa.save(integration)
    override fun findById(id: UUID): ScmIntegration? = jpa.findById(id).orElse(null)
    override fun findByOrgSlug(orgSlug: String): List<ScmIntegration> = jpa.findByOrgSlug(orgSlug)
    override fun findByOrgSlugAndProvider(orgSlug: String, provider: ScmProvider): ScmIntegration? =
        jpa.findByOrgSlugAndProvider(orgSlug, provider)
    override fun deleteByOrgSlugAndProvider(orgSlug: String, provider: ScmProvider) =
        jpa.deleteByOrgSlugAndProvider(orgSlug, provider)
    override fun existsByOrgSlugAndProvider(orgSlug: String, provider: ScmProvider): Boolean =
        jpa.existsByOrgSlugAndProvider(orgSlug, provider)
}
