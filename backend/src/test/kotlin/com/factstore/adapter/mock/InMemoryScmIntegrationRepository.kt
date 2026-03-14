package com.factstore.adapter.mock

import com.factstore.core.domain.ScmIntegration
import com.factstore.core.domain.ScmProvider
import com.factstore.core.port.outbound.IScmIntegrationRepository
import java.util.UUID

class InMemoryScmIntegrationRepository : IScmIntegrationRepository {
    private val store = mutableMapOf<UUID, ScmIntegration>()

    override fun save(integration: ScmIntegration): ScmIntegration {
        store[integration.id] = integration
        return integration
    }

    override fun findById(id: UUID): ScmIntegration? = store[id]

    override fun findByOrgSlug(orgSlug: String): List<ScmIntegration> =
        store.values.filter { it.orgSlug == orgSlug }

    override fun findByOrgSlugAndProvider(orgSlug: String, provider: ScmProvider): ScmIntegration? =
        store.values.find { it.orgSlug == orgSlug && it.provider == provider }

    override fun deleteByOrgSlugAndProvider(orgSlug: String, provider: ScmProvider) {
        store.entries.removeIf { it.value.orgSlug == orgSlug && it.value.provider == provider }
    }

    override fun existsByOrgSlugAndProvider(orgSlug: String, provider: ScmProvider): Boolean =
        store.values.any { it.orgSlug == orgSlug && it.provider == provider }
}
