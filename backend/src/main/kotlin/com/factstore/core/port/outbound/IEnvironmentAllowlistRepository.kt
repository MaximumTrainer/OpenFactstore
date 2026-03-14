package com.factstore.core.port.outbound

import com.factstore.core.domain.EnvironmentAllowlistEntry
import java.util.UUID

interface IEnvironmentAllowlistRepository {
    fun save(entry: EnvironmentAllowlistEntry): EnvironmentAllowlistEntry
    fun findById(id: UUID): EnvironmentAllowlistEntry?
    fun findAllByEnvironmentId(environmentId: UUID): List<EnvironmentAllowlistEntry>
    fun findActiveByEnvironmentId(environmentId: UUID): List<EnvironmentAllowlistEntry>
}
