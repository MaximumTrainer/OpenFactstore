package com.factstore.core.port.outbound

import com.factstore.core.domain.RegulatoryFramework
import java.util.UUID

interface IRegulatoryFrameworkRepository {
    fun save(framework: RegulatoryFramework): RegulatoryFramework
    fun findById(id: UUID): RegulatoryFramework?
    fun findAll(): List<RegulatoryFramework>
    fun findActive(): List<RegulatoryFramework>
    fun existsByName(name: String): Boolean
}
