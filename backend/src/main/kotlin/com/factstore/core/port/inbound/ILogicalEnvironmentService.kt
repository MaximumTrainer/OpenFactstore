package com.factstore.core.port.inbound

import com.factstore.dto.CreateLogicalEnvironmentRequest
import com.factstore.dto.LogicalEnvironmentResponse
import com.factstore.dto.MergedSnapshotResponse
import com.factstore.dto.UpdateLogicalEnvironmentRequest
import java.util.UUID

interface ILogicalEnvironmentService {
    fun createLogicalEnvironment(request: CreateLogicalEnvironmentRequest): LogicalEnvironmentResponse
    fun listLogicalEnvironments(): List<LogicalEnvironmentResponse>
    fun getLogicalEnvironment(id: UUID): LogicalEnvironmentResponse
    fun updateLogicalEnvironment(id: UUID, request: UpdateLogicalEnvironmentRequest): LogicalEnvironmentResponse
    fun deleteLogicalEnvironment(id: UUID)
    fun addMember(logicalEnvId: UUID, physicalEnvId: UUID): LogicalEnvironmentResponse
    fun removeMember(logicalEnvId: UUID, physicalEnvId: UUID)
    fun getMergedSnapshot(logicalEnvId: UUID): MergedSnapshotResponse
}
