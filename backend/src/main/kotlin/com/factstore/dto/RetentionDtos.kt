package com.factstore.dto

import com.factstore.core.domain.LegalHoldScope
import com.factstore.core.domain.RetentionAction
import com.factstore.core.domain.RetentionDataClass
import java.time.Instant
import java.util.UUID

/** #251: the body of PUT /api/v1/admin/retention/policies/{tenant}/{dataClass}. */
data class SetRetentionPolicyRequest(val retainDays: Int)

data class RetentionDataClassResponse(
    val dataClass: RetentionDataClass,
    val action: RetentionAction,
    val minimumDays: Int
)

data class RetentionPolicyResponse(
    val tenant: String,
    val dataClass: RetentionDataClass,
    val action: RetentionAction,
    val retainDays: Int,
    val minimumDays: Int,
    val updatedBy: String,
    val updatedAt: Instant
)

/** What one policy did, or would do, in one run. */
data class RetentionBatch(
    val tenant: String,
    val dataClass: RetentionDataClass,
    val action: RetentionAction,
    /** Data recorded before this instant is past its period. */
    val cutoff: Instant,
    /** Items past their period and not under legal hold. */
    val eligible: Int,
    /** Items actually shredded, archived or pruned; 0 in a dry run. */
    val applied: Int,
    val skippedForLegalHold: Int
)

data class RetentionRunReport(
    val dryRun: Boolean,
    val ranAt: Instant,
    val batches: List<RetentionBatch>
)

data class PlaceLegalHoldRequest(
    val scopeType: LegalHoldScope,
    val scopeId: UUID,
    val reason: String
)

data class LegalHoldResponse(
    val id: UUID,
    val scopeType: LegalHoldScope,
    val scopeId: UUID,
    val reason: String,
    val placedBy: String,
    val placedAt: Instant,
    val releasedBy: String? = null,
    val releasedAt: Instant? = null,
    val active: Boolean
)
