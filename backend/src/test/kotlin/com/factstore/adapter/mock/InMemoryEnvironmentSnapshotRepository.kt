package com.factstore.adapter.mock

import com.factstore.core.domain.EnvironmentSnapshot
import com.factstore.core.port.outbound.IEnvironmentSnapshotRepository
import java.util.UUID

class InMemoryEnvironmentSnapshotRepository : IEnvironmentSnapshotRepository {
    private val store = mutableMapOf<UUID, EnvironmentSnapshot>()

    override fun save(snapshot: EnvironmentSnapshot): EnvironmentSnapshot {
        store[snapshot.id] = snapshot
        return snapshot
    }

    override fun findById(id: UUID): EnvironmentSnapshot? = store[id]

    override fun findAllByEnvironmentId(environmentId: UUID): List<EnvironmentSnapshot> =
        store.values.filter { it.environmentId == environmentId }.sortedBy { it.snapshotIndex }

    override fun findByEnvironmentIdAndSnapshotIndex(environmentId: UUID, snapshotIndex: Long): EnvironmentSnapshot? =
        store.values.find { it.environmentId == environmentId && it.snapshotIndex == snapshotIndex }

    override fun findLatestByEnvironmentId(environmentId: UUID): EnvironmentSnapshot? =
        store.values.filter { it.environmentId == environmentId }.maxByOrNull { it.snapshotIndex }

    override fun countByEnvironmentId(environmentId: UUID): Long =
        store.values.count { it.environmentId == environmentId }.toLong()
}
