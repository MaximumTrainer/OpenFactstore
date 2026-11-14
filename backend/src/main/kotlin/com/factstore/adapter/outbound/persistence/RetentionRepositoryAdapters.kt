package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.Environment
import com.factstore.core.domain.EnvironmentSnapshot
import com.factstore.core.domain.LegalHold
import com.factstore.core.domain.Notification
import com.factstore.core.domain.RetentionDataClass
import com.factstore.core.domain.RetentionPolicy
import com.factstore.core.domain.SnapshotArtifact
import com.factstore.core.port.outbound.ILegalHoldRepository
import com.factstore.core.port.outbound.IRetentionPolicyRepository
import com.factstore.core.port.outbound.IRetentionStore
import com.factstore.core.port.outbound.RetainedNotification
import com.factstore.core.port.outbound.RetainedSnapshot
import jakarta.persistence.EntityManager
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface RetentionPolicyRepositoryJpa : JpaRepository<RetentionPolicy, UUID> {
    fun findByTenantAndDataClass(tenant: String, dataClass: RetentionDataClass): RetentionPolicy?
}

@Component
class RetentionPolicyRepositoryAdapter(private val jpa: RetentionPolicyRepositoryJpa) : IRetentionPolicyRepository {
    override fun save(policy: RetentionPolicy): RetentionPolicy = jpa.save(policy)
    override fun find(tenant: String, dataClass: RetentionDataClass): RetentionPolicy? = jpa.findByTenantAndDataClass(tenant, dataClass)
    override fun findAll(): List<RetentionPolicy> = jpa.findAll()
    override fun delete(policy: RetentionPolicy) = jpa.delete(policy)
}

@Repository
interface LegalHoldRepositoryJpa : JpaRepository<LegalHold, UUID> {
    fun findAllByReleasedAtIsNull(): List<LegalHold>
}

@Component
class LegalHoldRepositoryAdapter(private val jpa: LegalHoldRepositoryJpa) : ILegalHoldRepository {
    override fun save(hold: LegalHold): LegalHold = jpa.save(hold)
    override fun findById(id: UUID): LegalHold? = jpa.findById(id).orElse(null)
    override fun findAll(): List<LegalHold> = jpa.findAll()
    override fun findActive(): List<LegalHold> = jpa.findAllByReleasedAtIsNull()
}

/** The retention job's view of the database (#251). Ledger and audit tables are not reachable from here. */
@Component
class RetentionStoreAdapter(private val em: EntityManager) : IRetentionStore {

    override fun snapshotsRecordedBefore(cutoff: Instant): List<RetainedSnapshot> {
        val old = em.createQuery("SELECT s FROM EnvironmentSnapshot s WHERE s.recordedAt < :cutoff", EnvironmentSnapshot::class.java)
            .setParameter("cutoff", cutoff).resultList
        if (old.isEmpty()) return emptyList()
        val envIds = old.map { it.environmentId }.toSet()
        val environments = em.createQuery("SELECT e FROM Environment e WHERE e.id IN :ids", Environment::class.java)
            .setParameter("ids", envIds).resultList.associateBy { it.id }
        val latestIndex = em.createQuery(
            "SELECT s.environmentId, MAX(s.snapshotIndex) FROM EnvironmentSnapshot s WHERE s.environmentId IN :ids GROUP BY s.environmentId",
            Array<Any>::class.java
        ).setParameter("ids", envIds).resultList.associate { it[0] as UUID to (it[1] as Number).toLong() }
        val baselined = em.createQuery("SELECT b.snapshotId FROM EnvironmentBaseline b WHERE b.snapshotId IS NOT NULL", UUID::class.java)
            .resultList.toSet()
        val artifacts = em.createQuery("SELECT a FROM SnapshotArtifact a WHERE a.snapshotId IN :ids", SnapshotArtifact::class.java)
            .setParameter("ids", old.map { it.id }).resultList.groupBy { it.snapshotId }
        return old.map { s ->
            val env = environments[s.environmentId]
            RetainedSnapshot(
                id = s.id,
                environmentId = s.environmentId,
                environmentName = env?.name ?: "",
                tenant = env?.orgSlug ?: "default",
                snapshotIndex = s.snapshotIndex,
                recordedAt = s.recordedAt,
                recordedBy = s.recordedBy,
                artifacts = artifacts[s.id].orEmpty(),
                latest = latestIndex[s.environmentId] == s.snapshotIndex,
                baselined = s.id in baselined
            )
        }
    }

    // Removed through the entity manager, not bulk JPQL, so the persistence context forgets them too.
    override fun removeSnapshot(snapshotId: UUID) {
        em.createQuery("SELECT a FROM SnapshotArtifact a WHERE a.snapshotId = :id", SnapshotArtifact::class.java)
            .setParameter("id", snapshotId).resultList.forEach(em::remove)
        em.find(EnvironmentSnapshot::class.java, snapshotId)?.let(em::remove)
        em.flush()
    }

    override fun notificationsCreatedBefore(cutoff: Instant): List<RetainedNotification> =
        em.createQuery("SELECT n FROM Notification n WHERE n.createdAt < :cutoff", Notification::class.java)
            .setParameter("cutoff", cutoff).resultList.map { RetainedNotification(it.id, it.entityId) }

    override fun removeNotifications(ids: Collection<UUID>) {
        if (ids.isEmpty()) return
        ids.forEach { id -> em.find(Notification::class.java, id)?.let(em::remove) }
        em.flush()
    }

    override fun deliveriesSentBefore(cutoff: Instant): List<UUID> =
        em.createQuery("SELECT d.id FROM NotificationDelivery d WHERE d.sentAt < :cutoff", UUID::class.java)
            .setParameter("cutoff", cutoff).resultList

    override fun removeDeliveries(ids: Collection<UUID>) {
        if (ids.isEmpty()) return
        ids.forEach { id -> em.find(com.factstore.core.domain.NotificationDelivery::class.java, id)?.let(em::remove) }
        em.flush()
    }

    override fun factSubjectsUnderKey(tenant: String, period: String): Set<String> =
        em.createQuery("SELECT f.subjectSha256 FROM Fact f WHERE f.payloadJson LIKE :prefix", String::class.java)
            .setParameter("prefix", "enc:v1:$tenant:$period:%").resultList.map { it.lowercase() }.toSet()

    override fun artifactDigestsForTrails(trailIds: Collection<UUID>): Set<String> {
        if (trailIds.isEmpty()) return emptySet()
        return em.createQuery("SELECT a.sha256Digest FROM Artifact a WHERE a.trailId IN :ids", String::class.java)
            .setParameter("ids", trailIds).resultList.toSet()
    }

    override fun trailIdsForFlows(flowIds: Collection<UUID>): Set<UUID> {
        if (flowIds.isEmpty()) return emptySet()
        return em.createQuery("SELECT t.id FROM Trail t WHERE t.flowId IN :ids", UUID::class.java)
            .setParameter("ids", flowIds).resultList.toSet()
    }
}

