package com.factstore.core.port.outbound

import com.factstore.core.domain.ConfluenceConfig
import java.util.UUID

interface IConfluenceConfigRepository {
    fun save(config: ConfluenceConfig): ConfluenceConfig
    fun findFirst(): ConfluenceConfig?
    fun findById(id: UUID): ConfluenceConfig?
    fun deleteAll()
}
