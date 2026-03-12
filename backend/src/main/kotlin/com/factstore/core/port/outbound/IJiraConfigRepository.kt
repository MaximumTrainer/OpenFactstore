package com.factstore.core.port.outbound

import com.factstore.core.domain.JiraConfig
import java.util.UUID

interface IJiraConfigRepository {
    fun save(config: JiraConfig): JiraConfig
    fun findFirst(): JiraConfig?
    fun findById(id: UUID): JiraConfig?
    fun deleteAll()
}
