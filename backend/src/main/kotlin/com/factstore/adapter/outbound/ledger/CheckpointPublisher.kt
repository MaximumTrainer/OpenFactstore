package com.factstore.adapter.outbound.ledger

import com.factstore.core.domain.LedgerCheckpoint
import com.factstore.core.port.outbound.ICheckpointPublisher
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Publishes each signed checkpoint outside the application (#188), as one JSON line:
 *
 * - always to the log, under the `ledger.checkpoints` logger. Shipped to a SIEM or log archive, the
 *   log is a copy Factstore cannot rewrite;
 * - and, when `factstore.ledger.checkpoint-file` is set, appended to that file, for example on a
 *   write-once volume, or in a repository a job commits to.
 *
 * An auditor compares a proof's root with the published one (`VerifyLedgerProof` takes it as its
 * third argument). Factstore could only fake a proof by changing a root it has already published.
 */
@Component
class CheckpointPublisher(
    private val objectMapper: ObjectMapper,
    @Value("\${factstore.ledger.checkpoint-file:}") private val checkpointFile: String
) : ICheckpointPublisher {
    private val published = LoggerFactory.getLogger("ledger.checkpoints")
    private val log = LoggerFactory.getLogger(CheckpointPublisher::class.java)

    override fun publish(checkpoint: LedgerCheckpoint) {
        val line = objectMapper.writeValueAsString(
            mapOf(
                "checkpoint_index" to checkpoint.checkpointIndex,
                "first_sequence" to checkpoint.firstSequence,
                "last_sequence" to checkpoint.lastSequence,
                "leaf_count" to checkpoint.leafCount,
                "root_hash" to checkpoint.rootHash,
                "statement" to checkpoint.statement,
                "signature" to checkpoint.signature,
                "signing_key_id" to checkpoint.signingKeyId,
                "signature_algorithm" to checkpoint.signatureAlgorithm,
                "created_at" to checkpoint.createdAt.toString()
            )
        )
        published.info(line)
        if (checkpointFile.isNotBlank()) {
            try {
                val path = Path.of(checkpointFile)
                path.parent?.let { Files.createDirectories(it) }
                Files.writeString(path, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            } catch (e: Exception) {
                // The checkpoint is stored and logged; a failed file append must be seen, not hidden.
                log.error("Could not append checkpoint {} to {}: {}", checkpoint.checkpointIndex, checkpointFile, e.message)
            }
        }
    }
}
