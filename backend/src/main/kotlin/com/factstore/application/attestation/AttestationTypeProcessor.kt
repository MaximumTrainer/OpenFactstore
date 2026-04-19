package com.factstore.application.attestation

import com.factstore.core.domain.Attestation

interface AttestationTypeProcessor {
    /** The type name this processor handles (case-insensitive) */
    val typeName: String

    /**
     * Process evidence content and update attestation status/details.
     * @param evidenceContent raw bytes of the uploaded evidence file
     * @param attestation the attestation to update in-place
     * @return updated attestation (may be modified in-place)
     */
    fun process(evidenceContent: ByteArray, attestation: Attestation): Attestation
}
