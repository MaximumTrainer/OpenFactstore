package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.EnvironmentSnapshot
import com.factstore.core.port.outbound.IEnvironmentSnapshotRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EnvironmentSnapshotRepositoryJpa : JpaRepository<EnvironmentSnapshot, UUID> {
    fun findAllByEnvironmentId(environmentId: UUID): List<EnvironmentSnapshot>
    fun findByEnvironmentIdAndSnapshotIndex(environmentId: UUID, snapshotIndex: Long): EnvironmentSnapshot?
    fun countByEnvironmentId(environmentId: UUID): Long

    @Query("SELECT s FROM EnvironmentSnapshot s WHERE s.environmentId = :environmentId ORDER BY s.snapshotIndex DESC LIMIT 1")
    fun findTopByEnvironmentIdOrderBySnapshotIndexDesc(environmentId: UUID): EnvironmentSnapshot?
}

@Component
class EnvironmentSnapshotRepositoryAdapter(private val jpa: EnvironmentSnapshotRepositoryJpa) : IEnvironmentSnapshotRepository {
    override fun save(snapshot: EnvironmentSnapshot): EnvironmentSnapshot = jpa.save(snapshot)
    override fun findById(id: UUID): EnvironmentSnapshot? = jpa.findById(id).orElse(null)
    override fun findAllByEnvironmentId(environmentId: UUID): List<EnvironmentSnapshot> =
        jpa.findAllByEnvironmentId(environmentId)
    override fun findByEnvironmentIdAndSnapshotIndex(environmentId: UUID, snapshotIndex: Long): EnvironmentSnapshot? =
        jpa.findByEnvironmentIdAndSnapshotIndex(environmentId, snapshotIndex)
    override fun findLatestByEnvironmentId(environmentId: UUID): EnvironmentSnapshot? =
        jpa.findTopByEnvironmentIdOrderBySnapshotIndexDesc(environmentId)
    override fun countByEnvironmentId(environmentId: UUID): Long = jpa.countByEnvironmentId(environmentId)
}
