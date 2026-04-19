package com.factstore.adapter.mock

import com.factstore.core.domain.Environment
import com.factstore.core.port.outbound.IEnvironmentRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.util.UUID

class InMemoryEnvironmentRepository : IEnvironmentRepository {
    private val store = mutableMapOf<UUID, Environment>()

    override fun save(environment: Environment): Environment {
        store[environment.id] = environment
        return environment
    }

    override fun findById(id: UUID): Environment? = store[id]

    override fun findAll(): List<Environment> = store.values.toList()

    override fun findAll(pageable: Pageable): Page<Environment> {
        val all = store.values.toList()
        val start = (pageable.pageNumber * pageable.pageSize).coerceAtMost(all.size)
        val end = (start + pageable.pageSize).coerceAtMost(all.size)
        return PageImpl(all.subList(start, end), pageable, all.size.toLong())
    }

    override fun existsById(id: UUID): Boolean = store.containsKey(id)

    override fun existsByName(name: String): Boolean = store.values.any { it.name == name }

    override fun deleteById(id: UUID) {
        store.remove(id)
    }

    override fun findEnvironmentsWithArtifact(sha256: String): List<UUID> = emptyList()
}
