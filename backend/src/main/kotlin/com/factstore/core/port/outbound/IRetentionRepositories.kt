package com.factstore.core.port.outbound

import com.factstore.core.domain.LegalHold
import com.factstore.core.domain.RetentionDataClass
import com.factstore.core.domain.RetentionPolicy
import com.factstore.core.domain.SnapshotArtifact
import java.time.Instant
import java.util.UUID

interface IRetentionPolicyRepository {
    fun save(policy: RetentionPolicy): RetentionPolicy
    fun find(tenant: String, dataClass: RetentionDataClass): RetentionPolicy?
    fun findAll(): List<RetentionPolicy>
    fun delete(policy: RetentionPolicy)
}

interface ILegalHoldRepository {
    fun save(hold: LegalHold): LegalHold
    fun findById(id: UUID): LegalHold?
    fun findAll(): List<LegalHold>
    fun findActive(): List<LegalHold>
}

/** Where archived records go (#251): one JSON document per record, under a stable key. */
interface IRetentionArchive {
    /** Stores [content] under [key] and returns where it went. Must be durable before it returns. */
    fun store(key: String, content: String): String
}

/** An environment snapshot as the retention job sees it. */
data class RetainedSnapshot(
    val id: UUID,
    val environmentId: UUID,
    val environmentName: String,
    /** The environment's organisation, or `default`. */
    val tenant: String,
    val snapshotIndex: Long,
    val recordedAt: Instant,
    val recordedBy: String,
    val artifacts: List<SnapshotArtifact>,
    /** The environment's newest snapshot: current state, never retained away. */
    val latest: Boolean,
    /** A drift baseline points at it, so removing it would break drift detection. */
    val baselined: Boolean
)

data class RetainedNotification(val id: UUID, val entityId: UUID?)

/**
 * The data the retention job reads and removes (#251), behind one port so what it may touch is
 * stated in a single place. Ledger records and audit events are deliberately out of reach.
 */
interface IRetentionStore {
    fun snapshotsRecordedBefore(cutoff: Instant): List<RetainedSnapshot>
    fun removeSnapshot(snapshotId: UUID)
    fun notificationsCreatedBefore(cutoff: Instant): List<RetainedNotification>
    fun removeNotifications(ids: Collection<UUID>)
    fun deliveriesSentBefore(cutoff: Instant): List<UUID>
    fun removeDeliveries(ids: Collection<UUID>)
    /** Subject digests (hex) of the facts whose content is encrypted under [tenant]'s key for [period]. */
    fun factSubjectsUnderKey(tenant: String, period: String): Set<String>
    /** Digests (hex, no `sha256:` prefix) of the artifacts reported on these trails. */
    fun artifactDigestsForTrails(trailIds: Collection<UUID>): Set<String>
    fun trailIdsForFlows(flowIds: Collection<UUID>): Set<UUID>
}
