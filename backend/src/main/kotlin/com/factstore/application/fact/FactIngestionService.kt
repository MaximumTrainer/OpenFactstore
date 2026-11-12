package com.factstore.application.fact

import com.factstore.application.ActorResolver
import com.factstore.application.auth.WorkloadPrincipalDetails
import com.factstore.application.command.EventAppender
import com.factstore.application.schema.Conformity
import com.factstore.application.schema.SchemaRegistryService
import com.factstore.application.schema.SchemaViolation
import com.factstore.application.signing.SourceSignature
import com.factstore.application.signing.SourceSignatureVerifier
import com.factstore.core.domain.Fact
import com.factstore.core.domain.FactIdentity
import com.factstore.core.domain.SchemaUrn
import com.factstore.core.domain.Sha256Hash
import com.factstore.core.domain.event.DomainEvent
import com.factstore.core.port.outbound.IFactRepository
import com.factstore.application.observability.FailureMetrics
import com.factstore.core.domain.AuditEventType
import com.factstore.core.port.inbound.IAuditService
import com.factstore.exception.ConflictException
import com.factstore.exception.CrossWorkloadInjectionException
import com.factstore.exception.FactRejectedException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class FactIngestionResponse(
    @get:JsonProperty("fact_id") val factId: UUID,
    @get:JsonProperty("schema_ref") val schemaRef: String,
    @get:JsonProperty("ingested_at") val ingestedAt: Instant,
    /**
     * #186: the receipt. The hash of the signed, append-only ledger record the fact was written to,
     * and that record's position. The proof endpoint (#188) shows the same hash under a signed root.
     */
    @get:JsonProperty("receipt_hash") val receiptHash: String?,
    @get:JsonProperty("ledger_index") val ledgerIndex: Long
)

/** A stored fact, with the identity bound to it at ingestion (#191). */
data class FactView(
    @get:JsonProperty("fact_id") val factId: UUID,
    @get:JsonProperty("schema_ref") val schemaRef: String,
    val subject: Map<String, String?>,
    val tool: JsonNode,
    val identity: FactIdentity,
    @get:JsonProperty("ingested_at") val ingestedAt: Instant
)

/**
 * Strict ingress validation (#196). A fact is checked, in order, before anything is written:
 *
 * 1. the envelope: `fact_id` a UUID, `subject.sha256` 64 hex characters, `governance.schema_ref`
 *    a schema URN, `tool` an object, and no field the envelope does not define;
 * 2. the schema it names must be registered;
 * 3. `tool` must conform to that schema (JSON Schema 2020-12).
 *
 * Any failure is a [FactRejectedException] carrying every violation found, and nothing reaches the
 * fact table or the event log. Only a conforming fact is stored and appended as
 * [DomainEvent.FactIngested].
 */
@Service
class FactIngestionService(
    private val registry: SchemaRegistryService,
    private val facts: IFactRepository,
    private val eventAppender: EventAppender,
    private val actorResolver: ActorResolver,
    private val objectMapper: ObjectMapper,
    private val servicePolicy: WorkloadServicePolicy,
    private val auditService: IAuditService,
    private val failureMetrics: FailureMetrics,
    private val masking: com.factstore.application.masking.IngressMasking,
    private val signatureVerifier: SourceSignatureVerifier,
    /** #187: every fact must be signed at the source. Off only for a deployment migrating its runners. */
    @Value("\${factstore.ingest.require-signature:true}") private val requireSignature: Boolean
) {

    // A refused fact writes nothing, but its audit record must survive the refusal (#190).
    @Transactional(noRollbackFor = [CrossWorkloadInjectionException::class])
    fun ingest(rawBody: String, signatureHeader: String? = null, signatureKeyId: String? = null): FactIngestionResponse {
        val body = runCatching { objectMapper.readTree(rawBody) }.getOrNull()
            ?: throw FactRejectedException(
                FactRejectedException.INVALID_ENVELOPE, null, listOf(SchemaViolation("", "the body is not valid JSON"))
            )
        val envelope = Envelope.parse(body)
        val schemaRef = envelope.schemaRef

        when (val conformity = registry.validateConformity(schemaRef, envelope.tool)) {
            is Conformity.UnknownSchema -> throw FactRejectedException(
                FactRejectedException.UNKNOWN_SCHEMA, schemaRef.value,
                listOf(SchemaViolation("/governance/schema_ref", "${schemaRef.value} is not registered"))
            )
            is Conformity.Nonconformant -> throw FactRejectedException(
                FactRejectedException.SCHEMA_NONCONFORMANT, schemaRef.value,
                conformity.violations.map { it.copy(path = "/tool${it.path}") }
            )
            Conformity.Conforms -> Unit
        }

        // Validation first (#196), then the source signature over the exact bytes received (#187).
        val signature = sourceSignature(rawBody, signatureHeader, signatureKeyId)

        // Then masking (#198): nothing unmasked reaches immutable storage.
        val masking = masking.check(schemaRef, envelope.tool)

        val identity = verifiedIdentity()
        authorise(envelope, identity)

        if (facts.existsById(envelope.factId)) throw ConflictException("Fact ${envelope.factId} has already been ingested")

        val fact = facts.save(
            Fact(
                factId = envelope.factId,
                schemaRef = schemaRef.value,
                subjectSha256 = envelope.sha256.value,
                subjectRepository = envelope.repository,
                payloadJson = objectMapper.writeValueAsString(masking.payload),
                ingestedBy = actorResolver.current(),
                identity = identity,
                signatureKeyId = signature?.keyId,
                signature = signature?.signature,
                // When Factstore masked the payload, the signed bytes held the secret: keep their hash only.
                signedBody = signature?.takeIf { masking.maskedPaths.isEmpty() }?.let { rawBody },
                signedBodySha256 = signature?.let { sha256(rawBody) },
                maskingRuleset = masking.ruleset,
                maskedPaths = masking.maskedPaths
            )
        )
        val record = eventAppender.append(
            DomainEvent.FactIngested(
                aggregateId = fact.factId,
                occurredAt = fact.ingestedAt,
                schemaRef = fact.schemaRef,
                subjectSha256 = fact.subjectSha256,
                subjectRepository = fact.subjectRepository,
                payloadJson = fact.payloadJson,
                ingestedBy = fact.ingestedBy,
                identity = fact.identity,
                signatureKeyId = fact.signatureKeyId,
                signature = fact.signature,
                signedBody = fact.signedBody,
                signedBodySha256 = fact.signedBodySha256,
                maskingRuleset = fact.maskingRuleset,
                maskedPaths = fact.maskedPaths
            )
        )
        return FactIngestionResponse(fact.factId, fact.schemaRef, fact.ingestedAt, record.recordHash, record.sequenceNumber)
    }

    private fun sha256(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun sourceSignature(rawBody: String, header: String?, keyId: String?): SourceSignature? {
        if (!requireSignature && header.isNullOrBlank() && keyId.isNullOrBlank()) return null
        return signatureVerifier.verify(rawBody.toByteArray(Charsets.UTF_8), header, keyId)
    }

    /** Strict ABAC (#190): a workload emits facts only for its own service. */
    private fun authorise(envelope: Envelope, identity: FactIdentity) {
        val reason = servicePolicy.denial(identity, envelope.repository) ?: return
        failureMetrics.ingestDenial("CROSS_WORKLOAD")
        auditService.record(
            eventType = AuditEventType.AUTH_DENIED,
            actor = actorResolver.current(),
            payload = mapOf(
                "reason" to "cross_workload_injection",
                "detail" to reason,
                "factId" to envelope.factId.toString(),
                "subjectRepository" to envelope.repository,
                "workloadService" to servicePolicy.serviceOf(identity),
                "workloadSubject" to identity.subject
            )
        )
        throw CrossWorkloadInjectionException("Cross-workload fact injection refused: $reason")
    }

    /**
     * The caller's identity as the authentication filters verified it (#191), never anything
     * from the request body: a workload's token claims, or otherwise the credential's actor.
     */
    private fun verifiedIdentity(): FactIdentity {
        val details = SecurityContextHolder.getContext().authentication?.details
        if (details is WorkloadPrincipalDetails) return FactIdentity.fromWorkloadClaims(details.claims)
        return FactIdentity.credential(actorResolver.current())
    }

    @Transactional(readOnly = true)
    fun get(factId: UUID): FactView {
        val fact = facts.findById(factId) ?: throw NotFoundException("No fact $factId")
        return FactView(
            factId = fact.factId,
            schemaRef = fact.schemaRef,
            subject = mapOf("sha256" to fact.subjectSha256, "repository" to fact.subjectRepository),
            tool = objectMapper.readTree(fact.payloadJson),
            identity = fact.identity,
            ingestedAt = fact.ingestedAt
        )
    }

    private class Envelope(
        val factId: UUID,
        val sha256: Sha256Hash,
        val repository: String?,
        val schemaRef: SchemaUrn,
        val tool: JsonNode
    ) {
        companion object {
            private val TOP = setOf("fact_id", "subject", "governance", "tool")
            private val SUBJECT = setOf("sha256", "repository")
            private val GOVERNANCE = setOf("schema_ref")

            fun parse(body: JsonNode): Envelope {
                if (!body.isObject) {
                    throw FactRejectedException(
                        FactRejectedException.INVALID_ENVELOPE, null,
                        listOf(SchemaViolation("", "the body must be a JSON object"))
                    )
                }
                val violations = mutableListOf<SchemaViolation>()
                fun fail(path: String, message: String) {
                    violations += SchemaViolation(path, message)
                }

                unknownFields(body, TOP, "").forEach { fail(it, "is not a field of the fact envelope") }

                val factId = body.get("fact_id")?.takeIf { it.isTextual }?.asText()?.let(::parseUuid)
                if (factId == null) fail("/fact_id", "must be a UUID")

                var sha256: Sha256Hash? = null
                var repository: String? = null
                val subject = body.get("subject")
                if (subject == null || !subject.isObject) {
                    fail("/subject", "must be an object")
                } else {
                    unknownFields(subject, SUBJECT, "/subject").forEach { fail(it, "is not a field of subject") }
                    sha256 = subject.get("sha256")?.takeIf { it.isTextual }?.asText()
                        ?.let { runCatching { Sha256Hash(it) }.getOrNull() }
                    if (sha256 == null) fail("/subject/sha256", "must be 64 hexadecimal characters")
                    val repo = subject.get("repository")
                    if (repo != null && !repo.isNull && !repo.isTextual) fail("/subject/repository", "must be a string")
                    repository = repo?.takeIf { it.isTextual }?.asText()
                }

                var schemaRef: SchemaUrn? = null
                val governance = body.get("governance")
                if (governance == null || !governance.isObject) {
                    fail("/governance", "must be an object")
                } else {
                    unknownFields(governance, GOVERNANCE, "/governance").forEach { fail(it, "is not a field of governance") }
                    schemaRef = governance.get("schema_ref")?.takeIf { it.isTextual }?.asText()
                        ?.let { runCatching { SchemaUrn(it) }.getOrNull() }
                    if (schemaRef == null) fail("/governance/schema_ref", "must look like urn:schema:<name>:v<version>")
                }

                val tool = body.get("tool")
                if (tool == null || !tool.isObject) fail("/tool", "must be an object")

                if (violations.isNotEmpty()) {
                    throw FactRejectedException(FactRejectedException.INVALID_ENVELOPE, schemaRef?.value, violations)
                }
                return Envelope(factId!!, sha256!!, repository, schemaRef!!, tool!!)
            }

            /** Only the canonical 8-4-4-4-12 form: `UUID.fromString` alone accepts shorter groups. */
            private fun parseUuid(text: String): UUID? =
                runCatching { UUID.fromString(text) }.getOrNull()?.takeIf { it.toString() == text.lowercase() }

            private fun unknownFields(node: JsonNode, allowed: Set<String>, at: String): List<String> =
                node.fieldNames().asSequence().filterNot { it in allowed }.map { "$at/$it" }.toList()
        }
    }
}
