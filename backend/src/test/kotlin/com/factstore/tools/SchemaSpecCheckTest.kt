package com.factstore.tools

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * #197 — fact schemas are specified in this repository (`schemas/<name>/v<n>.json`) and CI refuses
 * a change that breaks compatibility, using the same rules as the registry's 409 (#195).
 */
class SchemaSpecCheckTest {

    @TempDir lateinit var head: Path
    @TempDir lateinit var base: Path

    private val v1 = """{"type":"object","required":["tool"],"properties":{"tool":{"type":"string"},"passed":{"type":"integer"}}}"""

    private fun write(root: Path, file: String, json: String) =
        File(root.toFile(), file).apply { parentFile.mkdirs(); writeText(json) }

    private fun check(withBase: Boolean = true) = SchemaSpecCheck.check(head.toFile(), if (withBase) base.toFile() else null)

    @Test
    fun `#197 a compatible new version passes`() {
        write(head, "junit/v1.json", v1)
        write(head, "junit/v2.json", v1.replace("\"passed\":{\"type\":\"integer\"}", "\"passed\":{\"type\":\"integer\"},\"failed\":{\"type\":\"integer\"}"))
        assertThat(check(withBase = false)).isEmpty()
    }

    @Test
    fun `#197 a new version that removes a property is refused with the reason`() {
        write(head, "junit/v1.json", v1)
        write(head, "junit/v2.json", """{"type":"object","required":["tool"],"properties":{"tool":{"type":"string"}}}""")
        assertThat(check(withBase = false)).singleElement().asString()
            .contains("junit/v2.json").contains("not backwards compatible").contains("passed")
    }

    @Test
    fun `#197 a new version that adds a required property is refused`() {
        write(head, "junit/v1.json", v1)
        write(head, "junit/v2.json", v1.replace("\"required\":[\"tool\"]", "\"required\":[\"tool\",\"passed\"]"))
        assertThat(check(withBase = false)).singleElement().asString().contains("junit/v2.json")
    }

    @Test
    fun `#197 versions start at 1 and have no gaps`() {
        write(head, "junit/v1.json", v1)
        write(head, "junit/v3.json", v1)
        assertThat(check(withBase = false)).singleElement().asString().contains("junit/v3.json").contains("v2")
    }

    @Test
    fun `#197 a published version cannot be edited`() {
        write(base, "junit/v1.json", v1)
        write(head, "junit/v1.json", v1.replace("integer", "number"))
        assertThat(check()).singleElement().asString().contains("junit/v1.json").contains("immutable")
    }

    @Test
    fun `#197 a published version cannot be deleted`() {
        write(base, "junit/v1.json", v1)
        write(base, "junit/v2.json", v1)
        write(head, "junit/v1.json", v1)
        assertThat(check()).singleElement().asString().contains("junit/v2.json").contains("deleted")
    }

    @Test
    fun `#197 reformatting a published version is not a change`() {
        write(base, "junit/v1.json", v1)
        write(head, "junit/v1.json", "{\n  " + v1.removePrefix("{").replace(",", ",\n  "))
        assertThat(check()).isEmpty()
    }

    @Test
    fun `#197 a file that is not a valid JSON Schema is refused`() {
        write(head, "junit/v1.json", """{"type":"objekt"}""")
        assertThat(check(withBase = false)).singleElement().asString().contains("junit/v1.json").contains("JSON Schema")
    }

    @Test
    fun `#197 files must be named schemas, name, v-number dot json`() {
        write(head, "Upper/v1.json", v1)
        write(head, "junit/latest.json", v1)
        assertThat(check(withBase = false)).hasSize(2)
    }

    @Test
    fun `#197 the schemas in this repository pass`() {
        val repoSchemas = listOf(File("../schemas"), File("schemas")).first { it.isDirectory }
        assertThat(SchemaSpecCheck.check(repoSchemas, null)).isEmpty()
    }
}
