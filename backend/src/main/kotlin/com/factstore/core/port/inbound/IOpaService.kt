package com.factstore.core.port.inbound

import com.factstore.dto.BundleResponse
import com.factstore.dto.EvaluatePolicyRequest
import com.factstore.dto.PolicyDecisionResponse
import com.factstore.dto.UploadBundleRequest
import java.util.UUID

interface IOpaService {
    fun uploadBundle(request: UploadBundleRequest): BundleResponse
    fun listBundles(): List<BundleResponse>
    fun getBundle(id: UUID): BundleResponse
    fun activateBundle(id: UUID): BundleResponse
    fun evaluatePolicy(request: EvaluatePolicyRequest): PolicyDecisionResponse
    fun listDecisions(): List<PolicyDecisionResponse>
    fun getDecision(id: UUID): PolicyDecisionResponse
}
