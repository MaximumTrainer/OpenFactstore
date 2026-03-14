package com.factstore.core.port.outbound

import com.factstore.core.domain.ScmIntegration
import com.factstore.core.domain.ScmProvider
import java.util.UUID

interface IScmIntegrationRepository {
    fun save(integration: ScmIntegration): ScmIntegration
    fun findById(id: UUID): ScmIntegration?
    fun findByOrgSlug(orgSlug: String): List<ScmIntegration>
    fun findByOrgSlugAndProvider(orgSlug: String, provider: ScmProvider): ScmIntegration?
    fun deleteByOrgSlugAndProvider(orgSlug: String, provider: ScmProvider)
    fun existsByOrgSlugAndProvider(orgSlug: String, provider: ScmProvider): Boolean
}
