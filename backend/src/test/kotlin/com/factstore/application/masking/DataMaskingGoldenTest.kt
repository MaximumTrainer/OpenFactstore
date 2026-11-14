package com.factstore.application.masking

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * #198 / #238 — the server and the CLI mask identically. Both run the shared fixtures in
 * `cli/pkg/masking/testdata/golden.json`; a runner that masks before signing therefore produces
 * exactly what the server's reject mode accepts.
 */
class DataMaskingGoldenTest {

    private val mapper = jacksonObjectMapper()
    private val masking = DataMasking(MaskingRuleset.builtIn())

    @Test
    fun `#238 the server masks every shared fixture exactly as recorded`() {
        val file = listOf("../cli/pkg/masking/testdata/golden.json", "cli/pkg/masking/testdata/golden.json")
            .map(::File).first { it.exists() }
        val golden = mapper.readTree(file)
        assertThat(golden["ruleset"].asText()).isEqualTo(MaskingRuleset.builtIn().version)

        for (case in golden["cases"]) {
            val name = case["name"].asText()
            val sensitive = case["sensitive"]?.map { it.asText() }?.toSet() ?: emptySet()
            val result = masking.scan(case["input"], sensitive)
            assertThat(result.masked).describedAs("$name: output").isEqualTo(case["output"])
            assertThat(result.findings.map { mapOf("path" to it.path, "rule" to it.rule) })
                .describedAs("$name: findings")
                .isEqualTo(case["findings"].map { mapOf("path" to it["path"].asText(), "rule" to it["rule"].asText()) })
        }
    }
}
