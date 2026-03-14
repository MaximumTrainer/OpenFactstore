package com.factstore.core.port.inbound

import java.util.UUID

interface IAuditPackageService {
    /** Builds a tar.gz audit package containing all metadata and evidence for the given trail. */
    fun buildForTrail(trailId: UUID): ByteArray
    /** Builds a tar.gz audit package scoped to a single artifact and its trail context. */
    fun buildForArtifact(artifactId: UUID): ByteArray
    /** Builds a tar.gz audit package scoped to a single attestation and its evidence files. */
    fun buildForAttestation(attestationId: UUID): ByteArray
}
