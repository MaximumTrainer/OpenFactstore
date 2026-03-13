package com.factstore.adapter.mock

import com.factstore.core.domain.Environment
import com.factstore.core.port.outbound.IEnvironmentRepository
import java.util.UUID

class InMemoryEnvironmentRepository : IEnvironmentRepository {
    private val store = mutableMapOf<UUID, Environment>()

    override fun save(environment: Environment): Environment {
        store[environment.id] = environment
        return environment
    }

    override fun findById(id: UUID): Environment? = store[id]

    override fun findAll(): List<Environment> = store.values.toList()

    override fun existsById(id: UUID): Boolean = store.containsKey(id)

    override fun existsByName(name: String): Boolean = store.values.any { it.name == name }

    override fun deleteById(id: UUID) {
        store.remove(id)
    }
}
