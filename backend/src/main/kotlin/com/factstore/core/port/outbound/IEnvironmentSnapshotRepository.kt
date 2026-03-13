package com.factstore.core.port.outbound

import com.factstore.core.domain.EnvironmentSnapshot
import java.util.UUID

interface IEnvironmentSnapshotRepository {
    fun save(snapshot: EnvironmentSnapshot): EnvironmentSnapshot
    fun findById(id: UUID): EnvironmentSnapshot?
    fun findAllByEnvironmentId(environmentId: UUID): List<EnvironmentSnapshot>
    fun findByEnvironmentIdAndSnapshotIndex(environmentId: UUID, snapshotIndex: Long): EnvironmentSnapshot?
    fun findLatestByEnvironmentId(environmentId: UUID): EnvironmentSnapshot?
    fun countByEnvironmentId(environmentId: UUID): Long
}
