package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.PolicyAttachment
import com.factstore.core.port.outbound.IPolicyAttachmentRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PolicyAttachmentRepositoryJpa : JpaRepository<PolicyAttachment, UUID> {
    fun existsByPolicyIdAndEnvironmentId(policyId: UUID, environmentId: UUID): Boolean
    fun findByEnvironmentId(environmentId: UUID): List<PolicyAttachment>
}

@Component
class PolicyAttachmentRepositoryAdapter(private val jpa: PolicyAttachmentRepositoryJpa) : IPolicyAttachmentRepository {
    override fun save(attachment: PolicyAttachment): PolicyAttachment = jpa.save(attachment)
    override fun findById(id: UUID): PolicyAttachment? = jpa.findById(id).orElse(null)
    override fun findAll(): List<PolicyAttachment> = jpa.findAll()
    override fun existsById(id: UUID): Boolean = jpa.existsById(id)
    override fun existsByPolicyIdAndEnvironmentId(policyId: UUID, environmentId: UUID): Boolean =
        jpa.existsByPolicyIdAndEnvironmentId(policyId, environmentId)
    override fun deleteById(id: UUID) = jpa.deleteById(id)
    override fun findByEnvironmentId(environmentId: UUID): List<PolicyAttachment> =
        jpa.findByEnvironmentId(environmentId)
}
