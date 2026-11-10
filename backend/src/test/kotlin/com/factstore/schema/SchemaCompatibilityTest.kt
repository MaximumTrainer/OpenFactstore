package com.factstore.schema

import com.factstore.application.schema.SchemaCompatibility
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * #195 / #197 — what counts as a breaking change between two versions of a fact schema.
 *
 * Breaking: a property removed, a property newly required, a type narrowed or changed, an enum
 * value removed, a pattern or format added or changed, or `additionalProperties` closed.
 * Compatible: an optional property added, a requirement dropped, an enum value added.
 */
class SchemaCompatibilityTest {

    private val mapper = jacksonObjectMapper()

    private fun schema(props: String, required: String = "[]", extra: String = "") =
        mapper.readTree("""{"type":"object","required":$required,"properties":{$props}$extra}""")

    companion object {
        @JvmStatic
        fun cases(): List<Arguments> = listOf(
            Arguments.of("optional property added", """"a":{"type":"string"}""", "[]",
                """"a":{"type":"string"},"b":{"type":"integer"}""", "[]", "", null),
            Arguments.of("requirement dropped", """"a":{"type":"string"}""", """["a"]""",
                """"a":{"type":"string"}""", "[]", "", null),
            Arguments.of("enum value added", """"s":{"enum":["A","B"]}""", "[]",
                """"s":{"enum":["A","B","C"]}""", "[]", "", null),
            Arguments.of("property removed", """"a":{"type":"string"},"b":{"type":"string"}""", "[]",
                """"a":{"type":"string"}""", "[]", "", "b"),
            Arguments.of("property newly required", """"a":{"type":"string"}""", "[]",
                """"a":{"type":"string"}""", """["a"]""", "", "a"),
            Arguments.of("type changed", """"a":{"type":"string"}""", "[]",
                """"a":{"type":"integer"}""", "[]", "", "a"),
            Arguments.of("enum value removed", """"s":{"enum":["A","B"]}""", "[]",
                """"s":{"enum":["A"]}""", "[]", "", "s"),
            Arguments.of("pattern added", """"a":{"type":"string"}""", "[]",
                """"a":{"type":"string","pattern":"^x"}""", "[]", "", "a"),
            Arguments.of("nested property removed", """"o":{"type":"object","properties":{"x":{"type":"string"}}}""", "[]",
                """"o":{"type":"object","properties":{}}""", "[]", "", "o.x"),
            Arguments.of("additional properties closed", """"a":{"type":"string"}""", "[]",
                """"a":{"type":"string"}""", "[]", ""","additionalProperties":false""", "additionalProperties"),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `breaking changes are reported and compatible ones are not`(
        @Suppress("UNUSED_PARAMETER") case: String,
        oldProps: String, oldRequired: String,
        newProps: String, newRequired: String, newExtra: String,
        expectedPath: String?
    ) {
        val problems = SchemaCompatibility.breakingChanges(schema(oldProps, oldRequired), schema(newProps, newRequired, newExtra))

        if (expectedPath == null) assertThat(problems).isEmpty()
        else assertThat(problems).anyMatch { it.contains(expectedPath) }
    }
}
