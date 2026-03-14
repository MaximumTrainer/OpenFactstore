package com.factstore.adapter.outbound.persistence

import com.factstore.core.domain.LogicalEnvironmentMember
import com.factstore.core.port.outbound.ILogicalEnvironmentMemberRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LogicalEnvironmentMemberRepositoryJpa : JpaRepository<LogicalEnvironmentMember, UUID> {
    fun findAllByLogicalEnvId(logicalEnvId: UUID): List<LogicalEnvironmentMember>
    fun findByLogicalEnvIdAndPhysicalEnvId(logicalEnvId: UUID, physicalEnvId: UUID): LogicalEnvironmentMember?
    fun deleteByLogicalEnvIdAndPhysicalEnvId(logicalEnvId: UUID, physicalEnvId: UUID)
    fun deleteAllByLogicalEnvId(logicalEnvId: UUID)
}

@Component
class LogicalEnvironmentMemberRepositoryAdapter(private val jpa: LogicalEnvironmentMemberRepositoryJpa) : ILogicalEnvironmentMemberRepository {
    override fun save(member: LogicalEnvironmentMember): LogicalEnvironmentMember = jpa.save(member)
    override fun findAllByLogicalEnvId(logicalEnvId: UUID): List<LogicalEnvironmentMember> =
        jpa.findAllByLogicalEnvId(logicalEnvId)
    override fun findByLogicalEnvIdAndPhysicalEnvId(logicalEnvId: UUID, physicalEnvId: UUID): LogicalEnvironmentMember? =
        jpa.findByLogicalEnvIdAndPhysicalEnvId(logicalEnvId, physicalEnvId)
    override fun deleteByLogicalEnvIdAndPhysicalEnvId(logicalEnvId: UUID, physicalEnvId: UUID) =
        jpa.deleteByLogicalEnvIdAndPhysicalEnvId(logicalEnvId, physicalEnvId)
    override fun deleteAllByLogicalEnvId(logicalEnvId: UUID) =
        jpa.deleteAllByLogicalEnvId(logicalEnvId)
}
