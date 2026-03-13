package com.factstore.core.port.outbound

import com.factstore.core.domain.Environment
import java.util.UUID

interface IEnvironmentRepository {
    fun save(environment: Environment): Environment
    fun findById(id: UUID): Environment?
    fun findAll(): List<Environment>
    fun existsById(id: UUID): Boolean
    fun existsByName(name: String): Boolean
    fun deleteById(id: UUID)
}
