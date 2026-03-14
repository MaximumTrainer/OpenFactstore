package com.factstore.core.port.outbound

import com.factstore.core.domain.Flow
import java.util.UUID

interface IFlowRepository {
    fun save(flow: Flow): Flow
    fun findById(id: UUID): Flow?
    fun findAll(): List<Flow>
    fun findAllByIds(ids: Collection<UUID>): List<Flow>
    fun existsById(id: UUID): Boolean
    fun existsByName(name: String): Boolean
    fun deleteById(id: UUID)
    fun countAll(): Long
}
