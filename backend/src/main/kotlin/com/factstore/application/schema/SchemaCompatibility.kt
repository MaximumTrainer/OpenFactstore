package com.factstore.application.schema

import com.fasterxml.jackson.databind.JsonNode

/**
 * Decides whether a new version of a fact schema stays backwards compatible with the previous one
 * (#195, and the rule #197's CI check is to share).
 *
 * "Compatible" means every payload valid under the old version is still valid under the new one,
 * so a CI runner that has not upgraded keeps being accepted.
 *
 * Breaking:
 * - a property removed;
 * - a property newly required;
 * - a type changed or narrowed;
 * - an enum value removed;
 * - a `pattern` or `format` added or changed;
 * - `additionalProperties` closed.
 *
 * Compatible: adding an optional property, dropping a requirement, adding an enum value or widening
 * a type.
 */
object SchemaCompatibility {

    /** Human-readable descriptions of each breaking change, empty when compatible. */
    fun breakingChanges(old: JsonNode, new: JsonNode): List<String> = mutableListOf<String>().also { compare(old, new, "", it) }

    private fun label(path: String) = path.ifEmpty { "the root" }

    private fun compare(old: JsonNode, new: JsonNode, path: String, out: MutableList<String>) {
        val oldTypes = types(old)
        val newTypes = types(new)
        // Every old type must still be accepted. An integer is also a number, so widening
        // integer to number is compatible.
        val narrowed = oldTypes.filterNot { it in newTypes || (it == "integer" && "number" in newTypes) }
        if (oldTypes.isNotEmpty() && newTypes.isNotEmpty() && narrowed.isNotEmpty()) {
            out += "type of ${label(path)} changed from ${oldTypes.sorted()} to ${newTypes.sorted()}"
        }

        val oldEnum = old.get("enum")?.map { it.toString() }?.toSet()
        val newEnum = new.get("enum")?.map { it.toString() }?.toSet()
        if (newEnum != null && (oldEnum == null || !newEnum.containsAll(oldEnum))) {
            val removed = (oldEnum ?: emptySet()) - newEnum
            out += if (oldEnum == null) "${label(path)} is newly restricted to an enum"
            else "enum value(s) $removed removed from ${label(path)}"
        }

        for (keyword in listOf("pattern", "format")) {
            val before = old.get(keyword)?.asText()
            val after = new.get(keyword)?.asText()
            if (after != null && after != before) out += "$keyword ${if (before == null) "added to" else "changed on"} ${label(path)}"
        }

        val oldAdditional = old.get("additionalProperties")
        val newAdditional = new.get("additionalProperties")
        if (newAdditional != null && newAdditional.isBoolean && !newAdditional.asBoolean() &&
            (oldAdditional == null || (oldAdditional.isBoolean && oldAdditional.asBoolean()))
        ) {
            out += "additionalProperties closed at ${label(path)}"
        }

        val oldRequired = old.get("required")?.map { it.asText() }?.toSet() ?: emptySet()
        val newRequired = new.get("required")?.map { it.asText() }?.toSet() ?: emptySet()
        (newRequired - oldRequired).forEach { out += "property ${child(path, it)} newly required" }

        val oldProps = old.get("properties")
        val newProps = new.get("properties")
        oldProps?.fieldNames()?.forEach { name ->
            val after = newProps?.get(name)
            if (after == null) out += "property ${child(path, name)} removed"
            else compare(oldProps.get(name), after, child(path, name), out)
        }

        val oldItems = old.get("items")
        val newItems = new.get("items")
        if (oldItems != null && newItems != null && oldItems.isObject && newItems.isObject) {
            compare(oldItems, newItems, "$path[]", out)
        }
    }

    private fun child(path: String, name: String) = if (path.isEmpty()) name else "$path.$name"

    private fun types(node: JsonNode): Set<String> {
        val type = node.get("type") ?: return emptySet()
        return if (type.isArray) type.map { it.asText() }.toSet() else setOf(type.asText())
    }
}
