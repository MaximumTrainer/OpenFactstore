package com.factstore.application.schema

import com.factstore.application.ActorResolver
import com.factstore.core.domain.AuditEventType
import com.factstore.core.domain.FactSchema
import com.factstore.core.domain.SchemaUrn
import com.factstore.core.port.inbound.IAuditService
import com.factstore.core.port.outbound.IFactSchemaRepository
import com.factstore.exception.BadRequestException
import com.factstore.exception.ConflictException
import com.factstore.exception.NotFoundException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class FactSchemaResponse(
    val ref: String,
    val name: String,
    val version: Int,
    val schema: JsonNode,
    val createdBy: String,
    val createdAt: Instant
)

data class PublishResult(val schema: FactSchemaResponse, val created: Boolean)

/**
 * The central schema registry for fact payloads (#195).
 *
 * Publishing rules:
 * - versions start at 1 and are published in order;
 * - a published version is immutable: the same content again is an idempotent no-op, and
 *   different content is a conflict;
 * - a new version must be backwards compatible with the previous one ([SchemaCompatibility]),
 *   or it is refused as a conflict that names each breaking change.
 *
 * Schemas are JSON Schema draft 2020-12.
 */
@Service
@Transactional
class SchemaRegistryService(
    private val repository: IFactSchemaRepository,
    private val objectMapper: ObjectMapper,
    private val auditService: IAuditService,
    private val actorResolver: ActorResolver
) {
    private val log = LoggerFactory.getLogger(SchemaRegistryService::class.java)
    private val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

    fun publish(name: String, version: Int, schema: JsonNode): PublishResult {
        if (!SchemaUrn.NAME.matches(name)) {
            throw BadRequestException("Schema name must be lower-case letters, digits, '.', '_' or '-' (got '$name')")
        }
        if (version < 1) throw BadRequestException("Schema versions start at 1")
        requireValidJsonSchema(schema)

        repository.find(name, version)?.let { existing ->
            if (objectMapper.readTree(existing.schemaJson) == schema) return PublishResult(existing.toResponse(), created = false)
            throw ConflictException("${existing.ref} is already published and immutable; publish v${version + 1} instead")
        }

        if (version > 1) {
            val previous = repository.find(name, version - 1)
                ?: throw BadRequestException("Publish ${SchemaUrn.of(name, version - 1).value} before v$version")
            val breaking = SchemaCompatibility.breakingChanges(objectMapper.readTree(previous.schemaJson), schema)
            if (breaking.isNotEmpty()) {
                throw ConflictException(
                    "v$version is not backwards compatible with ${previous.ref}: ${breaking.joinToString("; ")}"
                )
            }
        }

        val actor = actorResolver.current()
        val saved = repository.save(
            FactSchema(name = name, version = version, schemaJson = objectMapper.writeValueAsString(schema), createdBy = actor)
        )
        auditService.record(
            eventType = AuditEventType.SCHEMA_PUBLISHED,
            actor = actor,
            payload = mapOf("ref" to saved.ref)
        )
        log.info("Published fact schema {}", saved.ref)
        return PublishResult(saved.toResponse(), created = true)
    }

    @Transactional(readOnly = true)
    fun get(name: String, version: Int): FactSchemaResponse =
        (repository.find(name, version) ?: throw NotFoundException("No schema ${SchemaUrn.of(name, version).value}")).toResponse()

    @Transactional(readOnly = true)
    fun list(name: String): List<FactSchemaResponse> = repository.findAllByName(name).map { it.toResponse() }

    private fun requireValidJsonSchema(schema: JsonNode) {
        if (!schema.isObject) throw BadRequestException("A schema must be a JSON object")
        val metaSchema = factory.getSchema(java.net.URI.create("https://json-schema.org/draft/2020-12/schema"))
        val problems = metaSchema.validate(schema)
        if (problems.isNotEmpty()) {
            throw BadRequestException("Not a valid JSON Schema (draft 2020-12): ${problems.joinToString("; ") { it.message }}")
        }
    }

    private fun FactSchema.toResponse() = FactSchemaResponse(
        ref = ref,
        name = name,
        version = version,
        schema = objectMapper.readTree(schemaJson),
        createdBy = createdBy,
        createdAt = createdAt
    )
}
