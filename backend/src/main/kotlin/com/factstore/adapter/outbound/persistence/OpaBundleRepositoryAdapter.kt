package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.BundleStatus
import com.factstore.core.domain.OpaBundle
import com.factstore.core.port.outbound.IOpaBundleRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface OpaBundleRepositoryJpa : JpaRepository<OpaBundle, UUID> {
    fun findFirstByStatus(status: BundleStatus): OpaBundle?
}

@Component
class OpaBundleRepositoryAdapter(private val jpa: OpaBundleRepositoryJpa) : IOpaBundleRepository {
    override fun save(bundle: OpaBundle): OpaBundle = jpa.save(bundle)
    override fun findById(id: UUID): OpaBundle? = jpa.findById(id).orElse(null)
    override fun findAll(): List<OpaBundle> = jpa.findAll()
    override fun findActive(): OpaBundle? = jpa.findFirstByStatus(BundleStatus.ACTIVE)
}
