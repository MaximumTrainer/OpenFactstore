package com.factstore.core.port.inbound

import com.factstore.dto.CreatePolicyRequest
import com.factstore.dto.PolicyResponse
import com.factstore.dto.UpdatePolicyRequest
import java.util.UUID

interface IPolicyService {
    fun createPolicy(request: CreatePolicyRequest): PolicyResponse
    fun listPolicies(): List<PolicyResponse>
    fun getPolicy(id: UUID): PolicyResponse
    fun updatePolicy(id: UUID, request: UpdatePolicyRequest): PolicyResponse
    fun deletePolicy(id: UUID)
    fun updateWasmModule(id: UUID, wasmContent: String)
}
