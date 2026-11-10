package com.factstore.adapter.mock

import com.factstore.core.domain.Fact
import com.factstore.core.port.outbound.IFactRepository
import java.util.UUID

class InMemoryFactRepository : IFactRepository {
    private val store = mutableMapOf<UUID, Fact>()
    override fun save(fact: Fact): Fact = fact.also { store[it.factId] = it }
    override fun findById(factId: UUID): Fact? = store[factId]
    override fun existsById(factId: UUID): Boolean = factId in store
}
