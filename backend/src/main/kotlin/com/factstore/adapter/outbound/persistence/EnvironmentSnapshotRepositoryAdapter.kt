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
    fun findAllByEnvironmentIdOrderBySnapshotIndexAsc(environmentId: UUID): List<EnvironmentSnapshot>
    fun findByEnvironmentIdAndSnapshotIndex(environmentId: UUID, snapshotIndex: Long): EnvironmentSnapshot?
    fun findTopByEnvironmentIdOrderBySnapshotIndexDesc(environmentId: UUID): EnvironmentSnapshot?

    @Query("SELECT MAX(s.snapshotIndex) FROM EnvironmentSnapshot s WHERE s.environmentId = :environmentId")
    fun findMaxSnapshotIndexByEnvironmentId(environmentId: UUID): Long?
}

@Component
class EnvironmentSnapshotRepositoryAdapter(private val jpa: EnvironmentSnapshotRepositoryJpa) : IEnvironmentSnapshotRepository {
    override fun save(snapshot: EnvironmentSnapshot): EnvironmentSnapshot = jpa.save(snapshot)
    override fun findById(id: UUID): EnvironmentSnapshot? = jpa.findById(id).orElse(null)
    override fun findAllByEnvironmentId(environmentId: UUID): List<EnvironmentSnapshot> =
        jpa.findAllByEnvironmentIdOrderBySnapshotIndexAsc(environmentId)
    override fun findByEnvironmentIdAndSnapshotIndex(environmentId: UUID, snapshotIndex: Long): EnvironmentSnapshot? =
        jpa.findByEnvironmentIdAndSnapshotIndex(environmentId, snapshotIndex)
    override fun findLatestByEnvironmentId(environmentId: UUID): EnvironmentSnapshot? =
        jpa.findTopByEnvironmentIdOrderBySnapshotIndexDesc(environmentId)
    override fun findMaxSnapshotIndexByEnvironmentId(environmentId: UUID): Long? =
        jpa.findMaxSnapshotIndexByEnvironmentId(environmentId)
}
