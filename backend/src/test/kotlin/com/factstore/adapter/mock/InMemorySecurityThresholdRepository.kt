package com.factstore.adapter.mock

import com.factstore.core.domain.SecurityThreshold
import com.factstore.core.port.outbound.ISecurityThresholdRepository
import java.util.UUID

class InMemorySecurityThresholdRepository : ISecurityThresholdRepository {
    private val store = mutableMapOf<UUID, SecurityThreshold>()
    override fun save(threshold: SecurityThreshold): SecurityThreshold { store[threshold.id] = threshold; return threshold }
    override fun findById(id: UUID): SecurityThreshold? = store[id]
    override fun findByFlowId(flowId: UUID): SecurityThreshold? = store.values.find { it.flowId == flowId }
    fun findAll(): List<SecurityThreshold> = store.values.toList()
}
