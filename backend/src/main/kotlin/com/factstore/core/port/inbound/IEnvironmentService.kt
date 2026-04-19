package com.factstore.core.port.inbound

import com.factstore.dto.BaselineResponse
import com.factstore.dto.CreateBaselineRequest
import com.factstore.dto.CreateEnvironmentRequest
import com.factstore.dto.DeploymentResponse
import com.factstore.dto.DriftReportResponse
import com.factstore.dto.EnvironmentResponse
import com.factstore.dto.EnvironmentSnapshotResponse
import com.factstore.dto.PageResponse
import com.factstore.dto.RecordSnapshotRequest
import com.factstore.dto.SnapshotDiffResponse
import com.factstore.dto.UpdateEnvironmentRequest
import java.util.UUID

interface IEnvironmentService {
    fun createEnvironment(request: CreateEnvironmentRequest): EnvironmentResponse
    fun listEnvironments(): List<EnvironmentResponse>
    fun listEnvironments(page: Int, size: Int): PageResponse<EnvironmentResponse>
    fun getEnvironment(id: UUID): EnvironmentResponse
    fun updateEnvironment(id: UUID, request: UpdateEnvironmentRequest): EnvironmentResponse
    fun deleteEnvironment(id: UUID)
    fun recordSnapshot(environmentId: UUID, request: RecordSnapshotRequest): EnvironmentSnapshotResponse
    fun listSnapshots(environmentId: UUID): List<EnvironmentSnapshotResponse>
    fun getLatestSnapshot(environmentId: UUID): EnvironmentSnapshotResponse
    fun getSnapshot(environmentId: UUID, snapshotIndex: Long): EnvironmentSnapshotResponse
    fun diffSnapshots(environmentId: UUID, fromIndex: Long, toIndex: Long): SnapshotDiffResponse
    fun createBaseline(environmentId: UUID, request: CreateBaselineRequest): BaselineResponse
    fun getCurrentBaseline(environmentId: UUID): BaselineResponse
    fun checkDrift(environmentId: UUID): DriftReportResponse
    fun listDriftHistory(environmentId: UUID): List<DriftReportResponse>
    fun listDeployments(environmentId: UUID): List<DeploymentResponse>
}
