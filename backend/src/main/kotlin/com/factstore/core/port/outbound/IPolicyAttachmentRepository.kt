package com.factstore.core.port.outbound

import com.factstore.core.domain.PolicyAttachment
import java.util.UUID

interface IPolicyAttachmentRepository {
    fun save(attachment: PolicyAttachment): PolicyAttachment
    fun findById(id: UUID): PolicyAttachment?
    fun findAll(): List<PolicyAttachment>
    fun existsById(id: UUID): Boolean
    fun existsByPolicyIdAndEnvironmentId(policyId: UUID, environmentId: UUID): Boolean
    fun deleteById(id: UUID)
    fun findByEnvironmentId(environmentId: UUID): List<PolicyAttachment>
}
