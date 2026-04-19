package com.factstore.adapter.outbound.persistence.read

import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import com.factstore.core.port.outbound.read.IAttestationReadRepository
import com.factstore.adapter.outbound.persistence.AttestationRepositoryJpa
import com.factstore.dto.query.AttestationView
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class AttestationReadRepositoryAdapter(private val jpa: AttestationRepositoryJpa) : IAttestationReadRepository {

    override fun findByTrailId(trailId: UUID): List<AttestationView> =
        jpa.findByTrailId(trailId).map { it.toView() }
}

fun Attestation.toView() = AttestationView(
    id = id,
    trailId = trailId,
    type = type,
    status = status,
    evidenceFileHash = evidenceFileHash,
    evidenceFileName = evidenceFileName,
    evidenceFileSizeBytes = evidenceFileSizeBytes,
    details = details,
    name = name,
    evidenceUrl = evidenceUrl,
    compliant = status == AttestationStatus.PASSED,
    orgSlug = orgSlug,
    artifactFingerprint = artifactFingerprint,
    createdAt = createdAt
)
