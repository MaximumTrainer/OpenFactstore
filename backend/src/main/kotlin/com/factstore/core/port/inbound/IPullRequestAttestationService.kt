package com.factstore.core.port.inbound

import com.factstore.dto.AttestationResponse
import com.factstore.dto.CreatePrAttestationRequest
import java.util.UUID

interface IPullRequestAttestationService {
    fun attestPullRequest(trailId: UUID, request: CreatePrAttestationRequest): AttestationResponse
}
