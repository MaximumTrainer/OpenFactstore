package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.DataKey
import com.factstore.core.domain.ShredRequest
import com.factstore.core.port.outbound.IDataKeyRepository
import com.factstore.core.port.outbound.IShredRequestRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DataKeyJpa : JpaRepository<DataKey, UUID> {
    fun findByTenantAndPeriod(tenant: String, period: String): DataKey?
    fun findAllByOrderByTenantAscPeriodAsc(): List<DataKey>
}

@Repository
interface ShredRequestJpa : JpaRepository<ShredRequest, UUID>

@Component
class DataKeyRepositoryAdapter(private val jpa: DataKeyJpa) : IDataKeyRepository {
    override fun save(key: DataKey): DataKey = jpa.save(key)
    override fun find(tenant: String, period: String): DataKey? = jpa.findByTenantAndPeriod(tenant, period)
    override fun findAll(): List<DataKey> = jpa.findAllByOrderByTenantAscPeriodAsc()
}

@Component
class ShredRequestRepositoryAdapter(private val jpa: ShredRequestJpa) : IShredRequestRepository {
    override fun save(request: ShredRequest): ShredRequest = jpa.save(request)
    override fun findById(id: UUID): ShredRequest? = jpa.findById(id).orElse(null)
}
