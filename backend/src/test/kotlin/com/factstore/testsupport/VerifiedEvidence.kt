package com.factstore.testsupport

import com.factstore.core.domain.AttestationStatus
import com.factstore.core.port.inbound.IAttestationService
import com.factstore.dto.AttestationResponse
import com.factstore.dto.CreateAttestationRequest
import java.util.UUID

/**
 * Since #240 a `PASSED` claim for a type the server can evaluate (junit, snyk) is ignored until
 * evidence is uploaded. Tests that need such an attestation to pass record it the way a pipeline
 * now must: with a passing report.
 */
object VerifiedEvidence {
    private val passing = mapOf(
        "junit" to Triple("junit.xml", "application/xml", """<testsuite name="t" tests="1" failures="0" errors="0"><testcase name="ok"/></testsuite>"""),
        "snyk" to Triple("snyk.json", "application/json", """{"vulnerabilities":[]}""")
    )

    fun passingReport(type: String) = passing[type.lowercase()]

    fun record(service: IAttestationService, trailId: UUID, request: CreateAttestationRequest): AttestationResponse {
        val recorded = service.recordAttestation(trailId, request)
        val report = passingReport(request.type)
        if (request.status != AttestationStatus.PASSED || report == null || recorded.status != AttestationStatus.PENDING) {
            return recorded
        }
        service.uploadEvidence(trailId, recorded.id, report.first, report.second, report.third.toByteArray())
        return service.listAttestations(trailId).first { it.id == recorded.id }
    }
}

/** Records [request], uploading a passing report when it claims PASSED for an evaluated type. */
fun IAttestationService.recordWithEvidence(trailId: UUID, request: CreateAttestationRequest): AttestationResponse =
    VerifiedEvidence.record(this, trailId, request)
