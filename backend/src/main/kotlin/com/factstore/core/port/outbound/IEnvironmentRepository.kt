package com.factstore.core.port.outbound

import com.factstore.core.domain.Environment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

interface IEnvironmentRepository {
    fun save(environment: Environment): Environment
    fun findById(id: UUID): Environment?
    fun findAll(): List<Environment>
    fun findAll(pageable: Pageable): Page<Environment>
    fun existsById(id: UUID): Boolean
    fun existsByName(name: String): Boolean
    fun deleteById(id: UUID)
    fun findEnvironmentsWithArtifact(sha256: String): List<UUID>
}
