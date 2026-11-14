package com.factstore.core.port.inbound

import com.factstore.core.domain.RetentionDataClass
import com.factstore.dto.LegalHoldResponse
import com.factstore.dto.PlaceLegalHoldRequest
import com.factstore.dto.RetentionDataClassResponse
import com.factstore.dto.RetentionPolicyResponse
import com.factstore.dto.RetentionRunReport
import java.util.UUID

/** Data retention (#251): per-organisation policies, legal holds, and the job that applies them. */
interface IRetentionService {
    fun dataClasses(): List<RetentionDataClassResponse>
    fun listPolicies(): List<RetentionPolicyResponse>
    fun setPolicy(tenant: String, dataClass: RetentionDataClass, retainDays: Int): RetentionPolicyResponse
    fun removePolicy(tenant: String, dataClass: RetentionDataClass)

    /** Applies every policy once. A dry run reports what would happen and changes nothing. */
    fun run(dryRun: Boolean): RetentionRunReport

    fun placeHold(request: PlaceLegalHoldRequest): LegalHoldResponse
    fun releaseHold(id: UUID): LegalHoldResponse
    fun listHolds(includeReleased: Boolean = false): List<LegalHoldResponse>
}
