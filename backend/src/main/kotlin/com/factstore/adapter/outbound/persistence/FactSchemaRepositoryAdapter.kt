package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.FactSchema
import com.factstore.core.port.outbound.IFactSchemaRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface FactSchemaJpa : JpaRepository<FactSchema, UUID> {
    fun findByNameAndVersion(name: String, version: Int): FactSchema?
    fun findByNameOrderByVersion(name: String): List<FactSchema>
}

@Component
class FactSchemaRepositoryAdapter(private val jpa: FactSchemaJpa) : IFactSchemaRepository {
    override fun save(schema: FactSchema): FactSchema = jpa.save(schema)
    override fun find(name: String, version: Int): FactSchema? = jpa.findByNameAndVersion(name, version)
    override fun findAllByName(name: String): List<FactSchema> = jpa.findByNameOrderByVersion(name)
}
