package com.factstore.core.port.outbound

import com.factstore.dto.TrailCascadeCounts
import java.util.UUID

/**
 * What a trail owns (#161): attestations, artifacts, evidence files, approvals, coverage
 * reports, security scans, compliance assessments and Jira tickets. Hard deletion was removed
 * in #245, so this port only counts.
 */
interface ITrailCleanupRepository {

    /** How much evidence the trail owns. Nothing removes it: trails are archived, never deleted (#245). */
    fun countCascade(trailId: UUID): TrailCascadeCounts
}
