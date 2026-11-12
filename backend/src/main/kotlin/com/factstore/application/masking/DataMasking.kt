package com.factstore.application.masking

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import org.yaml.snakeyaml.Yaml
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/** Where a secret or PII was found, and by which rule. Never the value itself. */
data class MaskingFinding(val path: String, val rule: String)

data class MaskingResult(val masked: JsonNode, val findings: List<MaskingFinding>)

data class MaskingRule(val id: String, val category: String, val pattern: Regex)

/** A versioned set of masking rules (#198), from `masking/rules-v1.yaml` or an operator's file. */
class MaskingRuleset(val version: String, val rules: List<MaskingRule>) {
    companion object {
        fun builtIn(): MaskingRuleset =
            MaskingRuleset::class.java.getResourceAsStream("/masking/rules-v1.yaml").use { load(it!!) }

        fun fromFile(path: String): MaskingRuleset = Files.newInputStream(Path.of(path)).use { load(it) }

        @Suppress("UNCHECKED_CAST")
        private fun load(input: InputStream): MaskingRuleset {
            val doc = Yaml().load<Map<String, Any?>>(input)
            val version = doc["version"] as? String ?: error("A masking ruleset needs a version")
            val rules = (doc["rules"] as List<Map<String, Any?>>).map {
                MaskingRule(it["id"] as String, it["category"] as? String ?: "secret", Regex(it["pattern"] as String))
            }
            require(rules.map { it.id }.toSet().size == rules.size) { "Masking rule ids must be unique" }
            return MaskingRuleset(version, rules)
        }
    }
}

/**
 * Finds and masks secrets and PII in a fact's `tool` payload (#198).
 *
 * - Every string value is scanned with each rule, and a match becomes `[MASKED:<rule>]`. A
 *   pattern with a group named `value` masks only that group, so `password=…` keeps its
 *   `password=`.
 * - Fields the fact's schema marks `"x-sensitive": true` are masked whole, as
 *   `[MASKED:sensitive-field]`. Pointers use `*` for any array index or key.
 * - A value that is already masked is not a finding, so a runner that masked before signing
 *   passes cleanly. Object keys are never rewritten.
 *
 * The result is stable: the same input always gives the same output.
 */
class DataMasking(private val ruleset: MaskingRuleset) {

    val version: String get() = ruleset.version

    fun scan(payload: JsonNode, sensitivePointers: Set<String>): MaskingResult {
        val findings = mutableListOf<MaskingFinding>()
        val masked = walk(payload, "", sensitivePointers.map { it.split("/").drop(1) }, findings)
        return MaskingResult(masked, findings)
    }

    private fun walk(node: JsonNode, path: String, sensitive: List<List<String>>, findings: MutableList<MaskingFinding>): JsonNode =
        when {
            node is ObjectNode -> JsonNodeFactory.instance.objectNode().also { out ->
                node.fields().forEach { (key, child) -> out.set<JsonNode>(key, walk(child, "$path/${escape(key)}", sensitive, findings)) }
            }
            node is ArrayNode -> JsonNodeFactory.instance.arrayNode().also { out ->
                node.forEachIndexed { i, child -> out.add(walk(child, "$path/$i", sensitive, findings)) }
            }
            isSensitive(path, sensitive) -> {
                if (node.isNull || (node.isTextual && node.asText() == MASKED_FIELD)) {
                    node
                } else {
                    findings += MaskingFinding(path, SENSITIVE_FIELD)
                    TextNode(MASKED_FIELD)
                }
            }
            node.isTextual -> maskText(node.asText(), path, findings)
            else -> node
        }

    private fun maskText(text: String, path: String, findings: MutableList<MaskingFinding>): JsonNode {
        var result = text
        for (rule in ruleset.rules) {
            var hit = false
            result = rule.pattern.replace(result) { match ->
                hit = true
                val group = if (rule.pattern.pattern.contains("(?<value>")) match.groups["value"] else null
                if (group == null) {
                    "[MASKED:${rule.id}]"
                } else {
                    val start = group.range.first - match.range.first
                    val end = group.range.last + 1 - match.range.first
                    match.value.substring(0, start) + "[MASKED:${rule.id}]" + match.value.substring(end)
                }
            }
            if (hit) findings += MaskingFinding(path, rule.id)
        }
        return TextNode(result)
    }

    private fun isSensitive(path: String, sensitive: List<List<String>>): Boolean {
        if (sensitive.isEmpty()) return false
        val segments = path.split("/").drop(1)
        return sensitive.any { pattern ->
            pattern.size == segments.size && pattern.zip(segments).all { (p, s) -> p == "*" || p == s }
        }
    }

    private fun escape(key: String) = key.replace("~", "~0").replace("/", "~1")

    companion object {
        const val SENSITIVE_FIELD = "sensitive-field"
        const val MASKED_FIELD = "[MASKED:sensitive-field]"
    }
}
