package com.factstore.core.port.inbound

import com.factstore.dto.TrailCascadeCounts
import com.factstore.dto.TrailCleanupRequest
import com.factstore.dto.TrailCleanupResponse
import com.factstore.dto.TrailResponse
import java.util.UUID

/**
 * Retiring obsolete trails (#161).
 *
 * A trail is compliance evidence, so it is archived, never deleted (#245): archiving hides it
 * from the listings, makes it read-only and is reversible. Lawful erasure of content goes
 * through cryptographic shredding (docs/data-lifecycle.md), which is audited.
 */
interface ITrailCleanupService {
    fun archiveTrail(id: UUID): TrailResponse
    fun unarchiveTrail(id: UUID): TrailResponse
    /** How much evidence the trail holds; archiving keeps all of it. */
    fun cascadeFor(id: UUID): TrailCascadeCounts
    fun cleanup(request: TrailCleanupRequest): TrailCleanupResponse
}
