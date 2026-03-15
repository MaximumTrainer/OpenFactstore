package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.OidcJtiLog
import com.factstore.core.port.outbound.IOidcJtiLogRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

interface OidcJtiLogSpringRepository : JpaRepository<OidcJtiLog, UUID> {
    fun existsByJti(jti: String): Boolean
}

@Repository
class OidcJtiLogRepositoryAdapter(private val repo: OidcJtiLogSpringRepository) : IOidcJtiLogRepository {
    override fun existsByJti(jti: String): Boolean = repo.existsByJti(jti)
    override fun save(log: OidcJtiLog): OidcJtiLog = repo.save(log)
}
