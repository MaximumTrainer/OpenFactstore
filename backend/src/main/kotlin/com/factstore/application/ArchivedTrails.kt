package com.factstore.application

import com.factstore.core.domain.Attestation
import com.factstore.core.domain.Trail
import com.factstore.exception.ConflictException

/**
 * An archived trail is read-only (#245): it keeps its evidence for audit but takes no new
 * attestations, artifacts or evidence files. Unarchive it to record against it again.
 */
fun Trail.requireWritable(): Trail {
    if (archivedAt != null) {
        throw ConflictException("Trail $id is archived and read-only; unarchive it before recording against it")
    }
    return this
}

/**
 * Evidence, once attached, is never replaced (#245): a corrected report is a new attestation, or
 * an override (#125), so the original stays on the record.
 */
fun Attestation.requireNoEvidence(): Attestation {
    if (evidenceFileHash != null) {
        throw ConflictException(
            "Attestation $id already has evidence ($evidenceFileName); evidence is immutable, so record a new attestation instead"
        )
    }
    return this
}
