package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.AllowlistEntryStatus
import com.factstore.core.domain.EnvironmentAllowlistEntry
import com.factstore.core.port.outbound.IEnvironmentAllowlistRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EnvironmentAllowlistRepositoryJpa : JpaRepository<EnvironmentAllowlistEntry, UUID> {
    fun findAllByEnvironmentId(environmentId: UUID): List<EnvironmentAllowlistEntry>
    fun findAllByEnvironmentIdAndStatus(environmentId: UUID, status: AllowlistEntryStatus): List<EnvironmentAllowlistEntry>
}

@Component
class EnvironmentAllowlistRepositoryAdapter(
    private val jpa: EnvironmentAllowlistRepositoryJpa
) : IEnvironmentAllowlistRepository {
    override fun save(entry: EnvironmentAllowlistEntry) = jpa.save(entry)
    override fun findById(id: UUID): EnvironmentAllowlistEntry? = jpa.findById(id).orElse(null)
    override fun findAllByEnvironmentId(environmentId: UUID) = jpa.findAllByEnvironmentId(environmentId)
    override fun findActiveByEnvironmentId(environmentId: UUID) =
        jpa.findAllByEnvironmentIdAndStatus(environmentId, AllowlistEntryStatus.ACTIVE)
}
