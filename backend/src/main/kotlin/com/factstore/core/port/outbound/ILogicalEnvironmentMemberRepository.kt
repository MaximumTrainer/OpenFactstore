package com.factstore.core.port.outbound

import com.factstore.core.domain.LogicalEnvironmentMember
import java.util.UUID

interface ILogicalEnvironmentMemberRepository {
    fun save(member: LogicalEnvironmentMember): LogicalEnvironmentMember
    fun findAllByLogicalEnvId(logicalEnvId: UUID): List<LogicalEnvironmentMember>
    fun findByLogicalEnvIdAndPhysicalEnvId(logicalEnvId: UUID, physicalEnvId: UUID): LogicalEnvironmentMember?
    fun deleteByLogicalEnvIdAndPhysicalEnvId(logicalEnvId: UUID, physicalEnvId: UUID)
    fun deleteAllByLogicalEnvId(logicalEnvId: UUID)
}
