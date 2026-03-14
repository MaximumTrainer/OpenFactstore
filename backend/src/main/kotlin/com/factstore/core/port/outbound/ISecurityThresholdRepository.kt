package com.factstore.core.port.outbound

import com.factstore.core.domain.SecurityThreshold
import java.util.UUID

interface ISecurityThresholdRepository {
    fun save(threshold: SecurityThreshold): SecurityThreshold
    fun findById(id: UUID): SecurityThreshold?
    fun findByFlowId(flowId: UUID): SecurityThreshold?
}
