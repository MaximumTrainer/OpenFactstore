package com.factstore.core.port.inbound

import com.factstore.dto.AttestationResponse
import com.factstore.dto.CreateAttestationRequest
import com.factstore.dto.EvidenceFileResponse
import java.util.UUID

interface IAttestationService {
    fun recordAttestation(
        trailId: UUID,
        request: CreateAttestationRequest,
        artifactFingerprint: String? = null,
        orgSlug: String? = null,
        flowName: String? = null
    ): AttestationResponse
    fun listAttestations(trailId: UUID): List<AttestationResponse>
    fun uploadEvidence(
        trailId: UUID,
        attestationId: UUID,
        fileName: String,
        contentType: String,
        content: ByteArray
    ): EvidenceFileResponse
}
