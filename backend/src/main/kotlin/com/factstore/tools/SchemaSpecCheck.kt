package com.factstore.tools

import com.factstore.application.schema.SchemaCompatibility
import com.factstore.core.domain.SchemaUrn
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import java.io.File
import java.net.URI
import kotlin.system.exitProcess

/**
 * Spec-driven fact schemas (#197). The schemas in `schemas/<name>/v<n>.json` are the source of
 * truth, and CI runs this check on every pull request (`./gradlew schemaSpecCheck`):
 *
 * - files are named `<name>/v<n>.json`, with names as the registry accepts them;
 * - each file is a valid JSON Schema (draft 2020-12);
 * - versions start at 1 and have no gaps;
 * - each version is backwards compatible with the one before it, by the same rules the registry
 *   uses to answer `409` on publish ([SchemaCompatibility]);
 * - against the base branch, a published version is never edited (beyond formatting) or deleted.
 *
 * Returns one line per problem; an empty list means the change is acceptable.
 */
object SchemaSpecCheck {
    private val mapper = ObjectMapper()
    private val metaSchema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(URI.create("https://json-schema.org/draft/2020-12/schema"))
    private val VERSION_FILE = Regex("^v([1-9][0-9]{0,5})[.]json$")

    fun check(head: File, base: File?): List<String> {
        val problems = mutableListOf<String>()
        val headSchemas = read(head, problems)
        headSchemas.forEach { (name, versions) ->
            val numbers = versions.keys.sorted()
            numbers.forEachIndexed { i, n ->
                if (n != i + 1) problems += "$name/v$n.json: versions must start at v1 with no gaps; v${i + 1} is missing"
            }
            versions.forEach { (n, schema) ->
                val invalid = metaSchema.validate(schema)
                if (invalid.isNotEmpty()) {
                    problems += "$name/v$n.json: not a valid JSON Schema (draft 2020-12): ${invalid.joinToString("; ") { it.message }}"
                }
            }
            numbers.zipWithNext().forEach { (previous, next) ->
                val breaking = SchemaCompatibility.breakingChanges(versions.getValue(previous), versions.getValue(next))
                if (breaking.isNotEmpty()) {
                    problems += "$name/v$next.json: not backwards compatible with v$previous: ${breaking.joinToString("; ")}"
                }
            }
        }
        if (base != null && base.isDirectory) {
            read(base, mutableListOf()).forEach { (name, versions) ->
                versions.forEach { (n, published) ->
                    val current = headSchemas[name]?.get(n)
                    when {
                        current == null -> problems += "$name/v$n.json: a published version was deleted; versions are immutable"
                        current != published -> problems += "$name/v$n.json: a published version was changed; versions are immutable, publish v${(headSchemas[name]?.keys?.maxOrNull() ?: n) + 1} instead"
                    }
                }
            }
        }
        return problems
    }

    /** name -> version -> schema, reporting misnamed files and unparseable JSON. */
    private fun read(root: File, problems: MutableList<String>): Map<String, Map<Int, JsonNode>> {
        val result = sortedMapOf<String, MutableMap<Int, JsonNode>>()
        root.listFiles()?.sortedBy { it.name }?.forEach { dir ->
            if (!dir.isDirectory) {
                problems += "${dir.name}: expected a directory named after the schema"
                return@forEach
            }
            if (!SchemaUrn.NAME.matches(dir.name)) {
                problems += "${dir.name}/: schema names are lower-case letters, digits, '.', '_' or '-'"
                return@forEach
            }
            dir.listFiles()?.sortedBy { it.name }?.forEach { file ->
                val version = VERSION_FILE.find(file.name)?.groupValues?.get(1)?.toInt()
                if (version == null) {
                    problems += "${dir.name}/${file.name}: files are named v<version>.json"
                    return@forEach
                }
                val json = runCatching { mapper.readTree(file) }.getOrNull()
                if (json == null) problems += "${dir.name}/${file.name}: not valid JSON"
                else result.getOrPut(dir.name) { sortedMapOf() }[version] = json
            }
        }
        return result
    }

    /** `schemaSpecCheck <schemas-dir> [base-schemas-dir]`; exits 1 when there are problems. */
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isNotEmpty()) { "usage: SchemaSpecCheck <schemas-dir> [base-schemas-dir]" }
        val problems = check(File(args[0]), args.getOrNull(1)?.let(::File))
        if (problems.isEmpty()) {
            println("schemas: OK")
            return
        }
        problems.forEach { System.err.println("schemas: $it") }
        exitProcess(1)
    }
}
