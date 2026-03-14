package com.factstore.core.port.inbound

import com.factstore.dto.BuildProvenanceResponse
import com.factstore.dto.ProvenanceVerificationResponse
import com.factstore.dto.RecordProvenanceRequest
import java.util.UUID

interface IBuildProvenanceService {
    fun recordProvenance(trailId: UUID, artifactId: UUID, request: RecordProvenanceRequest): BuildProvenanceResponse
    fun getProvenance(trailId: UUID, artifactId: UUID): BuildProvenanceResponse
    fun getProvenanceBySha256(sha256Digest: String): BuildProvenanceResponse
    fun verifyProvenance(trailId: UUID, artifactId: UUID): ProvenanceVerificationResponse
}
