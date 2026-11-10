package com.factstore.core.port.outbound

import com.factstore.core.domain.FactSchema

interface IFactSchemaRepository {
    fun save(schema: FactSchema): FactSchema
    fun find(name: String, version: Int): FactSchema?
    fun findAllByName(name: String): List<FactSchema>
}
