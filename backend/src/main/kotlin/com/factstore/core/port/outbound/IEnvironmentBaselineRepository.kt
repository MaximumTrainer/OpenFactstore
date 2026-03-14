package com.factstore.core.port.outbound

import com.factstore.core.domain.EnvironmentBaseline
import java.util.UUID

interface IEnvironmentBaselineRepository {
    fun save(baseline: EnvironmentBaseline): EnvironmentBaseline
    fun findById(id: UUID): EnvironmentBaseline?
    fun findActiveByEnvironmentId(environmentId: UUID): EnvironmentBaseline?
    fun findAllByEnvironmentId(environmentId: UUID): List<EnvironmentBaseline>
}
